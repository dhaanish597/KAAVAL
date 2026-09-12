package app.vaakku.domain.extract

import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.OcrLine
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.RateQualifier
import app.vaakku.domain.model.Source
import java.math.BigDecimal
import java.util.UUID

/**
 * Turns OCR output into zero or more [Observation]s per row — build plan
 * §5.6. Rows come from [RowAssembler] so a label cell and its value cell
 * read as one line of text. Regex rules are exactly the ones in §5.6,
 * case-insensitive; a row producing no match for a type simply contributes
 * nothing for it — the reconciler tells NOT_IN_DOCUMENT (no written
 * observation, scan completed) apart from UNCERTAIN (a low-confidence one)
 * apart from a real DIFFERS, never this extractor.
 */
class WrittenExtractor {

    private companion object {
        val RATE_LABEL = Regex("rate of return|return|interest|assumed|illustrat", RegexOption.IGNORE_CASE)
        val PERCENT = Regex("""(\d+(?:\.\d+)?)\s*%""")
        val ILLUSTRATIVE_HINT = Regex("illustrat|assumed|projected|non-guaranteed", RegexOption.IGNORE_CASE)

        // "Guaranteed Returns on premiums paid: No." (real prop document §6) — the
        // real sentence puts a short qualifier ("on premiums paid") between the
        // label and the "No", which the original tight pattern did not tolerate.
        //
        // Fix round 1 found the first widening (an arbitrary-word gap) let a
        // DETERMINER "no" through — "Guaranteed Returns apply throughout; no
        // exceptions." A trailing lookahead fixed that: "no" must be the row's
        // terminal value (only trailing punctuation may follow it), never a
        // word introducing a noun.
        //
        // Fix round 2: the lookahead alone was still not enough, because the
        // GAP could still be arbitrary words (\S+) — any unrelated content
        // within four words of a *later, unrelated* terminal "no" still fired:
        // "Guaranteed Returns do not mean no." (a hedge — rule 2 says silence)
        // and, worse, "Guaranteed Returns on maturity Loan Facility No" —
        // exactly the shape RowAssembler produces when a multi-column table
        // row puts an unrelated column's "No" cell after the "Guaranteed
        // Returns" label, which this table-heavy document risks for real.
        // Round 2's fix — a *whitelist* of individual connective words,
        // repeatable up to 4 times — was itself still wrong: it was a bag of
        // words, not a phrase. Order was not enforced and any word could
        // repeat, so "Guaranteed Returns Paid Premium No" (reordered) and
        // "Guaranteed Returns paid paid paid paid No" (pure repetition) both
        // still matched. Worse, "premium(s) paid" alone (without "on") is
        // reused throughout this document for entirely unrelated clauses —
        // Surrender Value, Lapse, Paid-up Value, Free-Look, Revival, Tax
        // Benefits (`grep -in paid <the whole document>`, fix round 3) — so a
        // RowAssembler join of the "Guaranteed Returns" label with any of
        // *those* cells' "No" was a real, not hypothetical, false-positive
        // path.
        //
        // Fix round 3: the gap is now the single literal ordered phrase "on
        // [the] premium(s) paid", not a repeatable set. Verified this is the
        // *only* place in the whole document (all 10 pages) where "on" is
        // immediately followed by "premium(s) paid"
        // (`grep -inE "on (the )?premiums? paid" <the whole document>` finds
        // exactly one line: the real §6 sentence). Every other "premium(s)
        // paid" occurrence is preceded by "the", "total", "any", or starts a
        // sentence — never "on" — so none of them can complete this phrase,
        // no matter what RowAssembler joins them with. Reordering, repeating,
        // or dropping "on" all now break the match before "no" is even
        // reachable. If a genuinely different real connective phrase ever
        // turns up, it should be added as its own literal alternative, cited
        // by source line — never loosened back into a repeatable word class.
        // GUARANTEE_TRUE is deliberately left tight: widening it too is not
        // needed by any known document text, and a tight positive match is the
        // safer default (a missed "yes" is silence; a loose one risks reading
        // an unrelated "yes" nearby as this policy's guarantee).
        val GUARANTEE_FALSE = Regex(
            """not guaranteed|non-guaranteed|guaranteed returns?(?:\s+on\s+(?:the\s+)?premiums?\s+paid)?\s*:?\s*no\b(?=[\s.,;:)'"]*$)""",
            RegexOption.IGNORE_CASE,
        )
        val GUARANTEE_TRUE = Regex("""guaranteed returns?\s*:?\s*(yes|\d)""", RegexOption.IGNORE_CASE)

        val LOCK_IN_LABEL = Regex("""lock[- ]?in(\s+period)?""", RegexOption.IGNORE_CASE)
        val DURATION = Regex("""(\d+)\s*(years?|months?)""", RegexOption.IGNORE_CASE)

        val SURRENDER_VALUE = Regex("surrender value", RegexOption.IGNORE_CASE)
        val NIL = Regex("nil|zero|not payable", RegexOption.IGNORE_CASE)
        val BEFORE_DURATION = Regex(
            """(before|until|during).{0,30}?(\d+)(st|nd|rd|th)?\s*(policy\s+)?years?""",
            RegexOption.IGNORE_CASE,
        )

        val VOLUNTARY = Regex(
            """voluntary|not (a )?(mandatory|condition)|independent of (any )?loan""",
            RegexOption.IGNORE_CASE,
        )

        val CHARGE_LABEL = Regex(
            """(premium allocation|policy administration|mortality|surrender) charge|commission""",
            RegexOption.IGNORE_CASE,
        )
        val PERCENT_OR_RUPEE = Regex("""(\d+(?:\.\d+)?)\s*%|₹|Rs\.?\s*\d""", RegexOption.IGNORE_CASE)

        // Format plausibility (§5.6): "percent 0-30, years 0-40; otherwise 0" —
        // an OCR misread producing an out-of-range value is not trusted.
        val PLAUSIBLE_PERCENT = BigDecimal.ZERO..BigDecimal(30)
        const val PLAUSIBLE_MONTHS_MAX = 40 * 12
    }

