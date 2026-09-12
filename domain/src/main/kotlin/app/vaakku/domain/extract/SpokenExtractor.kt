package app.vaakku.domain.extract

import app.vaakku.domain.lexicon.FuzzyMatcher
import app.vaakku.domain.lexicon.Lexicon
import app.vaakku.domain.model.AsrSegment
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.RateQualifier
import app.vaakku.domain.model.ReasonCode
import app.vaakku.domain.model.Source
import app.vaakku.domain.normalize.DurationClaimKind
import app.vaakku.domain.normalize.Normalizer
import app.vaakku.domain.normalize.Tokenizer
import java.math.BigDecimal
import java.util.UUID

/**
 * Turns one ASR segment into zero or more [Observation]s — build plan §5.5.
 *
 * Shared shape across all six claim types: find anchor token(s) for that
 * type, take a local window around the chosen anchor, detect hedge /
 * negation / conditional in that window, build the typed value via
 * [Normalizer], and set `confidence = matchQuality * seg.segmentQuality`.
 *
 * **Within-segment "latest wins"**: when a claim type's anchor word appears
 * more than once in one segment (a self-correction, e.g. build plan A2/T09),
 * the LAST occurrence is used and its LOCAL window is what hedge/negation/
 * conditional detection reads — never the whole segment — so an earlier,
 * unrelated conditional/negation does not leak onto the corrected value.
 */
class SpokenExtractor(private val lexicon: Lexicon, private val normalizer: Normalizer) {

    private companion object {
        /** Radius (tokens) for hedge/conditional/percent-value windows around an anchor — §5.5 step 2. */
        const val ANCHOR_RADIUS = 6

        /** Radius (tokens) for negation detection around an anchor — §5.5 step 3. */
        const val NEGATION_RADIUS = 3

        /** Radius (tokens) for classifying a duration mention (lock-in vs liquidity vs term). Deliberately
         *  smaller than [ANCHOR_RADIUS] so two different duration mentions in one segment (build plan T02)
         *  do not bleed into each other's classification. */
        const val DISAMBIGUATION_RADIUS = 4

        /** How far back to look for a self-correction cue ("இல்ல இல்ல") before an ambiguous duration mention. */
        const val SELF_CORRECTION_LOOKBACK = 5
    }

    fun extract(seg: AsrSegment): List<Observation> {
        val tokens = Tokenizer.tokenize(seg.text)
        if (tokens.isEmpty()) return emptyList()
        return buildList {
            extractRate(tokens, seg)?.let { add(it) }
            extractGuarantee(tokens, seg)?.let { add(it) }
            addAll(extractDurationClaims(tokens, seg))
            extractBundling(tokens, seg)?.let { add(it) }
            extractCharges(tokens, seg)?.let { add(it) }
        }
    }

    // ------------------------------------------------------------------
    // RETURN_RATE
    // ------------------------------------------------------------------

