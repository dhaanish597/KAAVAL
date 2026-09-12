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
     * of each other, then orders each group left-to-right by x. [lines] is
     * first partitioned by [OcrLine.frameId] — every [Box] is pixel coordinates
     * within *one* photo, so two different frames legitimately reuse the same
     * y-range; a row must never be built from lines out of two different
     * frames, however close their `top`/`bottom` happen to land. (Found by
     * inspection while writing RealPropDocumentTest for a multi-page scan
     * session, fix round 1: naively concatenating two frames' lines with
     * independently-zeroed coordinates produced a spurious cross-frame row.)
     * Frame order in the output follows each frame's first appearance in
     * [lines]; the median line height — and so the clustering threshold — is
     * computed per frame, not across the whole call, since a median mixing two
     * unrelated photos' line-height distributions was never meaningful.
     */
    fun assemble(lines: List<OcrLine>): List<Row> {
        if (lines.isEmpty()) return emptyList()
        return lines.groupBy { it.frameId }.values.flatMap { assembleOneFrame(it) }
    }

    private fun assembleOneFrame(lines: List<OcrLine>): List<Row> {
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
                // Safe now that every line in the cluster shares one frameId —
                // before this fix, a cross-frame cluster made this silently
                // wrong for downstream Provenance.Written.frameId (crop/source-
                // photo lookup).
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
