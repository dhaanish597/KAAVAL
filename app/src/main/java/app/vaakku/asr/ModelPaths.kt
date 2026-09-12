package app.vaakku.asr

import android.content.Context
import java.io.File

/**
 * Where the ASR models live on the phone — build plan §6.3:
 * `/sdcard/Android/data/app.vaakku/files/models/<model-dir>/`, reached as
 * `context.getExternalFilesDir("models")`.
 *
 * Not in `assets/`: the four model sets total about 1.1 GB, which is not an APK.
 * The human pushes them with `scripts/push_models.sh`.
 *
 * **Uninstalling the app deletes this whole folder** — which is why CLAUDE.md
 * lists `adb uninstall` under "Never do" and why installs use `-r`.
 */
object ModelPaths {

    const val SILERO_VAD_FILE = "silero_vad.onnx"

    /** The models root, or null when external storage is not mounted. */
    fun root(context: Context): File? = context.getExternalFilesDir("models")

    fun silero(context: Context): File? = root(context)?.let { File(it, SILERO_VAD_FILE) }

    fun modelDir(context: Context, dirName: String): File? = root(context)?.let { File(it, dirName) }

    /**
     * Names the files [requiredFiles] expects but that are not present, relative
     * to [dir].
     *
     * Exists so the UI can say *which* file is missing instead of "engine failed
     * to load". A 250 MB push that stopped halfway is the likeliest way this goes
     * wrong at an event, and "tokens.txt is missing" is a ten-second fix while
     * "failed to load" is a twenty-minute one.
     */
    fun missingFiles(dir: File?, requiredFiles: List<String>): List<String> {
        if (dir == null) return requiredFiles.map { "$it (no external storage)" }
        if (!dir.isDirectory) return listOf("${dir.name}/ (directory not on the phone)")
        return requiredFiles.filter { name ->
            val f = File(dir, name)
            !f.isFile || f.length() == 0L
        }
    }
}
