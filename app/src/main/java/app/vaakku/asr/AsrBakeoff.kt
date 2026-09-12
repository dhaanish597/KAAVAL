package app.vaakku.asr

import android.content.Context
import android.content.res.AssetManager
import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.fixtures.Labels
import app.vaakku.domain.fixtures.SlotChecker
import app.vaakku.domain.lexicon.LexiconLoader
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.Observation
import app.vaakku.domain.normalize.Normalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.io.File

/** One slot from `labels.json`, with what this engine actually produced for it. */
data class SlotOutcome(
    val type: ClaimType,
    val expected: String,
    val actual: String,
    val correct: Boolean,
)

/** One engine's result for one clip. */
data class BakeoffRow(
    val engine: AsrEngineId,
    val clip: String,
    val transcript: String,
    val segments: Int,
    val audioMs: Long,
    val decodeMs: Long,
    val slots: List<SlotOutcome>,
    val error: String? = null,
) {
    val rtf: Double get() = if (audioMs <= 0L) 0.0 else decodeMs.toDouble() / audioMs.toDouble()
    val slotsCorrect: Int get() = slots.count { it.correct }
    val slotsExpected: Int get() = slots.size
}

/** Running totals for one engine, which is what the §13 P2 rule is applied to. */
data class EngineTotals(
    val engine: AsrEngineId,
    val rows: List<BakeoffRow>,
) {
    val slotsCorrect: Int get() = rows.sumOf { it.slotsCorrect }
    val slotsExpected: Int get() = rows.sumOf { it.slotsExpected }
    val slotAccuracy: Double get() = if (slotsExpected == 0) 0.0 else slotsCorrect.toDouble() / slotsExpected

    /**
     * Aggregate RTF: total decode time ÷ total audio.
     *
     * Aggregate, not mean-of-per-clip: a mean gives a two-second clip the same
     * weight as a twenty-second one, and §11.5's budget is about whether the
     * phone keeps up with a pitch, which is a total.
     */
    val aggregateRtf: Double
        get() {
            val audio = rows.sumOf { it.audioMs }
            return if (audio <= 0L) 0.0 else rows.sumOf { it.decodeMs }.toDouble() / audio
        }

    val worstRtf: Double get() = rows.maxOfOrNull { it.rtf } ?: 0.0
    val errors: List<String> get() = rows.mapNotNull { it.error }
}

/** Progress for the screen while a run is in flight. */
sealed interface BakeoffEvent {
    data class Started(val engine: AsrEngineId, val clips: Int) : BakeoffEvent
    data class Loaded(val engine: AsrEngineId, val loadMs: Long) : BakeoffEvent
    data class Row(val row: BakeoffRow) : BakeoffEvent
    data class EngineDone(val totals: EngineTotals) : BakeoffEvent
    data class EngineFailed(val engine: AsrEngineId, val reason: String) : BakeoffEvent
    data class Finished(val totals: List<EngineTotals>) : BakeoffEvent
}

/**
 * Every installed engine × every clip in `assets/testaudio/`, scored against
 * `labels.json` — build plan §11.3 item 2, and the second of the three pieces of
 * evidence gate G2 requires.
 *
 * ### Why this must exist on the phone
 * `evidence/asr_prescreen/` already has slot accuracy from the laptop, and the
 * transcripts will be near-identical. The number that cannot be borrowed is RTF:
 * the §13 P2 rule says "highest slot accuracy whose **phone** RTF ≤ 0.5", and a
 * laptop's RTF says nothing about a Snapdragon running on battery. So this runs
 * the same clips through the same code the live mic uses and measures the clock.
 *
 * ### Scoring is not reimplemented here
 * [SlotChecker] and [Labels] live in `:domain`, and the `evalTranscripts` Gradle
 * task uses the same two classes. A second scoring implementation on the app side
 * would be a second thing that can quietly disagree with the evidence file it is
 * meant to corroborate.
 *
 * Engine 5 takes no part: it owns the microphone and cannot be handed a WAV. See
 * [AndroidOnDeviceRecogniser].
 */
