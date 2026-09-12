package app.vaakku.asr

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.WaveReader

/**
 * Plays a WAV out of `assets/testaudio/` through exactly the pipeline the mic
 * uses — build plan §6.3. Two jobs: the bake-off (every engine over every clip,
 * comparably) and the rehearsal fallback.
 *
 * Decoding uses sherpa-onnx's own [WaveReader] rather than a hand-rolled RIFF
 * parser. It ships in the AAR, it is the same reader the recognisers were tested
 * against upstream, and writing a second WAV parser would be a second thing that
 * can disagree about endianness or chunk order.
 *
 * The whole clip is held in memory (the longest is 22 s ≈ 1.4 MB of float) and
 * handed out in mic-sized chunks. Feeding it in chunks is not a formality: the
 * VAD is a streaming model, and handing it a whole file at once produces
 * different segment boundaries than handing it 512 samples at a time — so a
 * bake-off run that skipped the chunking would not be measuring the live path.
 */
class WavAssetAudioSource(
    private val assets: AssetManager,
    /** Path inside `assets/`, e.g. `testaudio/T01_guarantee_fd.wav`. */
    private val assetPath: String,
) : AudioSource {

    override val label: String = assetPath.substringAfterLast('/').removeSuffix(".wav")

    private var samples: FloatArray = FloatArray(0)
    private var position = 0

    override val durationMs: Long?
        get() = if (samples.isEmpty()) null else samples.size * 1000L / ASR_SAMPLE_RATE_HZ

    /**
     * @throws IllegalStateException if the asset is missing or is not 16 kHz.
     *   The rate check is not pedantry: [WaveReader] returns whatever rate the
     *   file carries, and a 44.1 kHz clip would run through a 16 kHz VAD and a
     *   16 kHz feature extractor at 2.76x slow, producing transcripts that look
     *   like a bad model rather than like a bad configuration.
     */
    override fun start() {
        position = 0
        // runCatching, not `?: error(...)`: [WaveReader.readWave] is declared
        // @NotNull, but it delegates to a JNI method that really can hand back
        // null for a missing or malformed asset. Kotlin's own null check then
        // throws an NPE from inside the library, which says nothing about which
        // asset failed. Catching turns that into a sentence naming the file.
        val wave = runCatching { WaveReader.readWave(assets, assetPath) }
            .getOrElse { t ->
                error("Could not read asset '$assetPath' as a WAV: ${t.javaClass.simpleName}: ${t.message}")
            }
        check(wave.sampleRate == ASR_SAMPLE_RATE_HZ) {
            "'$assetPath' is ${wave.sampleRate} Hz; this pipeline is fixed at $ASR_SAMPLE_RATE_HZ Hz. " +
                "Run tools/asr_prescreen/prepare_testaudio.py over testdata/testaudio/."
        }
        samples = wave.samples
    }

    override fun read(into: FloatArray): Int {
        if (position >= samples.size) return -1
        val n = minOf(into.size, samples.size - position)
        System.arraycopy(samples, position, into, 0, n)
        position += n
        return n
    }

    override fun stop() {
        samples = FloatArray(0)
        position = 0
    }

    companion object {
        const val ASSET_DIR = "testaudio"

        /** Every WAV in `assets/testaudio/`, sorted, as asset paths. */
        fun listClips(assets: AssetManager): List<String> =
            assets.list(ASSET_DIR).orEmpty()
                .filter { it.endsWith(".wav", ignoreCase = true) }
                .sorted()
                .map { "$ASSET_DIR/$it" }
    }
}
