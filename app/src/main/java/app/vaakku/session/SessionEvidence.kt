package app.vaakku.session

import android.content.Context
import android.graphics.Bitmap
import app.vaakku.asr.Iso8601
import app.vaakku.domain.model.Box
import java.io.File

/**
 * The evidence folder for one scan session — build plan §6.4, §7.3.
 *
 * §6.4 fixes both the layout and the encoding:
 * `files/sessions/<sessionId>/page_<n>.jpg` for each captured page, and
 * `files/sessions/<sessionId>/crops/` for one JPEG crop per document row that
 * produced an observation, quality 80, at most 800 px wide. §7.3 later copies
 * this folder out to `Download/Vaakku/<sessionId>/` for the grievance packet,
 * which is why the names here are already the names that file expects.
 *
 * Internal app storage, not `Download/`: a page photograph is the buyer's
 * document, and it stays app-private until the buyer exports a packet
 * deliberately. (`EvidenceExport` writes to `Download/` because a debug
 * transcript is a developer artefact; a photograph of someone's policy is not.)
 *
 * **Images only.** No audio is written here, or anywhere else in this subsystem
 * — CLAUDE.md #4 allows images to touch disk and never allows audio to.
 *
 * P5 owns session lifecycle and will pass in the real session id; this class
 * only needs the id and a `filesDir`, so it works unchanged when it does.
 */
class SessionEvidence(
    /** `<filesDir>/sessions/<sessionId>`. Created lazily on the first write. */
    val sessionDir: File,
) {

    /** `<sessionDir>/crops`. */
    val cropsDir: File = File(sessionDir, "crops")

    /** `sessions/<id>/` — the tail of the path, for showing on screen. */
    val displayPath: String = "files/sessions/${sessionDir.name}/"

    /**
     * Writes one captured page as `page_<pageNumber>.jpg`.
     *
     * [masked] must already have been through [app.vaakku.ocr.PrivacyMask];
     * this class does no masking of its own and cannot tell whether a bitmap
     * has been masked, so the ordering is the caller's to keep.
     */
    fun writePageImage(masked: Bitmap, pageNumber: Int): File {
        sessionDir.mkdirs()
        val file = File(sessionDir, "page_$pageNumber.jpg")
        file.outputStream().use { out -> masked.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        return file
    }

    /**
     * Writes the region of [masked] described by [box] as
     * `crops/page<pageNumber>_row<index>.jpg`, or returns null if the box does
     * not intersect the bitmap at all (an OCR box is in the captured image's
     * own pixel space, so this should not happen — but a crop of zero pixels
     * would be a corrupt file rather than a useful one).
     *
     * The box is padded by [CROP_PADDING_PX] before clamping: OCR boxes sit
     * tight against the glyphs, and a crop with the descenders shaved off is
     * harder to check against the paper than one with a little air around it.
     */
    fun writeCrop(masked: Bitmap, box: Box, pageNumber: Int, index: Int): File? {
        val left = (box.left - CROP_PADDING_PX).coerceIn(0, masked.width)
        val top = (box.top - CROP_PADDING_PX).coerceIn(0, masked.height)
        val right = (box.right + CROP_PADDING_PX).coerceIn(0, masked.width)
        val bottom = (box.bottom + CROP_PADDING_PX).coerceIn(0, masked.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null

        cropsDir.mkdirs()
        val cropped = Bitmap.createBitmap(masked, left, top, width, height)
        val scaled = scaleToMaxWidth(cropped)
        val file = File(cropsDir, "page${pageNumber}_row$index.jpg")
        file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }

        // Recycle only what this function allocated. `createBitmap` can hand
        // back the source itself when the region is the whole bitmap, and
        // `createScaledBitmap` can hand back its input when no scaling was
        // needed, so both are compared by identity before being released.
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== masked) cropped.recycle()
        return file
    }

    private fun scaleToMaxWidth(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_CROP_WIDTH_PX) return bitmap
        val height = (bitmap.height.toLong() * MAX_CROP_WIDTH_PX / bitmap.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, MAX_CROP_WIDTH_PX, height, true)
    }

    companion object {
        /** §6.4: "JPEG q80". Used for the page image too, so there is one setting, not two. */
        const val JPEG_QUALITY = 80

        /** §6.4: "max 800 px wide". */
        const val MAX_CROP_WIDTH_PX = 800

        /** Air around a tight OCR box, in captured-image pixels. */
        const val CROP_PADDING_PX = 8

        /** `<filesDir>/sessions/<sessionId>`. */
        fun forSession(context: Context, sessionId: String): SessionEvidence =
            SessionEvidence(File(File(context.filesDir, "sessions"), sessionId))

        /**
         * A session id for a P3 scan session.
         *
         * P5 owns real session lifecycle and will mint these itself; until then
         * a timestamp is enough to keep five G3 scan sessions in five separate
         * folders. [Iso8601.stamp] is reused rather than re-derived so every
         * artefact this app writes carries the same stamp format.
         */
        fun newSessionId(): String = "scan_${Iso8601.stamp()}"
    }
}
