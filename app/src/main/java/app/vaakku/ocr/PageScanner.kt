package app.vaakku.ocr

import android.graphics.Bitmap
import app.vaakku.domain.extract.WrittenExtractor
import app.vaakku.domain.model.Box
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.session.SessionEvidence
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /**
     * The `OcrLine.confidence` numbers this page produced — the value
     * `RowAssembler`/`WrittenExtractor` carry into the reconciler's
     * `writtenMin` filter. Measured per page and shown on the scan screen so
     * a page that lists its clauses but sits under that number is visible
     * while the page is still in front of the camera. See [PageConfidence].
     */
    val confidence: PageConfidence,
    /**
     * Null when the page JPEG did not land — either a write failed (see
     * `SessionEvidence`) or [maskSummary] is
     * [PrivacyMask.MaskSummary.Withheld], meaning the masker could not vouch
     * for this page and nothing was written rather than something unmasked.
     */
    val pageImage: File?,
    val cropCount: Int,
    /**
     * What the privacy masker did to this page (§6.4, §6.5) — what the scan
     * screen prints beside the OCR timing, and the reason [pageImage] is null
     * when it is null for a privacy reason rather than a disk one.
     */
    val maskSummary: PrivacyMask.MaskSummary,
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
    private val privacyMask: PrivacyMask,
) {

    /** Stateless across pages; one instance because constructing it per page would be waste. */
    private val extractor = WrittenExtractor()

    /**
     * Recognises [page], extracts its observations, and writes the page image
     * and one crop per observed row.
     *
     * **Takes ownership of [page]** — it is recycled before this returns on
     * every path, success or failure, along with anything [PrivacyMask]
     * allocated. The caller must not touch it afterwards.
     */
    suspend fun scan(page: Bitmap, pageNumber: Int): ScannedPage {
        // The bitmap is tens of MB (see DocumentCamera's capture cap), so its
        // release is in a finally: a failed recognition, a disk error or a
        // cancelled scan must not strand one, or two bad taps turn a
        // recoverable error into an OutOfMemoryError.
        //
        // The identity check matters in both directions. When the masker ran,
        // `masked` is a second bitmap of the same size and both must go; when no
        // masker exists, PrivacyMask hands back `page` itself, and recycling it
        // here as well as below would be recycling a bitmap twice.
        var masked: Bitmap? = null
        try {
            return scanInternal(page, pageNumber) { masked = it }
        } finally {
            val maskedPage = masked
            if (maskedPage != null && maskedPage !== page && !maskedPage.isRecycled) maskedPage.recycle()
            if (!page.isRecycled) page.recycle()
        }
    }

    private suspend fun scanInternal(
        page: Bitmap,
        pageNumber: Int,
        onMasked: (Bitmap?) -> Unit,
    ): ScannedPage {
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

        // §6.4: the saved page image is masked BEFORE it is written, and if it
        // cannot be masked while the screen says masking is on, it is not
        // written at all — see PrivacyMask, which owns that decision. Crops are
        // cut from the same bitmap, because a crop goes into the grievance
        // packet exactly like the page does.
        val outcome = privacyMask.apply(page)
        val writable = outcome.safeToWrite
        onMasked(writable)

        // JPEG encoding and the writes themselves are blocking disk work, so
        // they run on the IO dispatcher; recognition and extraction above do
        // not. One block rather than one per crop: the encode travels with the
        // write it feeds, and ping-ponging dispatchers per crop would cost more
        // than it saves.
        //
        // A withheld page skips this entirely: there is no bitmap anyone is
        // allowed to write, so the observations keep cropFile = null exactly as
        // they would after a disk failure.
        val evidenceWritten = if (writable == null) {
            WrittenEvidence(extracted, pageImage = null, cropCount = 0)
        } else {
            withContext(Dispatchers.IO) { writeEvidence(writable, extracted, pageNumber) }
        }

        return ScannedPage(
            pageNumber = pageNumber,
            observations = evidenceWritten.observations,
            ocrElapsedMs = scan.elapsedMs,
            lineCount = scan.lines.size,
            // Measured on the mapped lines, i.e. exactly the numbers that go
            // on to RowAssembler.minConfidence and from there to the
            // reconciler's writtenMin filter.
            confidence = PageConfidence.of(scan.lines),
            pageImage = evidenceWritten.pageImage,
            cropCount = evidenceWritten.cropCount,
            maskSummary = outcome.summary,
        )
    }

    /** [writeEvidence]'s three results, so `scan` does not need three out-params. */
    private data class WrittenEvidence(
        val observations: List<Observation>,
        val pageImage: File?,
        val cropCount: Int,
    )

    /**
     * Writes the page image and one crop per observed row, and returns the
     * observations with [Provenance.Written.cropFile] filled in.
     *
     * **A write that fails costs its own file and nothing else.** An evidence
     * JPEG is evidence *about* an observation; losing one must not delete the
     * observation it describes, and a page whose `page_<n>.jpg` is already on
     * disk must not be discarded because crop three hit an IOException. So
     * each write is caught on its own: the observation simply keeps
     * `cropFile = null`, and the screen shows that plainly (CLAUDE.md #2 —
     * silence, but recorded silence).
     */
    private fun writeEvidence(
        masked: Bitmap,
        extracted: List<Observation>,
        pageNumber: Int,
    ): WrittenEvidence {
        val pageImage = runCatching { evidence.writePageImage(masked, pageNumber) }.getOrNull()

        // One crop per row, not per observation: a table row such as
        // "Guaranteed Returns | No" can produce two observations from the same
        // pixels, and writing that region twice would put two identical JPEGs
        // in the packet. Keyed by the provenance box, which is the row's union
        // box from RowAssembler.
        val cropByBox = mutableMapOf<Box, File>()
        val observations = extracted.map { observation ->
            val written = observation.provenance as? Provenance.Written ?: return@map observation
            val crop = cropByBox[written.box]
                ?: runCatching { evidence.writeCrop(masked, written.box, pageNumber, cropByBox.size) }
                    .getOrNull()
                    ?.also { cropByBox[written.box] = it }
                ?: return@map observation

            // cropFile is filled in HERE, not by WrittenExtractor: `domain/` is
            // pure JVM and must never learn a file path (CLAUDE.md #5), so it
            // builds Provenance.Written with cropFile = null and the Android
            // side completes it once the file actually exists.
            observation.copy(provenance = written.copy(cropFile = crop.absolutePath))
        }

        return WrittenEvidence(observations, pageImage, cropByBox.size)
    }
}