    private fun extractRate(tokens: List<String>, seg: AsrSegment): Observation? {
        val anchors = tokens.indices.filter { i ->
            tokens[i].length > 1 && tokens[i].endsWith("%") ||
                lexicon.bestTokenMatch(tokens[i], lexicon.data.percentMarkers) != null
        }
        if (anchors.isEmpty()) return null

        // Each anchor is resolved at its OWN global position via numberNear,
        // not by re-slicing a window and calling parsePercent on it — a
        // window built around anchor B can still contain anchor A, and
        // parsePercent always finds the FIRST marker in whatever list it is
        // given, which would silently re-resolve to A's number again (build
        // plan T05's second "percent" would otherwise collapse onto the
        // first one instead of finding its own).
        val percents = mutableSetOf<BigDecimal>()
        var minQuality = 1.0
        for (idx in anchors) {
            val value = if (tokens[idx].endsWith("%")) {
                normalizer.parseNumber(listOf(tokens[idx]))
            } else {
                normalizer.numberNear(tokens, idx, maxDistance = 2)
            } ?: continue
            percents += value
            val quality = if (tokens[idx].endsWith("%")) {
                FuzzyMatcher.EXACT_QUALITY
            } else {
                lexicon.bestTokenMatch(tokens[idx], lexicon.data.percentMarkers) ?: FuzzyMatcher.EXACT_QUALITY
            }
            minQuality = minOf(minQuality, quality)
        }
        if (percents.isEmpty()) return null

        val fullText = tokens.joinToString(" ")
        val qualifier = when {
            lexicon.textContainsAny(fullText, lexicon.data.hedgeUpTo) -> RateQualifier.UP_TO
            lexicon.textContainsAny(fullText, lexicon.data.hedgeIllustrative) -> RateQualifier.ILLUSTRATIVE
            lexicon.textContainsAny(fullText, lexicon.data.hedgeGeneric) -> RateQualifier.EXPECTED
            else -> RateQualifier.ASSERTED
        }
        // "hedged" tracks the build plan §5.7 compare-rule parenthetical exactly
        // ("hedged (UP_TO/EXPECTED)") — ILLUSTRATIVE is a forthright quote of the
        // document's own scenarios, not an evasive hedge.
        val hedged = qualifier == RateQualifier.UP_TO || qualifier == RateQualifier.EXPECTED
        val conditional = lexicon.textContainsAny(fullText, lexicon.data.conditional)

        return Observation(
            id = newId(),
            source = Source.SPOKEN,
            type = ClaimType.RETURN_RATE,
            value = ClaimValue.Rate(percents, qualifier),
            hedged = hedged,
            negated = false,
            conditional = conditional,
            confidence = minQuality * seg.segmentQuality,
            provenance = spokenProvenance(seg),
            tMs = seg.startMs,
        )
    }

    // ------------------------------------------------------------------
    // GUARANTEE — the special rule, §5.5 steps 6-8
    // ------------------------------------------------------------------

    private fun extractGuarantee(tokens: List<String>, seg: AsrSegment): Observation? {
        val anchors = phraseAnchors(tokens, lexicon.data.guaranteeWords)
        // §5.5.7: "FD மாதிரி" alone has no guarantee word, so this is empty and
        // extraction produces nothing for this type — no special-case needed.
        if (anchors.isEmpty()) return null

        val anchorIdx = anchors.last()
        val anchorQuality = qualityAt(tokens, anchorIdx, lexicon.data.guaranteeWords)
        val negQuality = negationQuality(tokens, anchorIdx, NEGATION_RADIUS)
        val negExact = negQuality == FuzzyMatcher.EXACT_QUALITY
        val negFuzzyOnly = negQuality != null && !negExact
        // Local, not the +-6 anchor radius: a conditional marker attached to
        // an EARLIER mention of the same word (build plan T09's self-correction)
        // must not bleed onto the LATEST mention's own reading. Same radius as
        // negation — both are close-range grammatical modifiers of one word.
        val conditional = hasConditional(tokens, anchorIdx, NEGATION_RADIUS)

        // §5.5.6: a clear (exact) negation within +-3 -> Guarantee(false). A
        // fuzzy-only negation match neither confirms nor denies — it is flagged
        // NEGATION_AMBIGUOUS for the reconciler to turn into UNCERTAIN, rather
        // than guessing which way the dropped/garbled word actually went.
        val guaranteed = !negExact
        val ambiguous = if (negFuzzyOnly) ReasonCode.NEGATION_AMBIGUOUS else null

        return Observation(
            id = newId(),
            source = Source.SPOKEN,
            type = ClaimType.GUARANTEE,
            value = ClaimValue.Guarantee(guaranteed),
            hedged = false,
            negated = negExact,
            conditional = conditional,
            confidence = anchorQuality * seg.segmentQuality,
            provenance = spokenProvenance(seg),
            tMs = seg.startMs,
            ambiguous = ambiguous,
        )
    }

    // ------------------------------------------------------------------
    // LOCK_IN + LIQUIDITY — both come from the same duration-mention scan
    // ------------------------------------------------------------------

