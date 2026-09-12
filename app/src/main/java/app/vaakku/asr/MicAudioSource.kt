package app.vaakku.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live microphone, 16 kHz mono, PCM16 converted to float — build plan §6.3.
 *
 * `VOICE_RECOGNITION` rather than `MIC`: it is the source Android documents as
 * "tuned for speech recognition", and on most ROMs it means the aggressive
 * voice-call processing (and some of the AGC) stays out of the way. That matters
 * here because `SegmentQuality`'s energy factor reads raw level, and a source
 * with heavy dynamics processing would make that measurement meaningless.
 *
 * **Audio never touches disk** (CLAUDE.md #4). Samples exist in [read]'s caller's
 * buffer and in the VAD's own bounded window, and nowhere else. There is no save
 * path in any build type, and the only bounded buffer this class owns is the
 * PCM16 staging array reused on every read.
 *
 * Not thread-safe: one reader thread, which is how [AsrPipeline] uses it.
 */
class MicAudioSource : AudioSource {

    override val label = "mic"
    override val durationMs: Long? = null

    private var record: AudioRecord? = null
    private var pcm16 = ShortArray(0)
    private val running = AtomicBoolean(false)

    /**
     * @throws IllegalStateException if the mic cannot be opened. Deliberately not
     *   a silent no-op: a pipeline that produced zero segments because the mic
     *   never opened looks identical to a pipeline that heard nothing, and those
     *   need completely different responses from the human holding the phone.
     */
    @SuppressLint("MissingPermission") // Checked by the caller; see SetupScreen's permission row.
    override fun start() {
        if (running.get()) return

        val minBytes = AudioRecord.getMinBufferSize(
            ASR_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBytes > 0) { "AudioRecord.getMinBufferSize returned $minBytes for 16 kHz mono PCM16." }

        // Four times the minimum. The decode of one segment can take hundreds of
        // ms on the big models, and during that time nothing is reading the mic;
        // a buffer at exactly the minimum would overrun and drop the start of the
        // next sentence, which is the half of a claim that carries its anchor word.
        val bufferBytes = minBytes * 4

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            ASR_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord did not initialise (state=${recorder.state}). " +
                "Is RECORD_AUDIO granted, and is another app holding the mic?"
        }

        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "AudioRecord did not start (recordingState=${recorder.recordingState})."
        }

        record = recorder
        running.set(true)
    }

    override fun read(into: FloatArray): Int {
        val recorder = record ?: return -1
        if (!running.get()) return -1

        if (pcm16.size < into.size) pcm16 = ShortArray(into.size)

        val read = recorder.read(pcm16, 0, into.size)
        if (read <= 0) {
            // ERROR_INVALID_OPERATION arrives after stop(); treat every negative
            // return as end-of-stream rather than looping on a dead recorder.
            return if (read < 0) -1 else 0
        }

        for (i in 0 until read) {
            // 32768, not 32767: Short.MIN_VALUE is -32768, and dividing by 32767
            // would let a full-scale negative sample land at -1.00003, outside the
            // -1..1 contract the recognisers and SegmentQuality assume.
            into[i] = pcm16[i] / 32768.0f
        }
        return read
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            record?.release()
            record = null
            return
        }
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
    }
}
