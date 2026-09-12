package app.vaakku.asr

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.Closeable
import java.io.File

/**
 * One stretch of speech the VAD decided was worth recognising.
 *
 * [startSample] is in samples from the start of the stream, because that is what
 * sherpa's `SpeechSegment.start` carries; [startMs] converts it. Getting that
 * wrong would put every provenance timestamp on every card out by a factor of
 * 16000, which is the kind of error that looks like a UI bug for an hour.
 */
data class SpeechChunk(
    val startSample: Int,
    val samples: FloatArray,
    val meanSpeechProb: Double,
) {
    val startMs: Long get() = startSample * 1000L / ASR_SAMPLE_RATE_HZ
    val durationMs: Long get() = samples.size * 1000L / ASR_SAMPLE_RATE_HZ
    val endMs: Long get() = startMs + durationMs
    val quality: Double get() = SegmentQuality.of(meanSpeechProb, samples)

    // FloatArray in a data class: equals/hashCode would compare by identity and
    // silently disagree with toString. Nothing compares these, so the generated
    // versions are overridden to be honest rather than left as a trap.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Silero VAD segmentation — build plan §6.3: speech segments of min 0.25 s and
 * max ~8 s, fed to an offline recogniser one segment at a time ("simulated
 * streaming").
 *
 * ### Why there are two VAD instances
 * `Vad.acceptWaveform()` drives the segmenter's internal state machine, and
 * `Vad.compute()` runs the same recurrent model to get a raw speech probability.
 * Calling both on one instance would interleave two consumers of one hidden
 * state and corrupt the segmentation in a way that is very hard to see. So the
 * segmenting VAD is never asked for probabilities; a second instance is reset
 * and re-run over each finished segment purely as a meter. Silero is 640 KB, so
 * the second copy costs nothing worth optimising.
 *
 * ### Memory
 * The native detector holds a bounded buffer (60 s by default = ~3.8 MB of
 * float). Nothing here accumulates: each segment is handed to the caller and
 * dropped. **No audio is written to disk, in any build type** (CLAUDE.md #4).
 */
class VadSegmenter(
    sileroModel: File,
    numThreads: Int = DEFAULT_NUM_THREADS,
) : Closeable {

    private val config = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = sileroModel.absolutePath,
            threshold = THRESHOLD,
            minSilenceDuration = MIN_SILENCE_SECONDS,
            minSpeechDuration = MIN_SPEECH_SECONDS,
            windowSize = WINDOW_SIZE,
            maxSpeechDuration = MAX_SPEECH_SECONDS,
        ),
        sampleRate = ASR_SAMPLE_RATE_HZ,
        numThreads = numThreads,
        provider = "cpu",
    )

    // assetManager = null: models are loaded from absolute paths under
    // getExternalFilesDir("models"), not from the APK (§6.3).
    private val segmenter = Vad(assetManager = null, config = config)
    private val meter = Vad(assetManager = null, config = config)

    private var closed = false

    /**
     * Feeds [chunk] (16 kHz float, -1..1) and returns whatever segments completed.
     *
     * Usually empty — a segment only appears once the VAD has seen enough
     * trailing silence to believe the sentence ended.
     */
    fun accept(chunk: FloatArray, length: Int = chunk.size): List<SpeechChunk> {
        check(!closed) { "VadSegmenter used after close()." }
        val slice = if (length == chunk.size) chunk else chunk.copyOf(length)
        segmenter.acceptWaveform(slice)
        return drain()
    }

    /**
     * Ends the stream and returns any final segment.
     *
     * Required for files and for "stop session": without it, the last sentence
     * before the stream ends is still inside the VAD's buffer and is simply lost
     * — and the last sentence of a sales pitch is exactly the one that closes.
     */
    fun flush(): List<SpeechChunk> {
        check(!closed) { "VadSegmenter used after close()." }
        segmenter.flush()
        return drain()
    }

    /** True while the VAD believes someone is currently speaking (for the UI). */
    fun isSpeaking(): Boolean = !closed && segmenter.isSpeechDetected()

    private fun drain(): List<SpeechChunk> {
        val out = mutableListOf<SpeechChunk>()
        while (!segmenter.empty()) {
            val segment = segmenter.front()
            out += SpeechChunk(
                startSample = segment.start,
                samples = segment.samples,
                meanSpeechProb = measureProbability(segment.samples),
            )
            segmenter.pop()
        }
        return out
    }

    /**
     * Mean per-window speech probability over a finished segment.
     *
     * The meter is reset first so one segment's recurrent state cannot colour the
     * next. A trailing partial window is dropped rather than zero-padded: padding
     * would feed the model silence it never heard and drag the mean down, and the
     * mean is what decides whether this segment may ever produce a DIFFERS card.
     */
    private fun measureProbability(samples: FloatArray): Double {
        meter.reset()
        val probs = ArrayList<Float>(samples.size / WINDOW_SIZE + 1)
        var i = 0
        while (i + WINDOW_SIZE <= samples.size) {
            probs += meter.compute(samples.copyOfRange(i, i + WINDOW_SIZE))
            i += WINDOW_SIZE
        }
        return SegmentQuality.meanProbability(probs)
    }

    override fun close() {
        if (closed) return
        closed = true
        segmenter.release()
        meter.release()
    }

    companion object {
        /** Silero's window, in samples. The model is compiled for this size. */
        const val WINDOW_SIZE = 512

        /** §6.3: "speech segments (min 0.25 s, max ~8 s)". */
        const val MIN_SPEECH_SECONDS = 0.25f
        const val MAX_SPEECH_SECONDS = 8.0f

        const val THRESHOLD = 0.5f

        /**
         * How much silence ends a segment.
         *
         * 0.5 s is Silero's own default, kept because
         * `evidence/asr_prescreen/vad_calibration.md` shows it produces sensible
         * segments on our actual recordings: one segment for most clips, five for
         * the 22 s rehearsal pitch, none longer than 7.3 s. Shortening it would
         * split a sentence at the pause a seller leaves after a number — putting
         * the number in one segment and its anchor word in the next, where
         * `SpokenExtractor` (which only looks within one segment) cannot join them.
         */
        const val MIN_SILENCE_SECONDS = 0.5f

        /** §6.3: "numThreads default 4; configurable." */
        const val DEFAULT_NUM_THREADS = 4
    }
}