    private data class ResolvedDuration(val type: ClaimType, val months: Int, val anchorIdx: Int)

    private fun extractDurationClaims(tokens: List<String>, seg: AsrSegment): List<Observation> {
        val unitAnchors = tokens.indices.filter { i ->
            lexicon.bestTokenMatch(tokens[i], lexicon.data.yearUnits) != null ||
                lexicon.bestTokenMatch(tokens[i], lexicon.data.monthUnits) != null
        }

        val resolved = mutableListOf<ResolvedDuration>()
        var currentType: ClaimType? = null

        for (idx in unitAnchors) {
            // Resolved at THIS anchor's own global position (see extractRate's
            // comment above) — not by re-slicing a window and calling
            // parseDurationMonths on it, which would re-find the FIRST
            // year/month marker in that slice rather than this one (build plan
            // A2's second "வருஷம்" would otherwise be skipped entirely whenever
            // it shares a window with the first).
            val isYear = lexicon.bestTokenMatch(tokens[idx], lexicon.data.yearUnits) != null
            val n = normalizer.numberNear(tokens, idx, maxDistance = 2) ?: continue
            val months = normalizer.monthsFrom(n, isYear)
            // Classification (lock-in / withdraw-after / term-so-no-claim / ambiguous)
            // is read from the lexical signal ALONE, deliberately decoupled from
            // number-parsing (see Normalizer.classifyDurationKind's doc) — the number
            // itself was already resolved at THIS anchor's own position above.
            val kind = normalizer.classifyDurationKind(windowAround(tokens, idx, DISAMBIGUATION_RADIUS))
            val type = when (kind) {
                DurationClaimKind.LOCK_IN -> ClaimType.LOCK_IN
                DurationClaimKind.WITHDRAWABLE_AFTER -> ClaimType.LIQUIDITY
                DurationClaimKind.NO_CLAIM -> null
                // Locally ambiguous (no lock-in/term/liquidity word nearby): only
                // carry it forward as a correction of the PREVIOUS type if a
                // self-correction cue sits right before it (build plan A2). A
                // bare unresolved duration otherwise stays unresolved — never guess.
                null -> if (currentType != null && hasSelfCorrectionBefore(tokens, idx)) currentType else null
            }
            if (type != null) {
                resolved += ResolvedDuration(type, months, idx)
                currentType = type
            }
        }

        val observations = mutableListOf<Observation>()

        val lockIn = resolved.filter { it.type == ClaimType.LOCK_IN }.maxByOrNull { it.anchorIdx }
        if (lockIn != null) {
            observations += Observation(
                id = newId(),
                source = Source.SPOKEN,
                type = ClaimType.LOCK_IN,
                value = ClaimValue.LockIn(lockIn.months),
                hedged = false,
                negated = false,
                conditional = hasConditional(tokens, lockIn.anchorIdx, NEGATION_RADIUS),
                confidence = qualityAt(tokens, lockIn.anchorIdx, lexicon.data.yearUnits + lexicon.data.monthUnits) * seg.segmentQuality,
                provenance = spokenProvenance(seg),
                tMs = seg.startMs,
            )
        }

        val withdrawAfter = resolved.filter { it.type == ClaimType.LIQUIDITY }.maxByOrNull { it.anchorIdx }

        // Same-segment anaphora: "Lock-in அஞ்சு வருஷம். அதுக்கு முன்னாடி surrender
        // value கிடையாது." (build plan T06) — the second sentence negates
        // "surrender" with no duration of its own; it refers back to the
        // lock-in just stated in the SAME segment. Scoped and documented, not a
        // general guess: it only fires when there is no explicit withdraw-after
        // duration and a LOCK_IN was resolved in this same segment.
        val surrenderNegIdx = tokens.indices.firstOrNull { i ->
            lexicon.bestTokenMatch(tokens[i], lexicon.data.liquidityWords) != null &&
                negationQuality(tokens, i, NEGATION_RADIUS) == FuzzyMatcher.EXACT_QUALITY
        }
        val surrenderNilBeforeMonths = if (withdrawAfter == null && surrenderNegIdx != null && lockIn != null) {
            lockIn.months
        } else {
            null
        }
        val liquidityAnchorIdx = withdrawAfter?.anchorIdx ?: surrenderNegIdx

        if (withdrawAfter != null || surrenderNilBeforeMonths != null) {
            val quality = liquidityAnchorIdx?.let {
                if (withdrawAfter != null) {
                    qualityAt(tokens, it, lexicon.data.yearUnits + lexicon.data.monthUnits)
                } else {
                    qualityAt(tokens, it, lexicon.data.liquidityWords)
                }
            } ?: FuzzyMatcher.EXACT_QUALITY
            observations += Observation(
                id = newId(),
                source = Source.SPOKEN,
                type = ClaimType.LIQUIDITY,
                value = ClaimValue.Liquidity(withdrawAfter?.months, surrenderNilBeforeMonths),
                hedged = false,
                negated = false,
                conditional = liquidityAnchorIdx?.let { hasConditional(tokens, it, NEGATION_RADIUS) } ?: false,
                confidence = quality * seg.segmentQuality,
                provenance = spokenProvenance(seg),
                tMs = seg.startMs,
            )
        }

        return observations
    }

