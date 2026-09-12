package app.vaakku.ocr

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.OcrLine
import app.vaakku.domain.model.Thresholds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `PageConfidence` is the one place in the read-side chain that measures the
 * number the reconciler's `writtenMin` filter actually gates on. These tests
 * pin the arithmetic (including the `Float.NaN` cases, which `coerceIn` does
 * not normalise and `minOf` propagates) and the two things the wording must
 * always distinguish: lines that read low, and a recognizer that is not
 * reporting the figure at all.
 */
class PageConfidenceTest {

    private fun line(confidence: Double, text: String = "Lock-in Period: 5 years") =
        OcrLine(text = text, box = Box(0, 0, 100, 24), confidence = confidence, frameId = "page_1")

    @Test
    fun `min and median come from the page's own lines`() {
        val page = PageConfidence.of(listOf(line(0.91), line(0.74), line(0.83)))
        assertEquals(0.74, page.min!!, 1e-9)
        assertEquals(0.83, page.median!!, 1e-9)
        assertEquals(3, page.measuredCount)
        assertEquals(0, page.belowGateCount)
        assertFalse(page.minBelowGate)
    }

    @Test
    fun `an even line count takes the mean of the middle two`() {
        val page = PageConfidence.of(listOf(line(0.90), line(0.70), line(0.80), line(0.60)))
        assertEquals(0.60, page.min!!, 1e-9)
        assertEquals(0.75, page.median!!, 1e-9)
    }

    @Test
    fun `lines under writtenMin are counted against the reconciler's own gate`() {
        assertEquals(0.70, Thresholds.DEFAULT.writtenMin, 1e-9)
        val page = PageConfidence.of(listOf(line(0.69), line(0.71), line(0.20)))
        assertEquals(0.70, page.gate, 1e-9)
        assertEquals(2, page.belowGateCount)
        assertTrue(page.minBelowGate)
        assertTrue(page.reportLines().any { it.contains("do not reach the ledger") })
    }

    @Test
    fun `a page of exact zeros is named as the number not being reported`() {
        // ML Kit's javadoc: line confidence "will be unavailable (i.e. returns
        // 0)" in some configurations. That is a different thing from a page
        // that genuinely read badly, and the human has to be told which.
        val page = PageConfidence.of(listOf(line(0.0), line(0.0), line(0.0)))
        assertTrue(page.allZero)
        val report = page.reportLines().joinToString(" ")
        assertTrue(report.contains("0.000"))
        assertTrue(report.contains("unavailable"))
        assertTrue(report.contains("does not change it"))
    }

    @Test
    fun `a merely low page is not reported as the unavailable case`() {
        val page = PageConfidence.of(listOf(line(0.31), line(0.44)))
        assertFalse(page.allZero)
        assertFalse(page.reportLines().any { it.contains("unavailable") })
        assertTrue(page.minBelowGate)
    }

    @Test
    fun `not-a-number is excluded from the measurement and counted on its own`() {
        val page = PageConfidence.of(listOf(line(Double.NaN), line(0.80), line(0.90)))
        assertEquals(1, page.unreadableCount)
        assertEquals(2, page.measuredCount)
        assertEquals(0.80, page.min!!, 1e-9)
        assertEquals(0.85, page.median!!, 1e-9)
        assertEquals(0, page.belowGateCount)
        assertTrue(page.reportLines().any { it.contains("not-a-number") })
    }

    @Test
    fun `a page of nothing but not-a-number shows no number at all`() {
        val page = PageConfidence.of(listOf(line(Double.NaN), line(Double.NaN)))
        assertNull(page.min)
        assertNull(page.median)
        assertFalse(page.allZero)
        assertFalse(page.minBelowGate)
        val report = page.reportLines().joinToString(" ")
        assertFalse(report.contains("NaN"))
        assertTrue(report.contains("no usable number"))
        assertTrue(report.contains("do not reach the ledger"))
    }

    @Test
    fun `an empty page reports nothing measurable and never prints NaN`() {
        val page = PageConfidence.of(emptyList())
        assertEquals(0, page.lineCount)
        assertNull(page.min)
        assertFalse(page.allZero)
        assertFalse(page.minBelowGate)
        assertFalse(page.reportLines().joinToString(" ").contains("NaN"))
    }

    @Test
    fun `formatting never renders NaN or a null as if it were a measurement`() {
        assertEquals("—", PageConfidence.format3(null))
        assertEquals("—", PageConfidence.format3(Double.NaN))
        assertEquals("0.700", PageConfidence.format3(0.7))
        assertEquals("0.70", PageConfidence.format2(0.7))
    }
}
