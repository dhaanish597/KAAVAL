package app.vaakku.asr

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes evidence files where the human can reach them over Office Kit.
 *
 * The build plan fixes this destination (§6.3, §12.1): `Download/Vaakku/evidence/`.
 * On API 29+ nothing needs a storage permission to write there through MediaStore
 * — which matters, because adding WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE
 * to save a text file would be an absurd permission footprint for this app.
 *
 * Falling back to the app-private external dir would produce a file the human
 * cannot move, so a MediaStore failure is surfaced as an error string instead of
 * being silently redirected.
 */
object EvidenceExport {

    const val MIME_TEXT = "text/plain"
    const val MIME_CSV = "text/csv"

    /**
     * Relative path inside Downloads, as MediaStore wants it (no leading slash).
     *
     * Not `const`: [Environment.DIRECTORY_DOWNLOADS] is a platform field, not a
     * compile-time constant.
     */
    private val RELATIVE_DIR = "${Environment.DIRECTORY_DOWNLOADS}/Vaakku/evidence"

    /**
     * Writes [text] as [fileName] under Download/Vaakku/evidence/.
     *
     * [mimeType] defaults to plain text; the bake-off passes `text/csv` so the
     * file opens as a sheet rather than as a wall of commas once the human moves
     * it to the laptop.
     *
     * @return a human-readable line: the path on success, the reason on failure.
     */
    suspend fun writeText(
        context: Context,
        fileName: String,
        text: String,
        mimeType: String = MIME_TEXT,
    ): String =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext writeLegacy(fileName, text)
            }
            runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_DIR)
                    // No IS_PENDING overwrite trick here: MediaStore appends a
                    // " (1)" suffix on a name collision rather than replacing the
                    // file, so callers should include a timestamp in [fileName]
                    // to keep successive runs distinguishable.
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri: Uri = resolver.insert(collection, values)
                    ?: return@runCatching "Insert into MediaStore returned null."

                resolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@runCatching "Could not open an output stream for $uri."

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                "Saved: Download/Vaakku/evidence/$fileName"
            }.getOrElse { t ->
                "Export failed: ${t.javaClass.simpleName}: ${t.message}"
            }
        }

    /**
     * API 31 and 32 both route through the branch above (minSdk is 31), so this is
     * dead code kept only as a guard against minSdk being lowered later. It is
     * written to be honest about that rather than pretending to be reachable.
     */
    @Suppress("unused")
    private fun writeLegacy(fileName: String, text: String): String = runCatching {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vaakku/evidence")
        if (!dir.exists() && !dir.mkdirs()) return@runCatching "Could not create ${dir.absolutePath}"
        val file = File(dir, fileName)
        file.writeText(text, Charsets.UTF_8)
        "Saved: ${file.absolutePath}"
    }.getOrElse { t ->
        "Export failed: ${t.javaClass.simpleName}: ${t.message}"
    }
}
