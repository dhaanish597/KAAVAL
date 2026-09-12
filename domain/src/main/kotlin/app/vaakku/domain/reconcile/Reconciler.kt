package app.vaakku.domain.reconcile

import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.ReasonCode
import app.vaakku.domain.model.Thresholds
import java.math.BigDecimal

/** Which card the UI shows next — build plan §5.7's "one fact per screen." */
data class CardSelection(val type: ClaimType, val entry: LedgerEntry, val position: Int, val totalPending: Int)

/**
 * The reconciler state machine — build plan §5.7, "the heart." Deterministic:
 * `(ledger, event) -> ledger`, same events in the same order always give the
 * same result. Every event is kept in an append-only log (it feeds the
 * receipt hash chain, §7.4) — [Reset] clears the DERIVED ledger state, never
 * the log itself.
 */
class Reconciler(private val thresholds: Thresholds = Thresholds.DEFAULT) {

    private val log = mutableListOf<ReconcilerEvent>()

    private var documentScanCompleted = false
    private val latestSpoken = mutableMapOf<ClaimType, Observation>()
    private val written = mutableMapOf<ClaimType, MutableList<Observation>>()
    private val mentionCounts = mutableMapOf<ClaimType, MutableMap<ClaimValue, Int>>()
    private val dismissed = mutableMapOf<ClaimType, Boolean>()

    /** Applies [event] and returns the resulting ledger (one entry per [ClaimType]). */
    fun apply(event: ReconcilerEvent): Map<ClaimType, LedgerEntry> {
        log += event
        when (event) {
            is ReconcilerEvent.SpokenObserved -> {
                val obs = event.observation
                latestSpoken[obs.type] = obs
                mentionCounts.getOrPut(obs.type) { mutableMapOf() }.merge(obs.value, 1, Int::plus)
                dismissed[obs.type] = false // a new observation un-dismisses, §5.7 rule 9
            }
            is ReconcilerEvent.WrittenObserved -> {
                val obs = event.observation
                written.getOrPut(obs.type) { mutableListOf() }.add(obs)
                dismissed[obs.type] = false
            }
            ReconcilerEvent.DocumentScanCompleted -> documentScanCompleted = true
            is ReconcilerEvent.UserRecheck -> dismissed[event.type] = true
            ReconcilerEvent.Reset -> {
                documentScanCompleted = false
                latestSpoken.clear()
                written.clear()
                mentionCounts.clear()
                dismissed.clear()
            }
        }
        return ledger()
    }

    /** The current ledger without applying a new event. */
    fun ledger(): Map<ClaimType, LedgerEntry> = ClaimType.entries.associateWith { decide(it) }

    /** Every event applied so far, in order — the append-only log §5.7 requires. */
    fun eventLog(): List<ReconcilerEvent> = log.toList()

    /**
     * The card the UI should show — the most recent non-dismissed DIFFERS,
     * or (if none) the most recent non-dismissed NOT_IN_DOCUMENT. MATCHES,
     * PENDING and UNCERTAIN never take the card. "Most recent" is the
     * spoken observation's own timestamp; DIFFERS is a strict priority tier
     * over NOT_IN_DOCUMENT, not merged into one combined recency order.
     */
    fun selectCard(): CardSelection? {
        val currentLedger = ledger()
        val differs = currentLedger.values.filter { it.state == DeltaState.DIFFERS && !it.dismissed }
        val notInDocument = currentLedger.values.filter { it.state == DeltaState.NOT_IN_DOCUMENT && !it.dismissed }
        val pending = differs.ifEmpty { notInDocument }
        if (pending.isEmpty()) return null
        val ordered = pending.sortedByDescending { it.spoken?.tMs ?: Long.MIN_VALUE }
        val index = ordered.indexOfFirst { it.type == ordered.first().type }
        return CardSelection(ordered.first().type, ordered.first(), index + 1, ordered.size)
    }

    // ------------------------------------------------------------------
    // Decision table — §5.7, applied in order
    // ------------------------------------------------------------------

