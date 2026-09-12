package app.vaakku.session

import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.model.ReasonCode
import app.vaakku.domain.reconcile.CardSelection

/**
 * Where a session is in its life. The UI shows a different bottom third for
 * each, and nothing else in the app is allowed to infer "is the mic open" from
 * anything but this.
 */
enum class SessionPhase {
    /** No session. The Setup screen is what the user sees. */
    IDLE,

    /** The service is up and the engine is loading — seconds, for the big models. */
    STARTING,

    /** The mic is open and segments are arriving. */
    LISTENING,

    /**
     * The session ran and has been ended by the user. The ledger stays readable
     * (Details, receipt) but nothing is being listened to.
     */
    ENDED,
}

/**
 * What the session subsystem measured about itself — build plan §6.6 screen 6,
 * the debug overlay.
 *
 * Kept apart from the rest of [SessionState] because none of it may ever reach
 * a buyer's screen: these are engineering numbers, and [ReasonCode] in
 * particular is internal by construction (`Types.kt`: "must never describe a
 * person"). The overlay that reads this is behind `BuildConfig.DEBUG`.
 */
data class SessionTelemetry(
    val engineName: String = "",
    val lastSegmentText: String = "",
    val lastDecodeMs: Long = 0,
    val lastAudioMs: Long = 0,
    val lastSegmentQuality: Double = 0.0,
    val lastRtf: Double = 0.0,
    val segmentCount: Int = 0,
    val lastOcrMs: Long = 0,
    val lastReason: ReasonCode? = null,
)

/**
 * Everything one session is, as one immutable value — build plan §6.7: "the UI
 * observes a `StateFlow<SessionState>`".
 *
 * ### Why the whole ledger travels in the state
 *
 * The Session screen shows one card, the Details screen shows six rows, and the
 * Provenance sheet shows one entry in full. All three are projections of the
 * same reconciler output, and recomputing them separately is how two screens end
 * up disagreeing about what the document said. One value, three readings.
 *
 * ### What is deliberately absent
 *
 * No audio, no transcript history, no field that grades anyone. [card] is the
 * reconciler's own [CardSelection] — the only thing that decides which fact is
 * on screen is `Reconciler.selectCard()`, never the UI.
 */
data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val sessionId: String = "",
    val startedAtMs: Long = 0,
    val endedAtMs: Long = 0,

    /** Spoken observations recorded so far — the "பேச்சு N" counter. */
    val spokenCount: Int = 0,

    /** Written observations read from the document — the "ஆவணம் M" counter. */
    val documentCount: Int = 0,

    /** Pages captured in this session, for the scan sheet and the receipt. */
    val pageCount: Int = 0,

    /** True once `DocumentScanCompleted` has been sent — see [SessionRuntime.doneScanning]. */
    val scanCompleted: Boolean = false,

    val ledger: Map<ClaimType, LedgerEntry> = emptyMap(),

    /** The one card to show, or null when there is nothing to say. */
    val card: CardSelection? = null,

    /**
     * Increments whenever [card] becomes a *different* card. The Session screen
     * pulses the haptic on a change of this number rather than on any
     * recomposition, so a rotation or a counter tick cannot buzz the phone.
     */
    val cardEpoch: Long = 0,

    /**
     * Why the session could not listen, in plain words, or null. A missing model
     * file and a refused microphone are both ordinary outcomes at an event, and
     * both have to be readable on the phone without a laptop attached.
     */
    val failure: String? = null,

    val telemetry: SessionTelemetry = SessionTelemetry(),
) {
    /** True while the microphone is actually open. */
    val listening: Boolean get() = phase == SessionPhase.LISTENING

    /** Wall-clock length of the session so far, for the receipt screen. */
    fun durationMs(nowMs: Long): Long = when {
        startedAtMs == 0L -> 0L
        endedAtMs != 0L -> endedAtMs - startedAtMs
        else -> nowMs - startedAtMs
    }
}
