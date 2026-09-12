package app.vaakku.ocr

import android.graphics.Bitmap

/**
 * The named seat P4's NPU privacy masker sits in — build plan §6.4 and §6.5.
 *
 * §6.4 requires the page image saved into a session's evidence folder to be
 * masked **before** it is written, because §6.5's whole promise is that the
 * receipt contains the document and never a person: a benefit illustration is
 * photographed across a table, and an agent's hands, sleeve or face can easily
 * be in frame. Once the evidence file exists on disk, an unmasked person is in
 * it permanently — masking afterwards would be a second file, not a fix.
 *
 * The masker itself (selfie segmentation on the Hexagon NPU) is P4's work and
 * does not exist yet. So this function is honest about doing nothing: it
 * returns the same bitmap, and [STATUS_LINE] is the exact sentence the scan
 * screen prints under the preview so the human holding the phone knows what the
 * saved image does and does not contain. Nothing in the app labels, draws or
 * implies a mask that is not running (CLAUDE.md #8).
 *
 * **P4 replaces the body of [applyOrPassThrough] and [STATUS_LINE], and nothing
 * else.** Every caller already treats the return value as "the bitmap that is
 * safe to write", so a P4 implementation that returns a *new*, masked bitmap
 * needs no change at any call site — see [app.vaakku.ocr.PageScanner.scan],
 * which also documents that it takes ownership of both bitmaps.
 */
object PrivacyMask {

    /**
     * What the scan screen shows about masking today. Phrased as a statement of
     * fact plus the one mitigation available until P4 lands: keep people out of
     * the frame.
     */
    const val STATUS_LINE: String =
        "Privacy mask: not active yet (P4 builds it). Saved page images are exactly what the camera saw — keep people out of frame."

    /**
     * Returns the bitmap that may be written to the evidence folder.
     *
     * Today: [bitmap] itself, unchanged and unmasked. From P4: a masked copy.
     */
    fun applyOrPassThrough(bitmap: Bitmap): Bitmap = bitmap
}
