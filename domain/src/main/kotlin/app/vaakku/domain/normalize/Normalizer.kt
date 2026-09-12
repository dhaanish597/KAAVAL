package app.vaakku.domain.normalize

import app.vaakku.domain.lexicon.Lexicon
import java.math.BigDecimal
import java.math.RoundingMode

/** What a duration phrase means, once disambiguated — build plan §5.4. */
sealed interface DurationDisambiguation {
    data class LockIn(val months: Int) : DurationDisambiguation
    data class WithdrawableAfter(val months: Int) : DurationDisambiguation
    /** "policy term பத்து வருஷம்" — a duration is present but it is not lock-in. */
    data object NoClaim : DurationDisambiguation
}

/**
 * Pure number/duration/percent parsing over Tamil–English tokens — build
 * plan §5.4. Every function here is a pure function of its arguments: same
 * tokens in, same result out, every time. **Never guess**: an unresolved or
 * ambiguous input returns null rather than a best-effort value.
 */
class Normalizer(private val lexicon: Lexicon) {

    /** 100 and 1,000 combine with the running total but do not close a group (English "two thousand five hundred"). */
    private val minorScales = setOf(BigDecimal("100"), BigDecimal("1000"))

    /** 1,00,000 (lakh) and 1,00,00,000 (crore) close a group — the next word starts a new one. */
    private val majorScales = setOf(BigDecimal("100000"), BigDecimal("10000000"))

    /**
     * Resolves ONE token to a numeric value: a plain Arabic/decimal literal
     * (optionally with a glued '%'), a Tamil numeral-digit run, or a lexicon
     * number word (Tamil, English, fraction, half-compound or compound
     * thousand). Returns null for anything else.
     */
    private fun tokenValue(token: String): BigDecimal? {
        val stripped = if (token.endsWith("%")) token.dropLast(1) else token
        if (stripped.isEmpty()) return null
        stripped.toBigDecimalOrNull()?.let { return it }
        lexicon.tamilDigitsToNumber(stripped)?.let { return it }
        lexicon.lookupNumberWord(stripped)?.let { return it }
        return null
    }

    /**
     * Parses a contiguous run of number-word tokens using Indian-numbering
     * compounding ("ஒரு லட்சம் இருபதாயிரம்" -> 120000, build plan §5.4).
     *
     * Contract: [tokens] must already be an isolated numeric phrase — every
     * token must resolve to a value, or this returns null. It never silently
     * drops a token it could not parse; a caller windowing a larger sentence
     * is responsible for isolating the numeric run first ([numberNear] does
     * this for the anchor-based callers below).
     */
    fun parseNumber(tokens: List<String>): BigDecimal? {
        if (tokens.isEmpty()) return null
        val values = tokens.map { tokenValue(it) ?: return null }
        var total = BigDecimal.ZERO
        var current = BigDecimal.ZERO
        for (v in values) {
            when {
                v in majorScales -> {
                    val multiplier = if (current == BigDecimal.ZERO) BigDecimal.ONE else current
                    current = multiplier * v
                    total += current
                    current = BigDecimal.ZERO
                }
                v in minorScales -> {
                    val multiplier = if (current == BigDecimal.ZERO) BigDecimal.ONE else current
                    current = multiplier * v
                }
                else -> current += v
            }
        }
        total += current
        return total
    }

    /**
     * Searches outward from [anchorIdx] (closest first, left before right at
     * equal distance) for the nearest token within [maxDistance] that
     * resolves to a number. This is the "unit word within 2 tokens" trap
     * rule from §5.3 made concrete: [anchorIdx] is always a unit/marker
     * token, so a lone number with no marker anywhere near it is never
     * reached by this search at all.
     */
    private fun numberNear(window: List<String>, anchorIdx: Int, maxDistance: Int): BigDecimal? {
        for (d in 1..maxDistance) {
            val left = anchorIdx - d
            if (left in window.indices) tokenValue(window[left])?.let { return it }
            val right = anchorIdx + d
            if (right in window.indices) tokenValue(window[right])?.let { return it }
        }
        return null
    }