    private fun decide(type: ClaimType): LedgerEntry {
        val spoken = latestSpoken[type]
        val allWritten = written[type].orEmpty()
        val isDismissed = dismissed[type] == true

        fun result(state: DeltaState, reason: ReasonCode): LedgerEntry =
            LedgerEntry(type, spoken, allWritten, state, reason, mentionCount(type, spoken), isDismissed)

        // 1. No spoken observation -> PENDING.
        if (spoken == null) return result(DeltaState.PENDING, ReasonCode.NO_SPOKEN)

        // 2. Spoken is conditional -> UNCERTAIN.
        if (spoken.conditional) return result(DeltaState.UNCERTAIN, ReasonCode.CONDITIONAL)

        // 3. Low confidence, or negation/normalization ambiguous -> UNCERTAIN.
        if (spoken.ambiguous == ReasonCode.NEGATION_AMBIGUOUS) return result(DeltaState.UNCERTAIN, ReasonCode.NEGATION_AMBIGUOUS)
        if (spoken.ambiguous == ReasonCode.NORMALIZATION_AMBIGUOUS) return result(DeltaState.UNCERTAIN, ReasonCode.NORMALIZATION_AMBIGUOUS)
        if (spoken.confidence < thresholds.spokenMin) return result(DeltaState.UNCERTAIN, ReasonCode.SPOKEN_LOW_CONF)

        // 4. Document scan not completed -> PENDING.
        if (!documentScanCompleted) return result(DeltaState.PENDING, ReasonCode.NO_DOC_YET)

        // 5/6. Written observations, filtered to confident ones.
        val confidentWritten = allWritten.filter { it.confidence >= thresholds.writtenMin }
        if (allWritten.isNotEmpty() && confidentWritten.isEmpty()) {
            return result(DeltaState.UNCERTAIN, ReasonCode.WRITTEN_LOW_CONF)
        }
        if (confidentWritten.isEmpty()) {
            return result(DeltaState.NOT_IN_DOCUMENT, ReasonCode.DOC_SILENT)
        }

        // 7. Compare by type.
        val comparison = compare(type, spoken, confidentWritten)
        if (comparison !is Comparison.CandidateDiffers) {
            val resolved = comparison as Comparison.Resolved
            return result(resolved.state, resolved.reason)
        }

        // 8. A candidate DIFFERS is confirmed only if not hedged, not negation-
        // ambiguous, AND (strong confidence OR corroborated by a repeat mention).
        val mCount = mentionCount(type, spoken)
        val strongEnough = spoken.confidence >= thresholds.spokenStrong || mCount >= 2
        return if (!spoken.hedged && spoken.ambiguous == null && strongEnough) {
            result(DeltaState.DIFFERS, ReasonCode.TOLERANCE_EXCEEDED)
        } else {
            result(DeltaState.UNCERTAIN, ReasonCode.NEEDS_CORROBORATION)
        }
    }

    private fun mentionCount(type: ClaimType, spoken: Observation?): Int {
        if (spoken == null) return 0
        return mentionCounts[type]?.get(spoken.value) ?: 1
    }

    private sealed interface Comparison {
        data class Resolved(val state: DeltaState, val reason: ReasonCode) : Comparison
        data object CandidateDiffers : Comparison
    }

    private fun compare(type: ClaimType, spoken: Observation, confidentWritten: List<Observation>): Comparison = when (type) {
        ClaimType.RETURN_RATE -> compareRate(spoken, confidentWritten)
        ClaimType.GUARANTEE -> compareGuarantee(spoken, confidentWritten)
        ClaimType.LOCK_IN -> compareLockIn(spoken, confidentWritten)
        ClaimType.LIQUIDITY -> compareLiquidity(spoken, confidentWritten)
        ClaimType.BUNDLING -> compareBundling(spoken, confidentWritten)
        ClaimType.CHARGES -> compareCharges(spoken, confidentWritten)
    }

