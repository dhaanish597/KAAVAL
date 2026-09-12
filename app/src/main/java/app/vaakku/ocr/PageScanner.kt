package app.vaakku.ocr

import android.graphics.Bitmap
import app.vaakku.domain.extract.WrittenExtractor
import app.vaakku.domain.model.Box
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.session.SessionEvidence
import com.google.mlkit.vision.common.InputImage
import java.io.File

/**
 * What one captured page produced — everything the scan screen shows for it and
 * everything the reconciler is fed from it.
 *
 * [ocrElapsedMs] is ML Kit's own recognition time, straight from
 * [OcrScan.elapsedMs]: it excludes the mapping, the extractor, the JPEG writes
 * and the capture itself, so it is the figure the §11.5 "scan → clauses" budget
 * is written against and not a measurement of this class's bookkeeping.
 */
data class ScannedPage(
    val pageNumber: Int,
    val observations: List<Observation>,
    val ocrElapsedMs: Long,
    val lineCount: Int,
    val pageImage: File,
    val cropCount: Int,
)

/**
 * One captured page → OCR → written observations → evidence on disk — build
 * plan §6.4, the Android half of §5.6.
 *
 * The seam is deliberately thin: ML Kit and `android.graphics` stop here, and
 * everything past [WrittenExtractor] is the pure-JVM domain module (CLAUDE.md
 * #5). Nothing in this class decides anything about a claim — it reads a page
 * and records where each reading came from. The reconciler, not this class,
 * compares.
 *
 * **No audio is written by this code path**, in any build type (CLAUDE.md #4).
 */
class PageScanner(
    private val recognizer: MlKitTextRecognizer,
    private val evidence: SessionEvidence,
) {

    /** Stateless across pages; one instance because constructing it per page would be waste. */
    private val extractor = WrittenExtractor()

    /**
     * Recognises [page], extracts its observations, and writes the page image
     * and one crop per observed row.
     *
     * **Takes ownership of [page]** — it is recycled before this returns, along
     * with anything [PrivacyMask] allocated. The caller must not touch it
     * afterwards.
     */
    suspend fun scan(page: Bitmap, pageNumber: Int): ScannedPage {
        val frameId = "page_$pageNumber"
        val scan = recognizer.recognize(InputImage.fromBitmap(page, 0), frameId)

        // ONE extract() PER CAPTURED PAGE. Never over several pages' lines
        // concatenated together, however tempting batching looks.
        //
        // RowAssembler clusters lines by vertical pixel position, and each
        // photograph has its own coordinate space starting near (0, 0) — the
        // top of page 6 sits at the same y as the top of page 5. Merge a row
        // from one page with a row from the other and the result is an
        // observation whose text appears on neither page: a clause the document
        // does not contain, quoted as if it did.
        //
        // RowAssembler does now partition its input by frameId first, so a
        // concatenated call would not actually cross frames today. That is
        // defence in depth, not a licence to batch: it makes correctness depend
        // on every caller minting a distinct frameId, where calling per page
        // makes it depend on nothing. One page in, one page's observations out.
        //
        // Accumulating the resulting Observations across pages is the correct
        // way to span pages, and costs nothing: Reconciler already keeps
        // written observations per claim type as a list, and the five G3
        // clauses are deliberately spread over printed pages 5 and 6.
        val extracted = extractor.extract(scan.lines)

        // §6.4: the saved page image is masked BEFORE it is written. Today
        // PrivacyMask passes the bitmap straight through and says so on screen;
        // P4 replaces its body and this call site does not change. Crops are
        // cut from the same masked bitmap, because a crop goes into the
        // grievance packet exactly like the page does.
        val masked = PrivacyMask.applyOrPassThrough(page)
        val pageImage = evidence.writePageImage(masked, pageNumber)

        // One crop per row, not per observation: a table row such as
        // "Guaranteed Returns | No" can produce two observations from the same
        // pixels, and writing that region twice would put two identical JPEGs
        // in the packet. Keyed by the provenance box, which is the row's union
        // box from RowAssembler.
        val cropByBox = mutableMapOf<Box, File>()
        val observations = extracted.map { observation ->
            val written = observation.provenance as? Provenance.Written ?: return@map observation
            val crop = cropByBox[written.box]
                ?: evidence.writeCrop(masked, written.box, pageNumber, cropByBox.size)
                    ?.also { cropByBox[written.box] = it }
                ?: return@map observation

            // cropFile is filled in HERE, not by WrittenExtractor: `domain/` is
            // pure JVM and must never learn a file path (CLAUDE.md #5), so it
            // builds Provenance.Written with cropFile = null and the Android
            // side completes it once the file actually exists.
            observation.copy(provenance = written.copy(cropFile = crop.absolutePath))
        }

        if (masked !== page) masked.recycle()
        page.recycle()

        return ScannedPage(
            pageNumber = pageNumber,
            observations = observations,
            ocrElapsedMs = scan.elapsedMs,
            lineCount = scan.lines.size,
            pageImage = pageImage,
            cropCount = cropByBox.size,
        )
    }
}