    fun extract(lines: List<OcrLine>): List<Observation> {
        val rows = RowAssembler.assemble(lines)
        return rows.flatMap { row ->
            listOfNotNull(
                matchRate(row),
                matchGuarantee(row),
                matchLockIn(row),
                matchLiquidity(row),
                matchBundling(row),
                matchCharges(row),
            )
        }
    }

    private fun matchRate(row: Row): Observation? {
        if (!RATE_LABEL.containsMatchIn(row.text)) return null
        val percents = PERCENT.findAll(row.text).mapNotNull { it.groupValues[1].toBigDecimalOrNull() }.toSet()
        if (percents.isEmpty()) return null
        val plausibility = if (percents.all { it in PLAUSIBLE_PERCENT }) 1.0 else 0.0
        val qualifier = if (ILLUSTRATIVE_HINT.containsMatchIn(row.text)) RateQualifier.ILLUSTRATIVE else RateQualifier.ASSERTED
        return observation(
            row, ClaimType.RETURN_RATE, ClaimValue.Rate(percents, qualifier),
            hedged = false, confidence = row.minConfidence * plausibility,
        )
    }

    private fun matchGuarantee(row: Row): Observation? {
        val guaranteed = when {
            GUARANTEE_FALSE.containsMatchIn(row.text) -> false
            GUARANTEE_TRUE.containsMatchIn(row.text) -> true
            else -> return null
        }
        return observation(row, ClaimType.GUARANTEE, ClaimValue.Guarantee(guaranteed), hedged = false, confidence = row.minConfidence)
    }

    private fun matchLockIn(row: Row): Observation? {
        if (!LOCK_IN_LABEL.containsMatchIn(row.text)) return null
        val m = DURATION.find(row.text) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val isYear = m.groupValues[2].startsWith("year", ignoreCase = true)
        val months = if (isYear) n * 12 else n
        val plausibility = if (months in 0..PLAUSIBLE_MONTHS_MAX) 1.0 else 0.0
        return observation(row, ClaimType.LOCK_IN, ClaimValue.LockIn(months), hedged = false, confidence = row.minConfidence * plausibility)
    }

    private fun matchLiquidity(row: Row): Observation? {
        if (!SURRENDER_VALUE.containsMatchIn(row.text)) return null
        if (!NIL.containsMatchIn(row.text)) return null
        val m = BEFORE_DURATION.find(row.text) ?: return null
        val years = m.groupValues[2].toIntOrNull() ?: return null
        val months = years * 12
        val plausibility = if (months in 0..PLAUSIBLE_MONTHS_MAX) 1.0 else 0.0
        return observation(
            row, ClaimType.LIQUIDITY,
            ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = months),
            hedged = false, confidence = row.minConfidence * plausibility,
        )
    }

    private fun matchBundling(row: Row): Observation? {
        if (!VOLUNTARY.containsMatchIn(row.text)) return null
        return observation(row, ClaimType.BUNDLING, ClaimValue.Bundling(requiredForLoan = false), hedged = false, confidence = row.minConfidence)
    }

    /**
     * §7 of the real prop document has three "Premium Allocation Charge" rows
     * (year 1: 5%; years 2-5: 2%; year 6 onward: Nil). Deliberate, documented
     * behaviour for each (§5.6, this task's step 5):
     *  - the 5% row produces `Charges(anyCharges = true, percent = 5, ...)`.
     *  - the 2% row *also* produces its own `Charges(percent = 2, ...)` — one
     *    label can legitimately appear more than once in a document (a
     *    tiered charge schedule) and each row is a separate observation; nothing
     *    here should collapse or dedupe them. A second, differently-valued
     *    CHARGES observation is not a bug, and the reconciler sees the whole
     *    `List<Observation>` for the type.
     *  - the "Nil" row produces **no observation at all**. [PERCENT_OR_RUPEE]
     *    requires a machine-parseable "%" or currency figure; "Nil" is prose,
     *    not a number, and guessing anyCharges=false from the word "Nil" would
     *    be exactly the kind of inference CLAUDE.md #2 forbids under any doubt
     *    (is it this charge that is nil, or the whole charge that is waived
     *    only after year 6?). Silence, not a claim, is correct here.
     */
    private fun matchCharges(row: Row): Observation? {
        if (!CHARGE_LABEL.containsMatchIn(row.text)) return null
        if (!PERCENT_OR_RUPEE.containsMatchIn(row.text)) return null
        val percent = PERCENT.find(row.text)?.groupValues?.get(1)?.toBigDecimalOrNull()
        val label = CHARGE_LABEL.find(row.text)?.value
        return observation(
            row, ClaimType.CHARGES,
            ClaimValue.Charges(anyCharges = true, percent = percent, label = label),
            hedged = false, confidence = row.minConfidence,
        )
    }

    private fun observation(
        row: Row,
        type: ClaimType,
        value: ClaimValue,
        hedged: Boolean,
        confidence: Double,
    ): Observation = Observation(
        id = UUID.randomUUID().toString(),
        source = Source.WRITTEN,
        type = type,
        value = value,
        hedged = hedged,
        negated = false,
        conditional = false,
        confidence = confidence,
        provenance = Provenance.Written(lineText = row.text, box = row.box, frameId = row.frameId, cropFile = null),
        tMs = 0L,
    )
}
