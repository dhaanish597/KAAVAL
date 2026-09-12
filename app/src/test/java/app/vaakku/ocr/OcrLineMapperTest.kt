package app.vaakku.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `OcrLineMapper` is the seam between ML Kit and the pure-JVM `OcrLine`
 * (build plan §6.4). These tests cover exactly the three things in it that
 * are pure JVM logic: the `Rect` → `Box` conversion, the null-box drop, and
 * the confidence policy — deliberately expressed as functions on plain Ints
 * and `Box`es rather than `android.graphics.Rect`/`Text.Line`, so none of
 * this needs Robolectric or a device.
 */
class OcrLineMapperTest {

    @Test
    fun `boxOf carries the four Rect corners straight through`() {
        val box = OcrLineMapper.boxOf(left = 10, top = 20, right = 130, bottom = 60)
        assertEquals(10, box.left)
        assertEquals(20, box.top)
        assertEquals(130, box.right)
        assertEquals(60, box.bottom)
    }

    @Test
    fun `a null box is dropped, not defaulted to zero`() {
        // A zero box would pull RowAssembler's median line height down and
        // silently corrupt row grouping for the whole page (see the KDoc on
        // OcrLineMapper.fromNullableBox) — so this must come back null, not
        // an OcrLine with box (0,0,0,0).
        val line = OcrLineMapper.fromNullableBox(
            box = null,
            text = "Assumed Rate of Return",
            confidence = 0.9,
            frameId = "frame-1",
        )
        assertNull(line)
    }

    @Test
    fun `a present box produces an OcrLine carrying every field through`() {
        val box = OcrLineMapper.boxOf(0, 0, 100, 24)
        val line = OcrLineMapper.fromNullableBox(
            box = box,
            text = "Guaranteed Returns: No",
            confidence = 0.87,
            frameId = "frame-7",
        )
        requireNotNull(line)
        assertEquals("Guaranteed Returns: No", line.text)
        assertEquals(box, line.box)
        assertEquals(0.87, line.confidence, 1e-9)
        assertEquals("frame-7", line.frameId)
    }

    @Test
    fun `confidenceOf passes a normal value through unchanged`() {
        assertEquals(0.0, OcrLineMapper.confidenceOf(0.0f), 1e-9)
        assertEquals(1.0, OcrLineMapper.confidenceOf(1.0f), 1e-9)
        assertEquals(0.42, OcrLineMapper.confidenceOf(0.42f), 1e-6)
    }

    @Test
    fun `confidenceOf clamps into zero to one even if ML Kit ever returns outside it`() {
        // Documented range is [0.0f, 1.0f], but this is a value crossing a
        // library boundary — defend the same way SegmentQuality defends its
        // own 0..1 outputs elsewhere in this app.
        assertEquals(0.0, OcrLineMapper.confidenceOf(-0.5f), 1e-9)
        assertEquals(1.0, OcrLineMapper.confidenceOf(1.5f), 1e-9)
    }
}