class AsrBakeoff(
    private val context: Context,
    private val assets: AssetManager,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Runs the whole matrix, loading one engine at a time and unloading it before
     * the next — §6.3's "never keep two big models loaded". With four engines at
     * 100–365 MB each, doing otherwise is how the §11.5 memory budget breaks.
     *
     * An engine that fails to load becomes an [BakeoffEvent.EngineFailed] and the
     * run continues. A half-finished `push_models.sh` should cost one engine's
     * column, not the whole measurement.
     */
    fun run(
        engines: List<AsrEngineId> = AsrEngineId.entries.filter { it.decodesSamples },
        numThreads: Int = VadSegmenter.DEFAULT_NUM_THREADS,
    ): Flow<BakeoffEvent> = flow {
        val clips = WavAssetAudioSource.listClips(assets)
        check(clips.isNotEmpty()) {
            "No WAVs in assets/${WavAssetAudioSource.ASSET_DIR}/. The debug build copies them " +
                "from testdata/testaudio/ — check the syncBakeoffAudio Gradle task ran."
        }

        val labels = loadLabels()
        val lexicon = LexiconLoader.loadDefault()
        val extractor = SpokenExtractor(lexicon, Normalizer(lexicon))

        val silero = ModelPaths.silero(context)
        check(silero != null && silero.isFile) {
            "${ModelPaths.SILERO_VAD_FILE} is not on the phone. Run scripts/push_models.sh."
        }

        val allTotals = mutableListOf<EngineTotals>()

        for (engineId in engines) {
            emit(BakeoffEvent.Started(engineId, clips.size))

            val loadStart = System.nanoTime()
            val engine = runCatching { AsrEngineHolder.create(context, engineId, numThreads) }
                .getOrElse { t ->
                    emit(BakeoffEvent.EngineFailed(engineId, "${t.javaClass.simpleName}: ${t.message}"))
                    null
                } ?: continue
            emit(BakeoffEvent.Loaded(engineId, (System.nanoTime() - loadStart) / 1_000_000L))

            val rows = mutableListOf<BakeoffRow>()
            try {
                for (clipPath in clips) {
                    val row = runClip(engine, extractor, silero, numThreads, clipPath, labels)
                    rows += row
                    emit(BakeoffEvent.Row(row))
                }
            } finally {
                engine.close()
            }

            val totals = EngineTotals(engineId, rows)
            allTotals += totals
            emit(BakeoffEvent.EngineDone(totals))
        }

        emit(BakeoffEvent.Finished(allTotals))
    }

    /**
     * One clip through the full pipeline.
     *
     * A fresh [VadSegmenter] per clip: the VAD is a recurrent model with a
     * persistent buffer, and carrying state from one clip into the next would
     * make a row depend on the row before it.
     */
    private suspend fun runClip(
        engine: AsrEngine,
        extractor: SpokenExtractor,
        silero: File,
        numThreads: Int,
        clipPath: String,
        labels: Map<String, Map<String, String>>,
    ): BakeoffRow {
        val clip = clipPath.substringAfterLast('/')
        val texts = mutableListOf<String>()
        val observations = mutableListOf<Observation>()
        var decodeMs = 0L
        var audioMs = 0L
        var segments = 0

        val error = runCatching {
            VadSegmenter(silero, numThreads).use { segmenter ->
                val pipeline = AsrPipeline(engine, segmenter, extractor)
                pipeline.stream(WavAssetAudioSource(assets, clipPath)).collect { recognised ->
                    segments++
                    if (recognised.segment.text.isNotBlank()) texts += recognised.segment.text
                    observations += recognised.observations
                    decodeMs += recognised.decodeMs
                    audioMs += recognised.audioMs
                }
            }
        }.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }

        val expected = labels[clip].orEmpty()
        val slots = expected.mapNotNull { (typeName, expectedValue) ->
            val type = ClaimType.entries.firstOrNull { it.name == typeName } ?: return@mapNotNull null
            SlotOutcome(
                type = type,
                expected = expectedValue,
                actual = SlotChecker.describe(type, observations),
                correct = SlotChecker.matches(type, expectedValue, observations),
            )
        }

        return BakeoffRow(
            engine = engine.id,
            clip = clip,
            transcript = texts.joinToString(" "),
            segments = segments,
            audioMs = audioMs,
            decodeMs = decodeMs,
            slots = slots,
            error = error,
        )
    }

    /** `labels.json` from assets, as clip name → (claim type name → expected). */
    private fun loadLabels(): Map<String, Map<String, String>> {
        val path = "${WavAssetAudioSource.ASSET_DIR}/labels.json"
        val text = runCatching { assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) } }
            .getOrElse { t -> error("Could not read assets/$path: ${t.javaClass.simpleName}: ${t.message}") }
        return json.decodeFromString(Labels.serializer(), text)
            .files
            .associate { it.file to it.expectedClaims }
    }

    companion object {

        /**
         * CSV for `Download/Vaakku/evidence/`.
         *
         * One row per engine × clip, then one summary row per engine. Summary
         * rows are in the same file rather than a second one because the human
         * moves these over Office Kit by hand, and two files is one more thing to
         * lose between the phone and STATUS.md.
         */
        fun toCsv(totals: List<EngineTotals>, deviceSummary: String, numThreads: Int): String = buildString {
            appendLine("# VAAKKU ASR bake-off (build plan §11.3 item 2, gate G2)")
            appendLine("# device: $deviceSummary")
            appendLine("# captured_at: ${Iso8601.now()}")
            appendLine("# num_threads: $numThreads")
            appendLine("# rtf = decode_ms / audio_ms; budget is <= 0.50 (§11.5)")
            appendLine("# engine 5 (ANDROID_ON_DEVICE) is absent: it owns the mic and cannot be fed a WAV.")
            appendLine(
                listOf(
                    "kind", "engine", "clip", "segments", "audio_ms", "decode_ms", "rtf",
                    "slots_correct", "slots_expected", "slot_detail", "transcript", "error",
                ).joinToString(","),
            )

            totals.forEach { t ->
                t.rows.forEach { r ->
                    appendLine(
                        listOf(
                            "row",
                            r.engine.name,
                            r.clip,
                            r.segments.toString(),
                            r.audioMs.toString(),
                            r.decodeMs.toString(),
                            "%.3f".format(r.rtf),
                            r.slotsCorrect.toString(),
                            r.slotsExpected.toString(),
                            r.slots.joinToString("; ") {
                                "${it.type.name} expected=${it.expected} actual=${it.actual} ${if (it.correct) "ok" else "MISS"}"
                            },
                            r.transcript,
                            r.error.orEmpty(),
                        ).joinToString(",") { csv(it) },
                    )
                }
            }

            totals.forEach { t ->
                appendLine(
                    listOf(
                        "total",
                        t.engine.name,
                        "ALL",
                        t.rows.sumOf { it.segments }.toString(),
                        t.rows.sumOf { it.audioMs }.toString(),
                        t.rows.sumOf { it.decodeMs }.toString(),
                        "%.3f".format(t.aggregateRtf),
                        t.slotsCorrect.toString(),
                        t.slotsExpected.toString(),
                        "slot_accuracy=%.3f; worst_rtf=%.3f".format(t.slotAccuracy, t.worstRtf),
                        "",
                        t.errors.joinToString("; "),
                    ).joinToString(",") { csv(it) },
                )
            }
        }

        /**
         * RFC 4180 quoting.
         *
         * Not optional here: the transcript column is Tamil text that routinely
         * contains commas, and a raw join would shift every column after it — the
         * kind of corruption that is invisible until someone reads the wrong
         * number off the wrong column and records it as a decision.
         */
        private fun csv(field: String): String {
            val flat = field.replace('\n', ' ').replace('\r', ' ')
            return if (flat.contains(',') || flat.contains('"')) {
                "\"" + flat.replace("\"", "\"\"") + "\""
            } else {
                flat
            }
        }
    }
}