    private fun hasSelfCorrectionBefore(tokens: List<String>, idx: Int): Boolean {
        val from = maxOf(0, idx - SELF_CORRECTION_LOOKBACK)
        if (from >= idx) return false
        val slice = tokens.subList(from, idx).joinToString(" ")
        return lexicon.textContainsAny(slice, lexicon.data.selfCorrectionMarkers)
    }

    // ------------------------------------------------------------------
    // BUNDLING
    // ------------------------------------------------------------------

    private fun extractBundling(tokens: List<String>, seg: AsrSegment): Observation? {
        val anchors = phraseAnchors(tokens, lexicon.data.bundlingWords)
        if (anchors.isEmpty()) return null
        val anchorIdx = anchors.last()

        val fullText = tokens.joinToString(" ")
        val required = lexicon.textContainsAny(fullText, lexicon.data.bundlingRequiredWords)
        val voluntary = lexicon.textContainsAny(fullText, lexicon.data.bundlingVoluntaryWords)
        // A bare "loan" mention with no requirement/voluntary signal is not a
        // claim — never guess which way an unqualified mention goes.
        if (!required && !voluntary) return null

        val conditional = hasConditional(tokens, anchorIdx, NEGATION_RADIUS)
        return Observation(
            id = newId(),
            source = Source.SPOKEN,
            type = ClaimType.BUNDLING,
            value = ClaimValue.Bundling(requiredForLoan = required && !voluntary),
            hedged = false,
            negated = voluntary,
            conditional = conditional,
            confidence = qualityAt(tokens, anchorIdx, lexicon.data.bundlingWords) * seg.segmentQuality,
            provenance = spokenProvenance(seg),
            tMs = seg.startMs,
        )
    }

    // ------------------------------------------------------------------
    // CHARGES
    // ------------------------------------------------------------------

