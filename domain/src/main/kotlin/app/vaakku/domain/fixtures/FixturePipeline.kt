package app.vaakku.domain.fixtures

import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.extract.WrittenExtractor
import app.vaakku.domain.lexicon.Lexicon
import app.vaakku.domain.lexicon.LexiconLoader
import app.vaakku.domain.model.AsrSegment
import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.OcrLine
import app.vaakku.domain.model.Thresholds
import app.vaakku.domain.normalize.Normalizer
import app.vaakku.domain.reconcile.Reconciler
import app.vaakku.domain.reconcile.ReconcilerEvent
import kotlinx.serialization.json.Json

/**
 * Runs one [Fixture] end to end through SpokenExtractor / WrittenExtractor /
 * Reconciler and reduces the result to the four USER-FACING states — build
 * plan §5.10. Shared by [app.vaakku.domain.fixtures] tests and by the
 * `fixtureReport` Gradle task, so both use exactly the same pipeline the
 * app itself would run.
 */
object FixturePipeline {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Fixture = json.decodeFromString(Fixture.serializer(), text)

    /** Build plan §2.3: PENDING and UNCERTAIN are both silent to the user — there is no fifth user-visible state. */
    fun userFacing(state: DeltaState): String = when (state) {
        DeltaState.MATCHES -> "MATCHES"
        DeltaState.NOT_IN_DOCUMENT -> "NOT_IN_DOCUMENT"
        DeltaState.DIFFERS -> "DIFFERS"
        DeltaState.PENDING, DeltaState.UNCERTAIN -> "SILENT"
    }

    /** Runs [fixture] and returns the user-facing state per [ClaimType]. */
    fun run(
        fixture: Fixture,
        lexicon: Lexicon = defaultLexicon,
        thresholds: Thresholds = Thresholds.DEFAULT,
    ): Map<ClaimType, String> {
        val spokenExtractor = SpokenExtractor(lexicon, Normalizer(lexicon))
        val writtenExtractor = WrittenExtractor()
        val reconciler = Reconciler(thresholds)

        fixture.spoken.forEach { seg ->
            val asr = AsrSegment(seg.text, seg.startMs, seg.endMs, seg.engine, seg.segmentQuality)
            spokenExtractor.extract(asr).forEach { obs -> reconciler.apply(ReconcilerEvent.SpokenObserved(obs)) }
        }

        val ocrLines = fixture.written.map { w ->
            OcrLine(w.text, Box(w.box[0], w.box[1], w.box[2], w.box[3]), w.confidence, w.frameId)
        }
        writtenExtractor.extract(ocrLines).forEach { obs -> reconciler.apply(ReconcilerEvent.WrittenObserved(obs)) }

        if (fixture.documentScanCompleted) reconciler.apply(ReconcilerEvent.DocumentScanCompleted)

        return reconciler.ledger().mapValues { userFacing(it.value.state) }
    }

    private val defaultLexicon by lazy { LexiconLoader.loadDefault() }
}
