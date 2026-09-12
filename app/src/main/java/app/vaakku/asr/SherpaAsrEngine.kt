package app.vaakku.asr

import android.content.Context
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineDolphinModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * Engines 1–4 of build plan §6.3, all four on sherpa-onnx's `OfflineRecognizer`.
 *
 * ### The config shapes are read, not remembered
 * `OfflineModelConfig` is one flat class with a field per model family, and only
 * the field for the family in use may be populated. The exact field names and
 * constructor shapes came from `javap` over the AAR — see
 * `evidence/sherpa_api_1.13.8.txt`, which exists because §6.3 says in as many
 * words: "Do not guess the sherpa-onnx Kotlin API."
 *
 * The feature settings (16 kHz, 80 mel bins, no dither) are the same ones the
 * laptop pre-screen used, taken from the Python bindings' own factory functions.
 * Keeping them identical is what makes `evidence/asr_prescreen/` a fair
 * prediction of what this class will produce on the phone; a quietly different
 * `featureDim` here would make the two sets of numbers incomparable while both
 * still looked reasonable.
 */
class SherpaAsrEngine(
    context: Context,
    override val id: AsrEngineId,
    numThreads: Int = VadSegmenter.DEFAULT_NUM_THREADS,
) : AsrEngine {

    private val recognizer: OfflineRecognizer
    private var closed = false

    init {
        val dirName = requireNotNull(id.modelDirName) { "${id.displayName} has no model directory." }
        val dir = ModelPaths.modelDir(context, dirName)
        val missing = ModelPaths.missingFiles(dir, id.requiredFiles)
        check(missing.isEmpty()) {
            "${id.displayName}: missing ${missing.joinToString(", ")} under " +
                "${dir?.absolutePath ?: "(no external storage)"}. Run scripts/push_models.sh."
        }
        val modelDir = requireNotNull(dir)

        val modelConfig = when (id) {
            AsrEngineId.SHERPA_WHISPER_TA -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                    decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                    language = "ta",
                    task = "transcribe",
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = numThreads,
                modelType = "whisper",
            )

            AsrEngineId.SHERPA_DOLPHIN_SMALL, AsrEngineId.SHERPA_DOLPHIN_BASE -> OfflineModelConfig(
                dolphin = OfflineDolphinModelConfig(
                    model = File(modelDir, "model.int8.onnx").absolutePath,
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = numThreads,
            )

            // No language parameter exists for this family in sherpa-onnx 1.13.8
            // (STATUS.md decision 27) — the model chooses its own output script,
            // and on Tamil audio it sometimes answers in Gurmukhi or Kannada.
            // That is a property of the engine, recorded rather than papered over.
            AsrEngineId.SHERPA_OMNI_300M -> OfflineModelConfig(
                omnilingual = OfflineOmnilingualAsrCtcModelConfig(
                    model = File(modelDir, "model.int8.onnx").absolutePath,
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = numThreads,
            )

            AsrEngineId.ANDROID_ON_DEVICE ->
                error("${id.displayName} is not a sherpa engine.")
        }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = ASR_SAMPLE_RATE_HZ,
                featureDim = FEATURE_DIM,
                dither = 0.0f,
            ),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )

        // assetManager = null → load from the absolute paths above rather than
        // from the APK. The parameter is nullable in this version; passing an
        // AssetManager would make sherpa look inside assets/ and fail.
        recognizer = OfflineRecognizer(assetManager = null, config = config)
    }

    override fun decode(samples: FloatArray): AsrDecode {
        check(!closed) { "${id.displayName} used after close()." }
        if (samples.isEmpty()) return AsrDecode("", 0L)

        val started = SystemClock.elapsedRealtime()
        val stream = recognizer.createStream()
        val text = try {
            stream.acceptWaveform(samples, ASR_SAMPLE_RATE_HZ)
            recognizer.decode(stream)
            recognizer.getResult(stream).text
        } finally {
            // release(), not left to finalize(): these hold native memory, and a
            // session produces one per segment for as long as someone is talking.
            stream.release()
        }
        return AsrDecode(text.trim(), SystemClock.elapsedRealtime() - started)
    }

    override fun close() {
        if (closed) return
        closed = true
        recognizer.release()
    }

    companion object {
        /**
         * 80 mel bins. Not a free choice: it is what every one of these four
         * models was exported against, and it is what the Python factories
         * (`from_whisper`, `from_dolphin_ctc`, and the default the omnilingual
         * factory leaves in place) used for the pre-screen.
         */
        const val FEATURE_DIM = 80
    }
}