    private fun extractCharges(tokens: List<String>, seg: AsrSegment): Observation? {
        val anchors = phraseAnchors(tokens, lexicon.data.chargesWords)
        if (anchors.isEmpty()) return null
        val anchorIdx = anchors.last()

        val negQuality = negationQuality(tokens, anchorIdx, NEGATION_RADIUS)
        val negExact = negQuality == FuzzyMatcher.EXACT_QUALITY
        val negFuzzyOnly = negQuality != null && !negExact
        val anyCharges = !negExact
        // A percent marker within the anchor radius, resolved at ITS OWN
        // position (same reasoning as extractRate) — a charge stated as
        // "5% charge" or "charge is 5%".
        val percent = if (anyCharges) {
            windowIndicesAround(tokens, anchorIdx, ANCHOR_RADIUS)
                .firstOrNull { i -> tokens[i].endsWith("%") || lexicon.bestTokenMatch(tokens[i], lexicon.data.percentMarkers) != null }
                ?.let { pIdx ->
                    if (tokens[pIdx].endsWith("%")) normalizer.parseNumber(listOf(tokens[pIdx])) else normalizer.numberNear(tokens, pIdx, 2)
                }
        } else {
            null
        }
        val conditional = hasConditional(tokens, anchorIdx, NEGATION_RADIUS)
        val ambiguous = if (negFuzzyOnly) ReasonCode.NEGATION_AMBIGUOUS else null

        return Observation(
            id = newId(),
            source = Source.SPOKEN,
            type = ClaimType.CHARGES,
            value = ClaimValue.Charges(anyCharges = anyCharges, percent = percent, label = null),
            hedged = false,
            negated = negExact,
            conditional = conditional,
            confidence = qualityAt(tokens, anchorIdx, lexicon.data.chargesWords) * seg.segmentQuality,
            provenance = spokenProvenance(seg),
            tMs = seg.startMs,
            ambiguous = ambiguous,
        )
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private fun windowAround(tokens: List<String>, idx: Int, radius: Int): List<String> {
        val from = maxOf(0, idx - radius)
        val to = minOf(tokens.size - 1, idx + radius)
        return tokens.subList(from, to + 1)
    }

    /** Same span as [windowAround] but returns global indices, ordered by distance from [idx] (closest first). */
    private fun windowIndicesAround(tokens: List<String>, idx: Int, radius: Int): List<Int> {
        val result = mutableListOf<Int>()
        for (d in 0..radius) {
            val left = idx - d
            val right = idx + d
            if (left in tokens.indices) result += left
            if (d != 0 && right in tokens.indices) result += right
        }
        return result
    }

    /** Best fuzzy-match quality at one specific token against [group], defaulting to exact (a phrase anchor). */
    private fun qualityAt(tokens: List<String>, idx: Int, group: Collection<String>): Double =
        lexicon.bestTokenMatch(tokens[idx], group) ?: FuzzyMatcher.EXACT_QUALITY

    /**
     * Anchor indices for [group]: a single token that fuzzy-matches, OR the
     * start of a 2-token run whose space-joined text matches a multi-word
     * lexicon phrase ("lock" + "in" -> "lock in", after the tokenizer has
     * already split the hyphen in "lock-in").
     */
    private fun phraseAnchors(tokens: List<String>, group: Collection<String>): List<Int> {
        val indices = mutableListOf<Int>()
        for (i in tokens.indices) {
            if (lexicon.bestTokenMatch(tokens[i], group) != null) {
                indices += i
                continue
            }
            if (i + 1 < tokens.size) {
                val twoToken = tokens[i] + " " + tokens[i + 1]
                if (lexicon.textContainsAny(twoToken, group)) {
                    indices += i
                }
            }
        }
        return indices
    }

    /** Best negation-word quality within +-[radius] tokens of [anchorIdx], or null if none found. */
    private fun negationQuality(tokens: List<String>, anchorIdx: Int, radius: Int): Double? {
        var best: Double? = null
        for (d in 1..radius) {
            // Both sides of the anchor, nearest first. The result is a max, so the
            // order does not matter; pairing them just avoids saying it twice.
            for (idx in intArrayOf(anchorIdx - d, anchorIdx + d)) {
                if (idx !in tokens.indices) continue
                val q = lexicon.bestTokenMatch(tokens[idx], lexicon.data.negation) ?: continue
                val current = best
                best = if (current == null) q else maxOf(current, q)
            }
        }
        return best
    }

    private fun hasConditional(tokens: List<String>, anchorIdx: Int, radius: Int): Boolean {
        val window = windowAround(tokens, anchorIdx, radius)
        return lexicon.textContainsAny(window.joinToString(" "), lexicon.data.conditional)
    }

    private fun spokenProvenance(seg: AsrSegment): Provenance.Spoken =
        Provenance.Spoken(span = seg.text, startMs = seg.startMs, endMs = seg.endMs, engine = seg.engine)

    private fun newId(): String = UUID.randomUUID().toString()
}
