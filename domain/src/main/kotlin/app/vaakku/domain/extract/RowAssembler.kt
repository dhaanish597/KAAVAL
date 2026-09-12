package app.vaakku.domain.extract

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.OcrLine

/** One visual row of the document, possibly assembled from several OCR lines (a label cell and a value cell). */
data class Row(
    val lines: List<OcrLine>,
    val text: String,
    val box: Box,
    val minConfidence: Double,
    val frameId: String,
)

/**
 * Groups OCR lines into visual rows — build plan §5.6. ML Kit returns table
 * cells as separate lines; a benefit-illustration table's label ("Assumed
 * rate of return") and its value ("4% p.a. and 8% p.a.") are two different
 * [OcrLine]s at the same vertical position, and must read as one row.
 */
object RowAssembler {

    /**
     * Groups [lines] whose vertical centres are within `0.6 * medianLineHeight`
     * of each other, then orders each group left-to-right by x. The median is
     * computed once over the whole page, not per group, so the threshold is
     * stable regardless of how many lines end up near each other.
     */
    fun assemble(lines: List<OcrLine>): List<Row> {
        if (lines.isEmpty()) return emptyList()

        val heights = lines.map { (it.box.bottom - it.box.top).toDouble() }
        val threshold = 0.6 * median(heights)

        val sorted = lines.sortedBy { verticalCenter(it) }
        val clusters = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val open = clusters.lastOrNull()
            if (open != null && kotlin.math.abs(verticalCenter(line) - verticalCenter(open.last())) <= threshold) {
                open += line
            } else {
                clusters += mutableListOf(line)
            }
        }

        return clusters.map { cluster ->
            val orderedByX = cluster.sortedBy { it.box.left }
            Row(
                lines = orderedByX,
                text = orderedByX.joinToString(" ") { it.text },
                box = Box(
                    left = orderedByX.minOf { it.box.left },
                    top = orderedByX.minOf { it.box.top },
                    right = orderedByX.maxOf { it.box.right },
                    bottom = orderedByX.maxOf { it.box.bottom },
                ),
                minConfidence = orderedByX.minOf { it.confidence },
                frameId = orderedByX.first().frameId,
            )
        }
    }

    private fun verticalCenter(line: OcrLine): Double = (line.box.top + line.box.bottom) / 2.0

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }
}
