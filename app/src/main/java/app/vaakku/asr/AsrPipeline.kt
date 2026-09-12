package app.vaakku.asr

import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.model.AsrSegment
import app.vaakku.domain.model.Observation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * One recognised segment, plus everything measured about it.
 *
 * [rtf] is real-time factor for this segment alone: decode time ÷ audio duration.
 * §11.5 budgets it at ≤ 0.5, and the §13 P2 decision rule uses the **phone**
 * number, not the laptop number in `evidence/asr_prescreen/prescreen.csv` — which
 * is the whole reason this field exists rather than being computed in the UI.
 */
data class RecognisedSegment(
    val segment: AsrSegment,
    val observations: List<Observation>,
    val decodeMs: Long,
    val audioMs: Long,
) {
    val rtf: Double get() = if (audioMs <= 0L) 0.0 else decodeMs.toDouble() / audioMs.toDouble()
}

/**
 * AudioSource → VAD → recogniser → `AsrSegment` → `SpokenExtractor` — build plan
 * §6.3's "simulated streaming".
 *
 * ### Nothing is buffered across segments
 * Each speech chunk is decoded, turned into observations, emitted, and dropped.
 * No transcript history and no audio history is kept here; the ledger downstream
 * is the only thing that accumulates. **Audio never touches disk** (CLAUDE.md #4).
 *
 * ### How to stop it
 * Call [AudioSource.stop] from another thread. [MicAudioSource.read] then returns
 * -1, the loop leaves normally, and [VadSegmenter.flush] emits the sentence still
 * sitting in the VAD's buffer. Cancelling the collecting coroutine instead also
 * works, but it throws away that last segment — and in a sales pitch the last
 * sentence is the one that closes. So: stop the source, don't cancel the flow.
 *
 * Not reusable across engines: the engine is a constructor argument because
 * [AsrEngineHolder] owns switching, and a pipeline that could swap engines
 * mid-stream would produce evidence rows that misattribute the engine that made them.
 */
class AsrPipeline(
    private val engine: AsrEngine,
    private val segmenter: VadSegmenter,
    private val extractor: SpokenExtractor,
) {

    /**
     * Reads [source] to its end (a file) or until it is stopped (a mic), emitting
     * one [RecognisedSegment] per stretch of speech.
     *
     * Runs on [Dispatchers.IO] because every step blocks: `AudioRecord.read`, the
     * VAD, and the recogniser are all synchronous native calls.
     */
    fun stream(source: AudioSource): Flow<RecognisedSegment> = flow {
        source.start()
        try {
            val buffer = FloatArray(CHUNK_SAMPLES)
            while (currentCoroutineContext().isActive) {
                val n = source.read(buffer)
                // 0 is not end-of-stream: AudioRecord returns it when no frames
                // are ready yet. Only a negative return ends the stream.
                if (n < 0) break
                if (n == 0) continue
                for (chunk in segmenter.accept(buffer, n)) emit(recognise(chunk))
            }
            for (chunk in segmenter.flush()) emit(recognise(chunk))
        } finally {
            source.stop()
        }
    }.flowOn(Dispatchers.IO)

    private fun recognise(chunk: SpeechChunk): RecognisedSegment {
        val decode = engine.decode(chunk.samples)
        val segment = AsrSegment(
            text = decode.text,
            startMs = chunk.startMs,
            endMs = chunk.endMs,
            // id.name, not displayName: this string ends up in evidence CSVs and
            // in `Provenance`, where a stable identifier is worth more than a
            // pretty one.
            engine = engine.id.name,
            segmentQuality = chunk.quality,
        )
        // The extractor is pure and cheap next to the recogniser, and it is run
        // here rather than in the UI so that a bake-off row and a live card come
        // from the identical code path.
        val observations = if (segment.text.isBlank()) emptyList() else extractor.extract(segment)
        return RecognisedSegment(
            segment = segment,
            observations = observations,
            decodeMs = decode.decodeMs,
            audioMs = chunk.durationMs,
        )
    }

    companion object {
        /**
         * Samples per read: 4096 = 256 ms at 16 kHz, exactly 8 Silero windows.
         *
         * A whole number of 512-sample windows matters — a ragged chunk size would
         * leave a partial window at every boundary, and the VAD's handling of that
         * is one more thing that would differ between the mic path and the WAV path.
         */
        const val CHUNK_SAMPLES = 4096
    }
}
