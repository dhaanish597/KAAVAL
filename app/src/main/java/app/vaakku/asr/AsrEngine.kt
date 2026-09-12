package app.vaakku.asr

import android.content.Context
import java.io.Closeable

/**
 * The five engines of build plan §6.3, as data.
 *
 * The model directory names are the names the archives unpack to, and
 * `scripts/push_models.sh` mirrors them verbatim onto the phone — so a rename
 * here is a rename on the phone too, and a typo shows up as "missing files"
 * rather than as a crash.
 */
enum class AsrEngineId(
    val displayName: String,
    /** Folder under `files/models/`, or null for engines that load no model of ours. */
    val modelDirName: String?,
    val requiredFiles: List<String>,
) {
    SHERPA_WHISPER_TA(
        displayName = "Whisper small (Tamil)",
        modelDirName = "whisper-small-ta",
        requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt"),
    ),
    SHERPA_DOLPHIN_SMALL(
        displayName = "Dolphin small CTC",
        modelDirName = "sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02",
        requiredFiles = listOf("model.int8.onnx", "tokens.txt"),
    ),
    SHERPA_DOLPHIN_BASE(
        displayName = "Dolphin base CTC",
        modelDirName = "sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02",
        requiredFiles = listOf("model.int8.onnx", "tokens.txt"),
    ),
    SHERPA_OMNI_300M(
        displayName = "Omnilingual 300M CTC",
        modelDirName = "sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12",
        requiredFiles = listOf("model.int8.onnx", "tokens.txt"),
    ),

    /**
     * `SpeechRecognizer.createOnDeviceSpeechRecognizer()`.
     *
     * It owns the microphone, so it cannot run alongside [MicAudioSource] and it
     * cannot be fed a WAV — which is why it takes no part in the bake-off. It is
     * also **already ruled out for Tamil on this phone**: the Rung-0 probe
     * (STATUS.md M1) found `ta-IN` absent from the on-device recogniser's list of
     * 31 languages. It stays in the list because the plan lists it and because
     * measuring it again is cheaper than arguing about it.
     */
    ANDROID_ON_DEVICE(
        displayName = "Android on-device",
        modelDirName = null,
        requiredFiles = emptyList(),
    ),
    ;

    /** True for the four engines that decode PCM we hand them. */
    val decodesSamples: Boolean get() = this != ANDROID_ON_DEVICE
}

/** What one decode produced, with the timing the RTF column needs. */
data class AsrDecode(
    val text: String,
    val decodeMs: Long,
)

/**
 * Whether an engine can be loaded right now, and if not, exactly what is missing.
 *
 * "Exactly what is missing" is the point. At an event the likely failure is a
 * half-finished `push_models.sh`, and "tokens.txt is missing" is a ten-second fix
 * while "engine failed to load" is a twenty-minute one.
 */
data class EngineAvailability(
    val id: AsrEngineId,
    val ready: Boolean,
    val missing: List<String>,
) {
    val summary: String
        get() = when {
            ready -> "ready"
            missing.isEmpty() -> "not available"
            else -> "missing: " + missing.joinToString(", ")
        }
}

/**
 * One loaded recogniser. Closing it frees the native model.
 *
 * Implementations must hold at most one model each, and callers must hold at
 * most one implementation at a time — see [AsrEngineHolder]. The models are
 * 100–365 MB on disk and larger in memory; two at once is how the §11.5 budget
 * of "app memory < 1.5 GB" gets blown.
 */
interface AsrEngine : Closeable {
    val id: AsrEngineId

    /** Decodes one VAD segment of 16 kHz float samples. */
    fun decode(samples: FloatArray): AsrDecode
}

/**
 * Holds at most one engine, and unloads the previous one before loading the next
 * — build plan §6.3's "never keep two big models loaded".
 *
 * Enforced here rather than left to each caller to remember, because the failure
 * mode is an out-of-memory kill several screens later, with nothing pointing back
 * at the switch that caused it.
 */
class AsrEngineHolder(private val context: Context) : Closeable {

    var current: AsrEngine? = null
        private set

    var numThreads: Int = VadSegmenter.DEFAULT_NUM_THREADS

    /** Loads [id], unloading whatever was loaded first. Returns the new engine. */
    fun switchTo(id: AsrEngineId): AsrEngine {
        current?.let { existing ->
            if (existing.id == id) return existing
        }
        close()
        val engine = create(context, id, numThreads)
        current = engine
        return engine
    }

    override fun close() {
        current?.close()
        current = null
    }

    companion object {
        fun create(context: Context, id: AsrEngineId, numThreads: Int): AsrEngine = when (id) {
            AsrEngineId.ANDROID_ON_DEVICE ->
                error(
                    "${id.displayName} owns the microphone and decodes no samples; " +
                        "it is driven by AndroidOnDeviceRecogniser, not by AsrEngineHolder.",
                )
            else -> SherpaAsrEngine(context, id, numThreads)
        }

        /** Reports readiness without loading anything — cheap enough for a screen. */
        fun availability(context: Context, id: AsrEngineId): EngineAvailability {
            if (id.modelDirName == null) {
                return EngineAvailability(id, ready = false, missing = listOf("driven separately; see the Rung-0 probe"))
            }
            val dir = ModelPaths.modelDir(context, id.modelDirName)
            val missing = ModelPaths.missingFiles(dir, id.requiredFiles)
            val vadMissing = ModelPaths.missingFiles(
                ModelPaths.root(context),
                listOf(ModelPaths.SILERO_VAD_FILE),
            )
            return EngineAvailability(id, ready = missing.isEmpty() && vadMissing.isEmpty(), missing = missing + vadMissing)
        }
    }
}
