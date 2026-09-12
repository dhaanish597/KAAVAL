package app.vaakku.receipt

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Copies a finished session out to where the buyer can reach it — build plan
 * §7.3.
 *
 * ### The layout, and why the names are already right
 *
 * §7.3 fixes `Download/Vaakku/<sessionId>/receipt.json`, the crop JPEGs under
 * `crops`, the page JPEGs named `page_<n>.jpg`, and a
 * `Download/Vaakku/<sessionId>.zip` beside the folder.
 * [app.vaakku.session.SessionEvidence] already writes those names into app
 * storage, so this is a copy rather than a rename — and `tools/packet-cli`
 * reads either the folder or the zip, because at an event somebody will hand
 * over whichever is easier to attach.
 *
 * ### No audio, ever
 *
 * CLAUDE.md #4 and §7.3's own "**No audio, ever**". This class does not copy a
 * folder wholesale; it copies [RECEIPT_NAME], anything matching [PAGE_PREFIX],
 * and the contents of `crops/` — an allowlist, checked per file. A recursive
 * copy would be shorter and would silently start exporting whatever some future
 * change happens to drop in a session directory. There has never been an audio
 * file in there and there is no code that could write one; this is the second
 * lock rather than the first.
 *
 * ### What the page images are, and are not
 *
 * §7.3 calls the exported pages "masked". **They are not masked yet.**
 * `PrivacyMask.applyOrPassThrough` returns its input unchanged until P4 lands,
 * so a `page_*.jpg` is the photograph as taken, which can include an agent's
 * hands or face at the edge of frame. Nothing here or on the export screen
 * calls them masked (CLAUDE.md #8), and the reason they are still exported is
 * that they are the buyer's own photographs of their own document, going to the
 * buyer's own Downloads folder — withholding them would remove evidence from
 * the person it belongs to, not protect anyone. The packet itself never renders
 * a page image; it shows the row crops. Recorded as an open issue against P4.
 */
object ReceiptExport {

    const val RECEIPT_NAME = "receipt.json"
    const val PAGE_PREFIX = "page_"
    const val CROPS_DIR = "crops"

    private const val MIME_JSON = "application/json"
    private const val MIME_JPEG = "image/jpeg"
    private const val MIME_ZIP = "application/zip"

    /**
     * `Download/Vaakku` — the MediaStore `RELATIVE_PATH` everything is written
     * under.
     *
     * A `get()` rather than a `val`: as a property initialiser this would read
     * [Environment] while the object is being constructed, which makes the whole
     * class unloadable in a plain JVM unit test. The file-selection logic below
     * is worth testing without a phone, and it should not have to carry a
     * platform dependency it does not use. Recomputing a string concatenation is
     * free at the handful of call sites this has.
     */
    private val root: String get() = "${Environment.DIRECTORY_DOWNLOADS}/Vaakku"

    /** Where an export of [sessionId] lands, as a path a person can be told. */
    fun displayFolder(sessionId: String): String = "Download/Vaakku/$sessionId/"

    /**
     * What the export achieved, in terms the Receipt screen can show.
     *
     * [folder] and [zip] are display paths, not Uris — a `content://` id means
     * nothing to somebody who has to find the file in Files afterwards.
     *
     * [failures] is one line per file that did not make it. It is a list rather
     * than a boolean because a receipt that exported without three of its crops
     * is still worth having, and the screen should be able to say exactly what
     * is missing instead of failing the whole export or claiming a clean one.
     */
    data class Result(
        val folder: String,
        val zip: String?,
        val fileCount: Int,
        val failures: List<String>,
    ) {
        val receiptWritten: Boolean get() = fileCount > 0
    }

    /**
     * Writes [receiptJson] and the session's images to `Download/Vaakku/<id>/`,
     * then the same set as `Download/Vaakku/<id>.zip`.
     *
     * [sessionDir] is `SessionEvidence.sessionDir` — the app-private folder the
     * scan wrote into. Its contents are read, never moved: the app keeps its own
     * copy, so a buyer who deletes the export still has the session.
     *
     * The receipt is written **first**. If the process is killed half-way
     * through copying twenty crops, what exists on disk is a receipt and some of
     * its images, which the packet CLI renders with "the image … is not in this
     * folder" beside the affected rows. The other order would leave images with
     * no receipt, which is not a record of anything.
     */
    suspend fun export(
        context: Context,
        sessionId: String,
        receiptJson: String,
        sessionDir: File,
    ): Result = withContext(Dispatchers.IO) {
        val relative = "$root/$sessionId"
        val failures = mutableListOf<String>()
        var count = 0

        // Collected for the zip as we go, so the zip is built from the same
        // bytes that were written out rather than by reading the export back.
        val zipEntries = LinkedHashMap<String, ByteArray>()

        val receiptBytes = receiptJson.toByteArray(Charsets.UTF_8)
        if (writeFile(context, relative, RECEIPT_NAME, MIME_JSON, receiptBytes, failures)) {
            zipEntries[RECEIPT_NAME] = receiptBytes
            count++
        }

        for (page in pageFiles(sessionDir)) {
            val bytes = readOrNull(page, failures) ?: continue
            if (writeFile(context, relative, page.name, MIME_JPEG, bytes, failures)) {
                zipEntries[page.name] = bytes
                count++
            }
        }

        for (crop in cropFiles(sessionDir)) {
            val bytes = readOrNull(crop, failures) ?: continue
            // MediaStore takes the subdirectory in RELATIVE_PATH, not in the
            // display name: a DISPLAY_NAME containing a slash is rejected.
            if (writeFile(context, "$relative/$CROPS_DIR", crop.name, MIME_JPEG, bytes, failures)) {
                zipEntries["$CROPS_DIR/${crop.name}"] = bytes
                count++
            }
        }

        val zip = if (zipEntries.isEmpty()) {
            null
        } else {
            writeZip(context, sessionId, zipEntries, failures)
        }

        Result(
            folder = displayFolder(sessionId),
            zip = zip,
            fileCount = count,
            failures = failures,
        )
    }

    /**
     * `page_*.jpg` in the session folder, in page order.
     *
     * `internal` so `ReceiptExportTest` can drive it against a real temp
     * directory. The interesting behaviour is the ordering and what is left out,
     * and neither needs a phone to check.
     */
    internal fun pageFiles(sessionDir: File): List<File> =
        sessionDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(PAGE_PREFIX) && it.name.endsWith(".jpg") }
            ?.sortedBy { pageNumber(it.name) }
            ?: emptyList()

    /**
     * The number in `page_12.jpg`, for ordering.
     *
     * Sorting by name would put `page_10` before `page_2`, which is only a
     * cosmetic wrong in a folder listing but would also reorder the zip. The
     * fallback keeps an unparseable name at the end rather than throwing.
     */
    internal fun pageNumber(name: String): Int =
        name.removePrefix(PAGE_PREFIX).removeSuffix(".jpg").toIntOrNull() ?: Int.MAX_VALUE

    /** Everything in the `crops` folder, by name — `page2_row4.jpg` sorts sensibly as text. */
    internal fun cropFiles(sessionDir: File): List<File> =
        File(sessionDir, CROPS_DIR).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun readOrNull(file: File, failures: MutableList<String>): ByteArray? = try {
        file.readBytes()
    } catch (error: Exception) {
        failures += "${file.name}: could not be read (${error.javaClass.simpleName})"
        null
    }

    /**
     * One file into MediaStore Downloads. True when the bytes landed.
     *
     * API 29+ needs no storage permission for this, which is why §7.3 specifies
     * it: the app has no storage permission at all and adding one to save a JSON
     * file would be an absurd permission footprint for an offline app.
     *
     * Any existing row at the same path is deleted first. MediaStore's default
     * on a name collision is to append " (1)" rather than overwrite, and a
     * `receipt (1).json` next to a stale `receipt.json` is a folder where the
     * packet CLI would read the wrong one. Exporting the same session twice
     * should replace it.
     */
    private fun writeFile(
        context: Context,
        relativePath: String,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        failures: MutableList<String>,
    ): Boolean = try {
        val resolver = context.contentResolver
        deleteExisting(context, relativePath, displayName)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri: Uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore returned no Uri")

        resolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("no output stream")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (error: Exception) {
        failures += "$displayName: ${error.javaClass.simpleName}: ${error.message}"
        false
    }

    /**
     * Removes a previous export of the same name, so a re-export replaces
     * rather than accumulating " (1)" copies. Failure here is not reported: if
     * the row cannot be deleted the insert still succeeds under a suffixed
     * name, which is untidy but not lost data.
     */
    private fun deleteExisting(context: Context, relativePath: String, displayName: String) {
        try {
            context.contentResolver.delete(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?",
                // MediaStore stores RELATIVE_PATH with a trailing slash.
                arrayOf("$relativePath/", displayName),
            )
        } catch (error: Exception) {
            // Ignored deliberately — see the KDoc.
        }
    }

    /**
     * The same files again as one zip beside the folder (§7.3).
     *
     * Entries are prefixed with `<sessionId>/` so unpacking creates a folder
     * instead of scattering `receipt.json` and a `crops/` directory into
     * whatever directory the human happened to be in. `tools/packet-cli` reads
     * both shapes — `findReceipt` prefers the shallowest `receipt.json` — so the
     * prefix costs nothing on the reading side.
     *
     * Built in memory: a session is a JSON file and a few hundred kilobytes of
     * JPEG, and a temp file would be a second copy of the buyer's document on
     * disk for no gain.
     */
    private fun writeZip(
        context: Context,
        sessionId: String,
        entries: Map<String, ByteArray>,
        failures: MutableList<String>,
    ): String? {
        val bytes = try {
            val buffer = ByteArrayOutputStream()
            ZipOutputStream(buffer).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry("$sessionId/$name"))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        } catch (error: Exception) {
            failures += "$sessionId.zip: ${error.javaClass.simpleName}: ${error.message}"
            return null
        }

        val ok = writeFile(context, root, "$sessionId.zip", MIME_ZIP, bytes, failures)
        return if (ok) "Download/Vaakku/$sessionId.zip" else null
    }
}
