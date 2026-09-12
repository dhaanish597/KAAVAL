package app.vaakku.ui.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The two values the Receipt screen formats itself (§6.6 screen 5).
 *
 * Neither is decoration. The duration is a statement about how long somebody was
 * spoken to, and the short head is what a person compares by eye against the
 * hash printed on the grievance packet — a formatter that drops a character
 * would make that comparison fail on a record that is perfectly good.
 */
class ReceiptScreenFormatTest {

    @Test
    fun `a sales conversation reads as mm ss`() {
        assertEquals("00:00", duration(0))
        assertEquals("00:01", duration(1_000))
        assertEquals("00:59", duration(59_999))
        assertEquals("01:00", duration(60_000))
        assertEquals("04:12", duration(252_000))
        assertEquals("59:59", duration(3_599_000))
    }

    @Test
    fun `a phone left on the table grows an hours field`() {
        assertEquals("1:00:00", duration(3_600_000))
        assertEquals("1:02:03", duration(3_723_000))
        assertEquals("10:00:00", duration(36_000_000))
    }

    @Test
    fun `a negative duration reads as zero rather than as a negative time`() {
        // Reachable only if a session's end were recorded before its start, which
        // would be a clock change mid-session. "-1:-3" on a buyer's record would
        // be worse than an honest zero.
        assertEquals("00:00", duration(-1))
        assertEquals("00:00", duration(-60_000))
    }

    @Test
    fun `the short head is the first sixteen characters in groups of four`() {
        val head = "0123456789abcdef" + "ffffffffffffffff" + "0".repeat(32)

        assertEquals("0123 4567 89ab cdef", shortHead(head))
    }

    @Test
    fun `a head shorter than sixteen characters is not padded out`() {
        // Cannot happen with SHA-256, and the formatter still must not invent
        // characters that are not in the hash.
        assertEquals("abc", shortHead("abc"))
        assertEquals("abcd ef", shortHead("abcdef"))
        assertEquals("", shortHead(""))
    }
}
