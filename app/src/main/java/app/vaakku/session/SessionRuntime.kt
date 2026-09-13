package app.vaakku.session

import app.vaakku.asr.RecognisedSegment
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.Observation
import app.vaakku.domain.reconcile.CardSelection
import app.vaakku.domain.reconcile.Reconciler
import app.vaakku.domain.reconcile.ReconcilerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one live session — build plan §6.7.
 *
 * ### Why this is an object
 *
 * Two independent producers feed one ledger: [SessionService] owns the
 * microphone and runs in its own coroutine, and the scan sheet owns the camera
 * and runs in the activity. They must reconcile against the **same**
 * [Reconciler], because a spoken claim and a written clause only mean anything
 * next to each other. A bound service with a binder would achieve that too, at
 * the cost of connection callbacks, a rebind path after a configuration change,
 * and a window during which the activity has no ledger. The app is one process
 * with one session at a time; a process-scoped holder is the honest shape for
 * that, and this comment is here so the trade is a decision rather than an
 * accident.
 *
 * ### Threading
 *
 * [Reconciler] is a plain state machine with no synchronisation of its own, and
 * it is written to from the service's IO coroutine and the activity's main
 * thread. Every mutation here holds [lock] and republishes inside it, so the
 * published [SessionState] can never be assembled from a half-applied event.
 * Reads go through [state], which is a `StateFlow` and safe from anywhere.
 *
 * ### What never happens here
 *
 * No audio is stored, buffered or written, in any build type (CLAUDE.md #4) —
 * this class never sees samples, only the text a segment produced. Nothing here
 * decides a state either: the five [app.vaakku.domain.model.DeltaState] values
 * come from the reconciler alone, and which one is on screen comes from
 * [Reconciler.selectCard]. This class only routes events and counts.
 */
object SessionRuntime {

    private val lock = Any()

    private var reconciler = Reconciler()

    private val _state = MutableStateFlow(SessionState())

    /** The session, as the UI sees it. */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** The reconciler's append-only event log — the receipt's input (§7.1). */
    fun eventLog(): List<ReconcilerEvent> = synchronized(lock) { reconciler.eventLog() }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Begins a session. Discards the previous reconciler outright rather than
     * sending [ReconcilerEvent.Reset]: `Reset` clears the derived ledger but
     * deliberately keeps the event log, which is right for a re-check within a
     * session and wrong across two sessions — the second session's receipt must
     * not carry the first one's events.
     */
    fun startSession(sessionId: String, nowMs: Long) = synchronized(lock) {
        reconciler = Reconciler()
        publish {
            SessionState(
                phase = SessionPhase.STARTING,
                sessionId = sessionId,
                startedAtMs = nowMs,
            )
        }
    }

    /** The engine is loading. Seconds, for Whisper small — worth saying out loud. */
    fun engineLoading(engineName: String) = synchronized(lock) {
        publish {
            it.copy(
                phase = SessionPhase.STARTING,
                failure = null,
                telemetry = it.telemetry.copy(engineName = engineName),
            )
        }
    }

    /** The microphone is open. Only [SessionService] may call this. */
    fun micOpen() = synchronized(lock) {
        publish { it.copy(phase = SessionPhase.LISTENING, failure = null) }
    }

    /**
     * The session could not listen, and this is why in plain words.
     *
     * **The phase set here is transient and the caller is expected to override
     * it.** `SessionService.listen()` calls this from its `catch` and then
     * `endSession()` from its `finally`, so the state a failed session is
     * actually observed in is ENDED with [SessionState.failure] set — which is
     * what puts the fault at the top of the Receipt screen (decision 66), and
     * what `ReceiptScreen`'s own docs describe. STARTING rather than ENDED here
     * only means this function does not itself declare the session over: the
     * ledger is untouched, and if a future caller wants to report a recoverable
     * fault and keep listening, this does not stand in the way.
     *
     * So do not "tidy" [endSession] into preserving this phase. A failed
     * session that never reaches ENDED never reaches the Receipt screen, and a
     * microphone that died in the fourth minute would cost the buyer the record
     * of the first three.
     */
    fun failed(message: String) = synchronized(lock) {
        publish { it.copy(phase = SessionPhase.STARTING, failure = message) }
    }

    /** The mic closed — either the user ended the session or the stream stopped. */
    fun endSession(nowMs: Long) = synchronized(lock) {
        publish {
            if (it.phase == SessionPhase.IDLE) it
            else it.copy(phase = SessionPhase.ENDED, endedAtMs = nowMs)
        }
    }

    /** Back to no session at all. The ledger and the log go with it. */
    fun clear() = synchronized(lock) {
        reconciler = Reconciler()
        _state.value = SessionState()
    }

    // ------------------------------------------------------------------
    // Observations
    // ------------------------------------------------------------------

    /**
     * One recognised segment: its claims go to the reconciler, its timings to
     * the debug overlay, and its text nowhere else. A segment that produced no
     * claim still counts as a segment and still updates the overlay — "the
     * engine is working and finding nothing" and "the engine is not working" are
     * different problems and must look different.
     */
    fun spoken(recognised: RecognisedSegment) = synchronized(lock) {
        recognised.observations.forEach { reconciler.apply(ReconcilerEvent.SpokenObserved(it)) }
        publish { current ->
            current.copy(
                spokenCount = current.spokenCount + recognised.observations.size,
                telemetry = current.telemetry.copy(
                    lastSegmentText = recognised.segment.text,
                    lastDecodeMs = recognised.decodeMs,
                    lastAudioMs = recognised.audioMs,
                    lastSegmentQuality = recognised.segment.segmentQuality,
                    lastRtf = recognised.rtf,
                    segmentCount = current.telemetry.segmentCount + 1,
                ),
            )
        }
    }

    /**
     * One captured page's clauses. [ocrElapsedMs] is ML Kit's own recognition
     * time, which is the figure the §11.5 scan budget is written against.
     */
    fun documentPage(observations: List<Observation>, ocrElapsedMs: Long) = synchronized(lock) {
        observations.forEach { reconciler.apply(ReconcilerEvent.WrittenObserved(it)) }
        publish { current ->
            current.copy(
                pageCount = current.pageCount + 1,
                documentCount = current.documentCount + observations.size,
                telemetry = current.telemetry.copy(lastOcrMs = ocrElapsedMs),
            )
        }
    }

    /**
     * The user has finished scanning. This is the event the reconciler requires
     * before it will ever report a claim as absent from the document (§5.7 rule
     * 4) — without it, a claim nobody has scanned for yet is PENDING and silent,
     * which is the correct default (CLAUDE.md #2).
     */
    fun doneScanning() = synchronized(lock) {
        reconciler.apply(ReconcilerEvent.DocumentScanCompleted)
        publish { it.copy(scanCompleted = true) }
    }

    // ------------------------------------------------------------------
    // The card queue
    // ------------------------------------------------------------------

    /**
     * "அடுத்து" — the user has read this card; show whatever is next.
     *
     * Both this and [recheck] send [ReconcilerEvent.UserRecheck], because the
     * reconciler has exactly one notion here and it is the right one for both:
     * set the claim aside, and bring it back the moment it is said again (§5.7
     * rule 9). The two methods exist separately because the call sites are
     * different screens, not because the effect differs.
     */
    fun nextCard() = synchronized(lock) {
        val type = _state.value.card?.type ?: return@synchronized
        setAside(type)
    }

    /** "மறுபரிசீலனை" — from the Provenance sheet, for one named claim type. */
    fun recheck(type: ClaimType) = synchronized(lock) { setAside(type) }

    private fun setAside(type: ClaimType) {
        reconciler.apply(ReconcilerEvent.UserRecheck(type))
        publish { it }
    }

    // ------------------------------------------------------------------
    // Publication
    // ------------------------------------------------------------------

    /**
     * Applies [transform], then re-derives the ledger and the selected card from
     * the reconciler, so no caller can forget to.
     *
     * [SessionState.cardEpoch] advances only when the card becomes a *different*
     * card — a new claim type, or the same type in a new state. The Session
     * screen pulses the haptic on that number alone, so a counter tick, a
     * rotation or a recomposition cannot buzz the phone (§6.6 screen 2: one
     * short pulse per new card, and nothing more insistent than that).
     *
     * Must be called with [lock] held.
     */
    private fun publish(transform: (SessionState) -> SessionState) {
        val next = transform(_state.value)
        val card = reconciler.selectCard()
        _state.value = next.copy(
            ledger = reconciler.ledger(),
            card = card,
            cardEpoch = if (sameCard(next.card, card)) next.cardEpoch else next.cardEpoch + 1,
            telemetry = next.telemetry.copy(lastReason = card?.entry?.reason ?: next.telemetry.lastReason),
        )
    }

    /** Two selections are the same card when they say the same thing about the same claim. */
    private fun sameCard(a: CardSelection?, b: CardSelection?): Boolean = when {
        a == null && b == null -> true
        a == null || b == null -> false
        else -> a.type == b.type && a.entry.state == b.entry.state
    }
}
