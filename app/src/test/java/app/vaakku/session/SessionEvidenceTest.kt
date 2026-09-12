package app.vaakku.session

import app.vaakku.domain.model.Box
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `SessionEvidence.cropRect` is the one crash-capable computation in the scan
 * path: `Bitmap.createBitmap` throws if the region leaves the source, and it
 * would take the whole page's observations with it (build plan §6.4).
 *
 * Deliberately expressed as a function on plain `Int`s and a `Box` — the same
 * shape `OcrLineMapper` uses for its `Rect` arithmetic — so none of this needs
 * Robolectric or a device.
 */
class SessionEvidenceTest {

    @Test
    fun `a box well inside the image gains its padding on every side`() {
        val rect = SessionEvidence.cropRect(
            box = Box(left = 100, top = 200, right = 500, bottom = 240),
            imageWidth = 3000,
            imageHeight = 4000,
            padding = 8,
        )!!
        assertEquals(92, rect.left)
        assertEquals(192, rect.top)
        assertEquals(416, rect.width)
        assertEquals(56, rect.height)
    }

    @Test
    fun `a box at the top-left corner clamps to the image instead of going negative`() {
        // Bitmap.createBitmap(-8, -8, ...) throws. A row of text touching the
        // edge of the frame is ordinary, not exceptional.
        val rect = SessionEvidence.cropRect(
            box = Box(left = 0, top = 0, right = 120, bottom = 30),
            imageWidth = 3000,
            imageHeight = 4000,
            padding = 8,
        )!!
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(128, rect.width)
        assertEquals(38, rect.height)
    }

    @Test
    fun `a box against the bottom-right edge clamps to the image bounds`() {
        val rect = SessionEvidence.cropRect(
            box = Box(left = 2900, top = 3950, right = 3000, bottom = 4000),
            imageWidth = 3000,
            imageHeight = 4000,
            padding = 8,
        )!!
        assertEquals(2892, rect.left)
        assertEquals(3942, rect.top)
        // right/bottom clamp at the image edge: 3000 - 2892, 4000 - 3942.
        assertEquals(108, rect.width)
        assertEquals(58, rect.height)
    }

    @Test
    fun `a box entirely off the image yields null rather than a zero-pixel crop`() {
        val rect = SessionEvidence.cropRect(
            box = Box(left = -400, top = -300, right = -100, bottom = -50),
            imageWidth = 3000,
            imageHeight = 4000,
            padding = 8,
        )
        assertNull(rect)
    }

    @Test
    fun `a box beyond the far edge yields null`() {
        val rect = SessionEvidence.cropRect(
            box = Box(left = 3200, top = 100, right = 3400, bottom = 140),
            imageWidth = 3000,
            imageHeight = 4000,
            padding = 8,
        )
        assertNull(rect)
    }

    @Test
    fun `an empty image yields null`() {
        val rect = SessionEvidence.cropRect(
            box = Box(left = 10, top = 10, right = 100, bottom = 40),
            imageWidth = 0,
            imageHeight = 0,
            padding = 8,
        )
        assertNull(rect)
    }

    @Test
    fun `a degenerate zero-height box survives as the padding band around it`() {
        // OCR should never emit one, but a rect of zero pixels is the crash
        // case, and padding is what keeps this side of it.
        val rect = SessionEvidence.cropRect(
            box = Box(left = 50, top = 50, right = 50, bottom = 50),
            imageWidth = 3000,
            imageHeight = 4000,
            padding = 8,
        )!!
        assertEquals(16, rect.width)
        assertEquals(16, rect.height)
    }

    @Test
    fun `the default padding is the declared constant`() {
        val explicit = SessionEvidence.cropRect(
            box = Box(left = 100, top = 100, right = 200, bottom = 140),
            imageWidth = 3000,
            imageHeight = 4000,
            padding = SessionEvidence.CROP_PADDING_PX,
        )
        val default = SessionEvidence.cropRect(
            box = Box(left = 100, top = 100, right = 200, bottom = 140),
            imageWidth = 3000,
            imageHeight = 4000,
        )
        assertEquals(explicit, default)
    }
}