    private fun markerIndex(window: List<String>, group: Collection<String>): Int? =
        window.indices.firstOrNull { i -> lexicon.bestTokenMatch(window[i], group) != null }

    /**
     * Finds a percent value in [window]: either a single token carrying both
     * the number and a glued '%' ("8%"), or a percent-marker token
     * (percent / சதவீதம் / ...) with a number within 2 tokens of it. Returns
     * null if no percent marker is present at all — the "ஒரு"/"ஆறு" trap
     * (§5.3): a bare number is never treated as a percent.
     *
     * Scope note: only single-token markers are matched (plus glued '%').
     * Space-containing markers in the lexicon ("per cent", "per annum",
     * "p.a.") are data for a future phrase-level pass; no required test case
     * in this phase needs them, and it is safer to under-detect than guess.
     */
    fun parsePercent(window: List<String>): BigDecimal? {
        for (tok in window) {
            if (tok.length > 1 && tok.endsWith("%")) {
                tokenValue(tok)?.let { return it }
            }
        }
        val idx = markerIndex(window, lexicon.data.percentMarkers) ?: return null
        return numberNear(window, idx, maxDistance = 2)
    }

    /**
     * Finds a duration in [window], converted to months: a year-unit marker
     * with a nearby number gives `n * 12` months; a month-unit marker gives
     * `n` months directly ("அஞ்சு வருஷம்" -> 60, "18 months" -> 18, §5.4).
     * Null if no year/month unit marker is present — same trap rule as
     * [parsePercent].
     */
    fun parseDurationMonths(window: List<String>): Int? {
        val yearIdx = markerIndex(window, lexicon.data.yearUnits)
        val monthIdx = markerIndex(window, lexicon.data.monthUnits)
        val (idx, isYear) = when {
            yearIdx != null && monthIdx != null -> if (yearIdx <= monthIdx) yearIdx to true else monthIdx to false
            yearIdx != null -> yearIdx to true
            monthIdx != null -> monthIdx to false
            else -> return null
        }
        val n = numberNear(window, idx, maxDistance = 2) ?: return null
        val months = if (isYear) n.multiply(BigDecimal(12)) else n
        return months.setScale(0, RoundingMode.HALF_UP).intValueExact()
    }

    /**
     * Decides which claim (if any) a duration phrase belongs to — build plan
     * §5.4's disambiguation rule, made explicit rather than left to the
     * extractor to re-derive:
     *  - "policy term" (without an explicit lock-in word) -> [NoClaim].
     *  - a lock-in word present -> [LockIn].
     *  - a liquidity/withdrawal word plus an "after"-style marker -> [WithdrawableAfter].
     *  - none of the above -> null (ambiguous; the extractor marks
     *    NORMALIZATION_AMBIGUOUS rather than guessing).
     */
    fun disambiguateDuration(window: List<String>): DurationDisambiguation? {
        val months = parseDurationMonths(window) ?: return null
        val text = window.joinToString(" ")
        val hasTerm = lexicon.textContainsAny(text, lexicon.data.termWords)
        val hasLockIn = lexicon.textContainsAny(text, lexicon.data.lockinWords)
        val hasAfter = lexicon.textContainsAny(text, lexicon.data.afterMarkers)
        val hasLiquidity = lexicon.textContainsAny(text, lexicon.data.liquidityWords)
        return when {
            hasTerm && !hasLockIn -> DurationDisambiguation.NoClaim
            hasLockIn -> DurationDisambiguation.LockIn(months)
            hasAfter && hasLiquidity -> DurationDisambiguation.WithdrawableAfter(months)
            else -> null
        }
    }
}

/** Convenience: tokenize then normalize, for callers that start from raw text. */
fun Normalizer.parsePercentOf(text: String): BigDecimal? = parsePercent(Tokenizer.tokenize(text))

/** Convenience: tokenize then normalize, for callers that start from raw text. */
fun Normalizer.parseDurationMonthsOf(text: String): Int? = parseDurationMonths(Tokenizer.tokenize(text))
