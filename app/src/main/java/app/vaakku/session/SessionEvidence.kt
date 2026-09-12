package app.vaakku.session

import android.content.Context
import android.graphics.Bitmap
import app.vaakku.asr.Iso8601
import app.vaakku.domain.model.Box
import java.io.File

/**
 * A clamped crop region in captured-image pixels: the result of padding an OCR
 * box and trimming it to the image. Plain `Int`s, no `android.*`, so the one
 * piece of crash-capable arithmetic in this class can be unit-tested on the
 * JVM (`SessionEvidenceTest`) rather than only on a phone.
 */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

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
 * Every write returns `File?` and answers `null` when the image did not
 * actually land. That is deliberate: a JPEG that failed half-way is still a
 * file, and recording its path in `Provenance.Written.cropFile` would give P5's
 * provenance sheet a blank image with nothing saying why. Silence is the right
 * failure (CLAUDE.md #2) as long as it is recorded silence.
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
     * Writes one captured page as `page_<pageNumber>.jpg`, or returns null if
     * the encode reported failure (the partial file is deleted).
     *
     * [masked] must already have been through [app.vaakku.ocr.PrivacyMask];
     * this class does no masking of its own and cannot tell whether a bitmap
     * has been masked, so the ordering is the caller's to keep.
     */
    fun writePageImage(masked: Bitmap, pageNumber: Int): File? {
        sessionDir.mkdirs()
        val file = File(sessionDir, "page_$pageNumber.jpg")
        return writeJpeg(masked, file, PAGE_JPEG_QUALITY)
    }

    /**
     * Writes the region of [masked] described by [box] as
     * `crops/page<pageNumber>_row<index>.jpg`, or returns null if the box does
     * not intersect the image or the encode reported failure.
     *
     * The box is padded by [CROP_PADDING_PX] before clamping — see [cropRect].
     */
    fun writeCrop(masked: Bitmap, box: Box, pageNumber: Int, index: Int): File? {
        val rect = cropRect(box, masked.width, masked.height) ?: return null

        cropsDir.mkdirs()
        val cropped = Bitmap.createBitmap(masked, rect.left, rect.top, rect.width, rect.height)
        val scaled = scaleToMaxWidth(cropped)
        val file = writeJpeg(scaled, File(cropsDir, "page${pageNumber}_row$index.jpg"), CROP_JPEG_QUALITY)

        // Recycle only what this function allocated. `createBitmap` can hand
        // back the source itself when the region is the whole bitmap, and
        // `createScaledBitmap` can hand back its input when no scaling was
        // needed, so both are compared by identity before being released.
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== masked) cropped.recycle()
        return file
    }

    /**
     * Encodes [bitmap] into [file], or returns null and removes the file when
     * `Bitmap.compress` reports failure.
     */
    private fun writeJpeg(bitmap: Bitmap, file: File, quality: Int): File? {
        val encoded = file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (!encoded) {
            file.delete()
            return null
        }
        return file
    }

    private fun scaleToMaxWidth(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_CROP_WIDTH_PX) return bitmap
        val height = (bitmap.height.toLong() * MAX_CROP_WIDTH_PX / bitmap.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, MAX_CROP_WIDTH_PX, height, true)
    }

    companion object {
        /**
         * §6.4 fixes q80 for the crops. The page image carries its own
         * constant even though it currently holds the same number: the page is
         * the §7.3 packet artefact and the crops are thumbnails beside a
         * quoted line, so one may be re-tuned without dragging the other with
         * it.
         */
        const val CROP_JPEG_QUALITY = 80

        /** See [CROP_JPEG_QUALITY]. */
        const val PAGE_JPEG_QUALITY = 80

        /** §6.4: "max 800 px wide". */
        const val MAX_CROP_WIDTH_PX = 800

        /** Air around a tight OCR box, in captured-image pixels. */
        const val CROP_PADDING_PX = 8

        /**
         * The padded, clamped crop region for [box] inside an image of
         * [imageWidth] x [imageHeight], or null when nothing of it lands on
         * the image.
         *
         * OCR boxes sit tight against the glyphs, and a crop with the
         * descenders shaved off is harder to check against the paper than one
         * with a little air around it — hence [padding]. Everything is then
         * clamped into the image, because `Bitmap.createBitmap` throws on a
         * region that leaves the source and this is the one computation here
         * that can crash a scan.
         */
        fun cropRect(
            box: Box,
            imageWidth: Int,
            imageHeight: Int,
            padding: Int = CROP_PADDING_PX,
        ): CropRect? {
            if (imageWidth <= 0 || imageHeight <= 0) return null
            val left = (box.left - padding).coerceIn(0, imageWidth)
            val top = (box.top - padding).coerceIn(0, imageHeight)
            val right = (box.right + padding).coerceIn(0, imageWidth)
            val bottom = (box.bottom + padding).coerceIn(0, imageHeight)
            val width = right - left
            val height = bottom - top
            if (width <= 0 || height <= 0) return null
            return CropRect(left, top, width, height)
        }

        /** `<filesDir>/sessions/<sessionId>`. */
        fun forSession(context: Context, sessionId: String): SessionEvidence =
            SessionEvidence(File(File(context.filesDir, "sessions"), sessionId))

        /**
         * A session id guaranteed not to name a folder that already exists.
         *
         * [Iso8601.stamp] has one-second resolution, and two "New scan
         * session" taps inside the same second would otherwise share a folder
         * while page numbering restarted at 1 — the second session would
         * overwrite the first session's `page_1.jpg`. Evidence that can
         * overwrite itself is worse than no evidence, so the id is checked
         * against the filesystem and suffixed until it is free. Checking the
         * disk also survives a process restart inside the same second, which a
         * process-scoped counter would not.
         *
         * [prefix] separates a real session (`session_…`, minted by
         * `MainActivity` when the user starts one) from a bench scan with no
         * session behind it (`scan_…`, the Dev menu). The receipt is built from
         * a session folder, so the two must be tellable apart by name when the
         * folder is all you have.
         */
        fun newSessionId(context: Context, prefix: String = "scan"): String {
            val stamp = Iso8601.stamp()
            val sessions = File(context.filesDir, "sessions")
            var candidate = "${prefix}_$stamp"
            var suffix = 2
            while (File(sessions, candidate).exists()) {
                candidate = "${prefix}_${stamp}_$suffix"
                suffix++
            }
            return candidate
        }
    }
}
