package app.vaakku.ocr

import app.vaakku.domain.model.OcrLine
import app.vaakku.domain.model.Thresholds
import java.util.Locale

/**
 * The `OcrLine.confidence` numbers one captured page actually produced —
 * build plan §6.4, and the one figure in the whole read-side chain that
 * nothing else in P3 measures.
 *
 * ### Why this class exists
 *
 * The chain is `OcrLineMapper.confidenceOf` -> [OcrLine.confidence] ->
 * `RowAssembler.minConfidence` (a `minOf` over the row's lines) ->
 * `WrittenExtractor`'s `confidence = row.minConfidence * plausibility` ->
 * `Reconciler`'s `filter { it.confidence >= thresholds.writtenMin }`, where
 * [Thresholds.writtenMin] is 0.70. The scan screen lists observations, which
 * are produced *before* that filter — so a page whose lines all read, say,
 * 0.31 would list every clause perfectly and still contribute nothing at all
 * to the ledger. Without this measurement that gap is invisible until the
 * reconciler runs, which on the demo path is a whole gate later.
 *
 * ML Kit's own javadoc for `Text.Line.getConfidence()` states the information
 * "will be unavailable (i.e. returns 0)" under some configurations. A page of
 * exact zeros therefore means something different from a page of low numbers:
 * the recognizer is not reporting the figure, and re-taking the photograph
 * cannot change it. [allZero] separates those two so the human is told which
 * one they are looking at instead of re-shooting a page that will never read
 * differently.
 *
 * ### This is a measurement, not a decision
 *
 * Nothing here alters a threshold, a confidence, or any observation. It
 * reports numbers about the reading and says, as a fact, which lines sit under
 * the number the reconciler keeps written readings at. It expresses no state
 * (§2.3's three user-facing states appear nowhere in it), no colour, and
 * nothing about any person. It lives on the Dev-menu scan surface, not on a
 * buyer-facing card.
 *
 * ### Not-a-number
 *
 * `Float.NaN` survives `coerceIn`, propagates through `minOf`, and fails
 * `>= 0.70` — so a NaN line is silently dropped by the reconciler exactly like
 * a low one, and must not be averaged into a number presented as a
 * measurement. Such lines are excluded from [min]/[median]/[belowGateCount]
 * and counted on their own in [unreadableCount].
 *
 * @property lineCount every line the page produced.
 * @property measuredCount lines carrying a usable (non-NaN) number.
 * @property unreadableCount lines whose number was NaN.
 * @property min lowest usable number, or null when none was usable.
 * @property median middle usable number (mean of the middle two when even).
 * @property belowGateCount usable numbers strictly below [gate].
 * @property allZero true when every usable number was exactly 0.0.
 * @property gate the `writtenMin` the reconciler filters written readings on.
 */
data class PageConfidence(
    val lineCount: Int,
    val measuredCount: Int,
    val unreadableCount: Int,
    val min: Double?,
    val median: Double?,
    val belowGateCount: Int,
    val allZero: Boolean,
    val gate: Double,
) {

    /** True when the lowest usable number on the page sits under [gate]. */
    val minBelowGate: Boolean get() = min != null && min < gate

    /**
     * The page's confidence measurement in plain lines, ready for the scan
     * screen. Built here rather than in the composable so the wording is unit
     * testable and so the rule about what may be said stays in one file.
     */
    fun reportLines(): List<String> {
        val gateText = format2(gate)
        val lines = mutableListOf<String>()

        lines += if (measuredCount == 0) {
            "line confidence: no usable number on any of $lineCount line(s)"
        } else {
            "line confidence: min ${format3(min)}, median ${format3(median)} " +
                "over $measuredCount of $lineCount line(s)"
        }

        if (measuredCount > 0) {
            lines += "under $gateText: $belowGateCount of $measuredCount line(s)"
        }
        if (unreadableCount > 0) {
            lines += "$unreadableCount line(s) reported not-a-number; those do not meet $gateText either"
        }

        if (allZero) {
            lines += "every line on this page read exactly 0.000. ML Kit documents that line " +
                "confidence is unavailable in some configurations and returns 0 then, so this is " +
                "the number not being reported — not a reading of the page. Re-taking the " +
                "photograph does not change it."
        }
        if (minBelowGate || (measuredCount == 0 && unreadableCount > 0)) {
            lines += "the reconciler keeps written readings at $gateText and above, so lines under " +
                "$gateText do not reach the ledger."
        }
        return lines
    }

    companion object {

        /** Measures [lines]; [gate] defaults to the reconciler's own `writtenMin`. */
        fun of(lines: List<OcrLine>, gate: Double = Thresholds.DEFAULT.writtenMin): PageConfidence {
            // NaN is filtered here, once, so nothing below has to think about
            // it: sorted() would place it last, minOf() would propagate it, and
            // a mean of two middles containing one would be NaN as well.
            val measured = lines.map { it.confidence }.filter { !it.isNaN() }.sorted()
            return PageConfidence(
                lineCount = lines.size,
                measuredCount = measured.size,
                unreadableCount = lines.size - measured.size,
                min = measured.firstOrNull(),
                median = medianOf(measured),
                belowGateCount = measured.count { it < gate },
                allZero = measured.isNotEmpty() && measured.all { it == 0.0 },
                gate = gate,
            )
        }

        /** Three decimals, or an em dash when there is no number to show. */
        fun format3(value: Double?): String =
            if (value == null || value.isNaN()) "—" else String.format(Locale.ROOT, "%.3f", value)

        /** Two decimals, for the gate itself. */
        fun format2(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

        private fun medianOf(sorted: List<Double>): Double? {
            if (sorted.isEmpty()) return null
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
        }
    }
}
