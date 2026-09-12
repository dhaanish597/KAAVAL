package app.vaakku.domain.extract

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.OcrLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Build plan §5.6 — group OcrLines whose vertical centres are within 0.6 * median line height, order by x. */
class RowAssemblerTest {

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int, confidence: Double = 0.95) =
        OcrLine(text = text, box = Box(left, top, right, bottom), confidence = confidence, frameId = "f1")

    @Test
    fun `empty input produces no rows`() {
        assertEquals(emptyList<Row>(), RowAssembler.assemble(emptyList()))
    }

    @Test
    fun `a label cell and a value cell at the same vertical position become one row, label first`() {
        val value = line("4% p.a. and 8% p.a.", left = 560, top = 300, right = 820, bottom = 330)
        val label = line("Assumed rate of return (illustrative)", left = 40, top = 300, right = 520, bottom = 330)
        val rows = RowAssembler.assemble(listOf(value, label)) // input order deliberately reversed
        assertEquals(1, rows.size)
        assertEquals("Assumed rate of return (illustrative) 4% p.a. and 8% p.a.", rows[0].text)
    }

    @Test
    fun `two lines far apart vertically stay in separate rows`() {
        val row1 = line("Assumed rate of return (illustrative)", 40, 300, 520, 330)
        val row2 = line("Returns are NOT guaranteed", 40, 350, 520, 380)
        val rows = RowAssembler.assemble(listOf(row1, row2))
        assertEquals(2, rows.size)
        assertEquals("Assumed rate of return (illustrative)", rows[0].text)
        assertEquals("Returns are NOT guaranteed", rows[1].text)
    }

    @Test
    fun `three cells on one visual row merge and order left to right`() {
        val c1 = line("Premium allocation charge:", 40, 500, 300, 530)
        val c2 = line("5%", 320, 505, 360, 525)
        val c3 = line("in year 1", 380, 500, 500, 530)
        val rows = RowAssembler.assemble(listOf(c3, c1, c2))
        assertEquals(1, rows.size)
        assertEquals("Premium allocation charge: 5% in year 1", rows[0].text)
    }

    @Test
    fun `row minConfidence is the minimum across its lines`() {
        val label = line("Lock-in period:", 40, 400, 300, 430, confidence = 0.98)
        val value = line("5 years", 320, 400, 420, 430, confidence = 0.62)
        val rows = RowAssembler.assemble(listOf(label, value))
        assertEquals(0.62, rows[0].minConfidence)
    }

    @Test
    fun `row box is the union of its lines' boxes`() {
        val label = line("Lock-in period:", 40, 400, 300, 430)
        val value = line("5 years", 320, 405, 420, 435)
        val rows = RowAssembler.assemble(listOf(label, value))
        assertEquals(Box(left = 40, top = 400, right = 420, bottom = 435), rows[0].box)
    }

    @Test
    fun `many rows down a page each stay distinct`() {
        val lines = (0 until 6).map { i -> line("row $i", 40, i * 60, 400, i * 60 + 30) }
        val rows = RowAssembler.assemble(lines)
        assertEquals(6, rows.size)
        assertTrue(rows.map { it.text } == listOf("row 0", "row 1", "row 2", "row 3", "row 4", "row 5"))
    }
}
