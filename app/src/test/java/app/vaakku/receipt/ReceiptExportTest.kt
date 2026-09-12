package app.vaakku.receipt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The part of §7.3 that can be checked without a phone: which files an export
 * picks up, and in what order.
 *
 * MediaStore and the Keystore need a device and are verified on one. What is
 * worth testing here is the allowlist — the second lock on CLAUDE.md #4, "audio
 * never touches disk" — because that is a rule about what must *not* be copied,
 * and a rule like that is only as good as the test that a stray file stays out.
 */
class ReceiptExportTest {

    @Test
    fun `pages come back in page order, not name order`(@TempDir dir: File) {
        listOf("page_1.jpg", "page_2.jpg", "page_10.jpg", "page_11.jpg").forEach { touch(dir, it) }

        val names = ReceiptExport.pageFiles(dir).map { it.name }

        // Sorted by name this would be page_1, page_10, page_11, page_2 — which
        // is only cosmetic in a folder listing but would also reorder the zip.
        assertEquals(listOf("page_1.jpg", "page_2.jpg", "page_10.jpg", "page_11.jpg"), names)
    }

    @Test
    fun `an unparseable page number sorts last instead of throwing`(@TempDir dir: File) {
        listOf("page_2.jpg", "page_.jpg", "page_x.jpg", "page_1.jpg").forEach { touch(dir, it) }

        val names = ReceiptExport.pageFiles(dir).map { it.name }

        assertEquals("page_1.jpg", names.first())
        assertEquals("page_2.jpg", names[1])
        // Both unparseable names are present and both are after the real pages.
        assertTrue(names.containsAll(listOf("page_.jpg", "page_x.jpg")))
        assertEquals(4, names.size)
    }

    @Test
    fun `nothing but page jpegs is picked up from the session folder`(@TempDir dir: File) {
        touch(dir, "page_1.jpg")
        // Every one of these is a file that could plausibly appear in a session
        // folder one day. None of them is a page image, so none may be exported
        // by the page pass — the receipt is written separately and by name.
        listOf(
            "receipt.json",
            "page_1.png",
            "page_notes.txt",
            "pages_1.jpg",
            "transcript.txt",
            "segment.wav",
            "session.pcm",
        ).forEach { touch(dir, it) }
        File(dir, "page_dir.jpg").mkdirs()

        assertEquals(listOf("page_1.jpg"), ReceiptExport.pageFiles(dir).map { it.name })
    }

    @Test
    fun `crops come from the crops folder, by name, jpeg only`(@TempDir dir: File) {
        val crops = File(dir, ReceiptExport.CROPS_DIR).apply { mkdirs() }
        listOf("page2_row4.jpg", "page1_row1.jpg", "page1_row2.jpg", "notes.txt").forEach { touch(crops, it) }
        // A page image in the session root is not a crop.
        touch(dir, "page_1.jpg")

        val names = ReceiptExport.cropFiles(dir).map { it.name }

        assertEquals(listOf("page1_row1.jpg", "page1_row2.jpg", "page2_row4.jpg"), names)
    }

    @Test
    fun `a session folder with no images is empty rather than a failure`(@TempDir dir: File) {
        // The ordinary case for a session where nothing was ever scanned. The
        // receipt is still written, so the export still produces a folder.
        assertEquals(emptyList<File>(), ReceiptExport.pageFiles(dir))
        assertEquals(emptyList<File>(), ReceiptExport.cropFiles(dir))
    }

    @Test
    fun `a missing session folder is empty rather than an exception`(@TempDir dir: File) {
        val absent = File(dir, "never_created")

        assertEquals(emptyList<File>(), ReceiptExport.pageFiles(absent))
        assertEquals(emptyList<File>(), ReceiptExport.cropFiles(absent))
    }

    @Test
    fun `the display folder is the path a person can be told`() {
        assertEquals(
            "Download/Vaakku/session_20260913T101500Z/",
            ReceiptExport.displayFolder("session_20260913T101500Z"),
        )
    }

    private fun touch(dir: File, name: String) {
        dir.mkdirs()
        File(dir, name).writeText(name)
    }
}