    private fun compareRate(spoken: Observation, confidentWritten: List<Observation>): Comparison {
        val spokenRate = spoken.value as ClaimValue.Rate
        val writtenPercents = confidentWritten.flatMap { (it.value as ClaimValue.Rate).percents }
        val tolerance = BigDecimal.valueOf(thresholds.rateTolPp)
        val allInScenarios = spokenRate.percents.isNotEmpty() && spokenRate.percents.all { sp ->
            writtenPercents.any { wp -> (sp - wp).abs() <= tolerance }
        }
        return when {
            allInScenarios -> Comparison.Resolved(DeltaState.MATCHES, ReasonCode.VALUE_IN_SCENARIOS)
            spoken.hedged -> Comparison.Resolved(DeltaState.UNCERTAIN, ReasonCode.HEDGED)
            else -> Comparison.CandidateDiffers
        }
    }

    private fun compareGuarantee(spoken: Observation, confidentWritten: List<Observation>): Comparison {
        val s = (spoken.value as ClaimValue.Guarantee).guaranteed
        val matches = confidentWritten.any { (it.value as ClaimValue.Guarantee).guaranteed == s }
        return if (matches) Comparison.Resolved(DeltaState.MATCHES, ReasonCode.VALUE_EQUAL) else Comparison.CandidateDiffers
    }

    private fun compareLockIn(spoken: Observation, confidentWritten: List<Observation>): Comparison {
        val s = (spoken.value as ClaimValue.LockIn).months
        val matches = confidentWritten.any { (it.value as ClaimValue.LockIn).months == s }
        return if (matches) Comparison.Resolved(DeltaState.MATCHES, ReasonCode.VALUE_EQUAL) else Comparison.CandidateDiffers
    }

    private fun compareLiquidity(spoken: Observation, confidentWritten: List<Observation>): Comparison {
        val s = spoken.value as ClaimValue.Liquidity
        // §5.7: compare against a written surrenderNilBeforeMonths, OR (if this
        // type has none) a written LOCK_IN's months as a fallback proxy.
        val writtenNilBefore = confidentWritten.mapNotNull { (it.value as ClaimValue.Liquidity).surrenderNilBeforeMonths }
        val n = writtenNilBefore.firstOrNull() ?: written[ClaimType.LOCK_IN].orEmpty()
            .filter { it.confidence >= thresholds.writtenMin }
            .map { (it.value as ClaimValue.LockIn).months }
            .firstOrNull()
            ?: return Comparison.CandidateDiffers

        return when {
            s.withdrawableAfterMonths != null -> {
                if (s.withdrawableAfterMonths < n) Comparison.CandidateDiffers else Comparison.Resolved(DeltaState.MATCHES, ReasonCode.VALUE_EQUAL)
            }
            s.surrenderNilBeforeMonths != null -> {
                if (s.surrenderNilBeforeMonths == n) Comparison.Resolved(DeltaState.MATCHES, ReasonCode.VALUE_EQUAL) else Comparison.CandidateDiffers
            }
            else -> Comparison.CandidateDiffers
        }
    }

    private fun compareBundling(spoken: Observation, confidentWritten: List<Observation>): Comparison {
        val s = (spoken.value as ClaimValue.Bundling).requiredForLoan
        val writtenVoluntary = confidentWritten.any { !(it.value as ClaimValue.Bundling).requiredForLoan }
        return if (s && writtenVoluntary) Comparison.CandidateDiffers else Comparison.Resolved(DeltaState.MATCHES, ReasonCode.VALUE_EQUAL)
    }

    private fun compareCharges(spoken: Observation, confidentWritten: List<Observation>): Comparison {
        val s = (spoken.value as ClaimValue.Charges).anyCharges
        val writtenHasCharges = confidentWritten.any { (it.value as ClaimValue.Charges).anyCharges }
        return if (!s && writtenHasCharges) Comparison.CandidateDiffers else Comparison.Resolved(DeltaState.MATCHES, ReasonCode.VALUE_EQUAL)
    }
}
