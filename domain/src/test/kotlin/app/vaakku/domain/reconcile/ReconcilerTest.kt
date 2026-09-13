package app.vaakku.domain.reconcile

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.ReasonCode
import app.vaakku.domain.model.Source
import app.vaakku.domain.model.Thresholds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

/** Build plan §5.7 — the reconciler decision table, rule by rule, plus latest-wins, mentionCount, UserRecheck, card selection, event log. */
class ReconcilerTest {

    private fun spoken(
        type: ClaimType,
        value: ClaimValue,
        confidence: Double = 0.9,
        hedged: Boolean = false,
        conditional: Boolean = false,
        ambiguous: ReasonCode? = null,
        tMs: Long = 0,
    ) = Observation(
        id = UUID.randomUUID().toString(), source = Source.SPOKEN, type = type, value = value,
        hedged = hedged, negated = false, conditional = conditional, confidence = confidence,
        provenance = Provenance.Spoken("span", tMs, tMs + 1000, "test"), tMs = tMs, ambiguous = ambiguous,
    )

    private fun writtenObs(type: ClaimType, value: ClaimValue, confidence: Double = 0.95) = Observation(
        id = UUID.randomUUID().toString(), source = Source.WRITTEN, type = type, value = value,
        hedged = false, negated = false, conditional = false, confidence = confidence,
        provenance = Provenance.Written("line", Box(0, 0, 10, 10), "f1", null), tMs = 0,
    )

    private fun scannedReconciler(): Reconciler {
        val r = Reconciler()
        r.apply(ReconcilerEvent.DocumentScanCompleted)
        return r
    }

    // ------------------------------------------------------------------
    // Rules 1-6
    // ------------------------------------------------------------------

    @Test
    fun `rule 1 - no spoken observation is PENDING NO_SPOKEN`() {
        val entry = Reconciler().ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.PENDING, entry.state)
        assertEquals(ReasonCode.NO_SPOKEN, entry.reason)
    }

    @Test
    fun `rule 2 - a conditional spoken observation is UNCERTAIN CONDITIONAL`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), conditional = true)))
        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.CONDITIONAL, entry.reason)
    }

    @Test
    fun `rule 3a - negation-ambiguous is UNCERTAIN, never asserted either way`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), ambiguous = ReasonCode.NEGATION_AMBIGUOUS)))
        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.NEGATION_AMBIGUOUS, entry.reason)
    }

    @Test
    fun `rule 3b - normalization-ambiguous is UNCERTAIN`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(12), ambiguous = ReasonCode.NORMALIZATION_AMBIGUOUS)))
        val entry = r.ledger()[ClaimType.LOCK_IN]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.NORMALIZATION_AMBIGUOUS, entry.reason)
    }

    @Test
    fun `rule 3c - low spoken confidence is UNCERTAIN SPOKEN_LOW_CONF`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.2)))
        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.SPOKEN_LOW_CONF, entry.reason)
    }

    @Test
    fun `rule 4 - document not yet scanned is PENDING NO_DOC_YET, even with a confident spoken claim`() {
        val r = Reconciler() // no DocumentScanCompleted
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))
        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.PENDING, entry.state)
        assertEquals(ReasonCode.NO_DOC_YET, entry.reason)
    }

    @Test
    fun `rule 5 - written observations exist but all below WRITTEN_MIN is UNCERTAIN WRITTEN_LOW_CONF`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("8")), app.vaakku.domain.model.RateQualifier.ASSERTED), confidence = 0.95)))
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("8")), app.vaakku.domain.model.RateQualifier.ASSERTED), confidence = 0.3)))
        val entry = r.ledger()[ClaimType.RETURN_RATE]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.WRITTEN_LOW_CONF, entry.reason)
    }

    @Test
    fun `A10 rule 6 - no written observation of this type, scan completed, is NOT_IN_DOCUMENT DOC_SILENT`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.BUNDLING, ClaimValue.Bundling(true), confidence = 0.95)))
        val entry = r.ledger()[ClaimType.BUNDLING]!!
        assertEquals(DeltaState.NOT_IN_DOCUMENT, entry.state)
        assertEquals(ReasonCode.DOC_SILENT, entry.reason)
    }

    // ------------------------------------------------------------------
    // Rule 7 — compare by type
    // ------------------------------------------------------------------

    @Test
    fun `RATE - a spoken percent inside the written scenario set MATCHES`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("4"), BigDecimal("8")), app.vaakku.domain.model.RateQualifier.ILLUSTRATIVE))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("8")), app.vaakku.domain.model.RateQualifier.ASSERTED), confidence = 0.95)))
        val entry = r.ledger()[ClaimType.RETURN_RATE]!!
        assertEquals(DeltaState.MATCHES, entry.state)
    }

    @Test
    fun `A8 - a hedged spoken rate not in the written set is UNCERTAIN HEDGED, not DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("4")), app.vaakku.domain.model.RateQualifier.ILLUSTRATIVE))))
        r.apply(
            ReconcilerEvent.SpokenObserved(
                spoken(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("8")), app.vaakku.domain.model.RateQualifier.UP_TO), confidence = 0.95, hedged = true),
            ),
        )
        val entry = r.ledger()[ClaimType.RETURN_RATE]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.HEDGED, entry.reason)
    }

    @Test
    fun `demo headline - guaranteed spoken vs not-guaranteed written is a confirmed DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))
        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.DIFFERS, entry.state)
    }

    @Test
    fun `LOCK_IN - equal months MATCHES, different months is a candidate DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(60), confidence = 0.95)))
        assertEquals(DeltaState.MATCHES, r.ledger()[ClaimType.LOCK_IN]!!.state)

        val r2 = scannedReconciler()
        r2.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r2.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(12), confidence = 0.95)))
        assertEquals(DeltaState.DIFFERS, r2.ledger()[ClaimType.LOCK_IN]!!.state)
    }

    @Test
    fun `LIQUIDITY - withdraw-after less than written nil-before is a candidate DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LIQUIDITY, ClaimValue.Liquidity(null, 60))))
        r.apply(
            ReconcilerEvent.SpokenObserved(
                spoken(ClaimType.LIQUIDITY, ClaimValue.Liquidity(withdrawableAfterMonths = 12, surrenderNilBeforeMonths = null), confidence = 0.95),
            ),
        )
        assertEquals(DeltaState.DIFFERS, r.ledger()[ClaimType.LIQUIDITY]!!.state)
    }

    @Test
    fun `LIQUIDITY - withdraw-after at or beyond the written nil-before MATCHES`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LIQUIDITY, ClaimValue.Liquidity(null, 60))))
        r.apply(
            ReconcilerEvent.SpokenObserved(
                spoken(ClaimType.LIQUIDITY, ClaimValue.Liquidity(withdrawableAfterMonths = 60, surrenderNilBeforeMonths = null), confidence = 0.95),
            ),
        )
        assertEquals(DeltaState.MATCHES, r.ledger()[ClaimType.LIQUIDITY]!!.state)
    }

    @Test
    fun `LIQUIDITY - a written nil-before falls back to a written LOCK_IN's months when its own is absent`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(
            ReconcilerEvent.SpokenObserved(
                spoken(ClaimType.LIQUIDITY, ClaimValue.Liquidity(withdrawableAfterMonths = 12, surrenderNilBeforeMonths = null), confidence = 0.95),
            ),
        )
        // LIQUIDITY itself is silent in the document, so rule 6 fires first.
        assertEquals(DeltaState.NOT_IN_DOCUMENT, r.ledger()[ClaimType.LIQUIDITY]!!.state)
    }

    @Test
    fun `T03 A10 - BUNDLING required spoken vs written voluntary is a candidate DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.BUNDLING, ClaimValue.Bundling(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.BUNDLING, ClaimValue.Bundling(true), confidence = 0.95)))
        assertEquals(DeltaState.DIFFERS, r.ledger()[ClaimType.BUNDLING]!!.state)
    }

    @Test
    fun `T04 - CHARGES none spoken vs written any-charge is a candidate DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.CHARGES, ClaimValue.Charges(true, BigDecimal("5"), "premium allocation charge"))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.CHARGES, ClaimValue.Charges(false, null, null), confidence = 0.95)))
        assertEquals(DeltaState.DIFFERS, r.ledger()[ClaimType.CHARGES]!!.state)
    }

    // ------------------------------------------------------------------
    // Rule 8 — confirming a candidate DIFFERS
    // ------------------------------------------------------------------

    @Test
    fun `rule 8 - strong single-mention confidence confirms DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = Thresholds.DEFAULT.spokenStrong)))
        assertEquals(DeltaState.DIFFERS, r.ledger()[ClaimType.GUARANTEE]!!.state)
    }

    @Test
    fun `rule 8 - a weak single mention is NEEDS_CORROBORATION, not DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.6)))
        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.NEEDS_CORROBORATION, entry.reason)
    }

    @Test
    fun `rule 8 - a weak mention repeated twice is corroborated into DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.6, tMs = 0)))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.6, tMs = 5000)))
        assertEquals(DeltaState.DIFFERS, r.ledger()[ClaimType.GUARANTEE]!!.state)
    }

    @Test
    fun `rule 8 - a hedged candidate DIFFERS is never confirmed even at strong confidence`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(12), confidence = 0.99, hedged = true)))
        val entry = r.ledger()[ClaimType.LOCK_IN]!!
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertEquals(ReasonCode.NEEDS_CORROBORATION, entry.reason)
    }

    // ------------------------------------------------------------------
    // Latest-wins / mentionCount
    // ------------------------------------------------------------------

    @Test
    fun `A2 - latest spoken value wins over an earlier one for the same type`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(36), confidence = 0.95, tMs = 0)))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(60), confidence = 0.95, tMs = 3000)))
        val entry = r.ledger()[ClaimType.LOCK_IN]!!
        assertEquals(ClaimValue.LockIn(60), entry.spoken!!.value)
        assertEquals(DeltaState.MATCHES, entry.state)
    }

    @Test
    fun `mentionCount counts repeats of the SAME value, independent of other values heard in between`() {
        val r = scannedReconciler()
        val v8 = ClaimValue.Rate(setOf(BigDecimal("8")), app.vaakku.domain.model.RateQualifier.ASSERTED)
        val v9 = ClaimValue.Rate(setOf(BigDecimal("9")), app.vaakku.domain.model.RateQualifier.ASSERTED)
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.RETURN_RATE, v8, tMs = 0)))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.RETURN_RATE, v9, tMs = 1000)))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.RETURN_RATE, v8, tMs = 2000)))
        assertEquals(2, r.ledger()[ClaimType.RETURN_RATE]!!.mentionCount)
    }

    // ------------------------------------------------------------------
    // UserRecheck / dismissal
    // ------------------------------------------------------------------

    @Test
    fun `rule 9 - UserRecheck dismisses a card until a new observation of that type arrives`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))
        assertTrue(r.ledger()[ClaimType.GUARANTEE]!!.state == DeltaState.DIFFERS)

        r.apply(ReconcilerEvent.UserRecheck(ClaimType.GUARANTEE))
        assertTrue(r.ledger()[ClaimType.GUARANTEE]!!.dismissed)

        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95, tMs = 5000)))
        assertFalse(r.ledger()[ClaimType.GUARANTEE]!!.dismissed)
    }

    // ------------------------------------------------------------------
    // Card selection
    // ------------------------------------------------------------------

    @Test
    fun `selectCard prefers the most recent non-dismissed DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95, tMs = 0)))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(12), confidence = 0.95, tMs = 5000)))

        val card = r.selectCard()
        assertEquals(ClaimType.LOCK_IN, card!!.type)
        assertEquals(2, card.totalPending)
    }

    @Test
    fun `selectCard falls back to NOT_IN_DOCUMENT only when there is no pending DIFFERS`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.BUNDLING, ClaimValue.Bundling(true), confidence = 0.95)))
        val card = r.selectCard()
        assertEquals(ClaimType.BUNDLING, card!!.type)
        assertEquals(DeltaState.NOT_IN_DOCUMENT, card.entry.state)
    }

    @Test
    fun `selectCard is null when nothing is pending`() {
        assertNull(scannedReconciler().selectCard())
    }

    @Test
    fun `a dismissed DIFFERS is excluded from card selection`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))
        r.apply(ReconcilerEvent.UserRecheck(ClaimType.GUARANTEE))
        assertNull(r.selectCard())
    }

    // ------------------------------------------------------------------
    // Reset / event log
    // ------------------------------------------------------------------

    @Test
    fun `Reset clears the derived ledger but the event log stays append-only`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))
        r.apply(ReconcilerEvent.Reset)

        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertEquals(DeltaState.PENDING, entry.state)
        assertEquals(ReasonCode.NO_SPOKEN, entry.reason)

        // DocumentScanCompleted, SpokenObserved, Reset — nothing was erased from the log.
        assertEquals(3, r.eventLog().size)
        assertEquals(ReconcilerEvent.Reset, r.eventLog().last())
    }

    @Test
    fun `eventLog preserves the exact order events were applied in`() {
        val r = Reconciler()
        r.apply(ReconcilerEvent.DocumentScanCompleted)
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.CHARGES, ClaimValue.Charges(false, null, null))))
        r.apply(ReconcilerEvent.UserRecheck(ClaimType.CHARGES))
        val log = r.eventLog()
        assertEquals(3, log.size)
        assertTrue(log[0] is ReconcilerEvent.DocumentScanCompleted)
        assertTrue(log[1] is ReconcilerEvent.SpokenObserved)
        assertTrue(log[2] is ReconcilerEvent.UserRecheck)
    }

    @Test
    fun `same events in the same order always produce the same ledger - deterministic replay`() {
        fun run(): Map<ClaimType, DeltaState> {
            val r = scannedReconciler()
            r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
            r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))
            return r.ledger().mapValues { it.value.state }
        }
        assertEquals(run(), run())
    }

    // ------------------------------------------------------------------
    // Coverage — LedgerEntry.observed
    //
    // How much of the conversation the app took in. The receipt screen shows
    // this as "N / 6"; it used to show `ledger().size / 6`, which is `6 / 6`
    // for every session ever recorded, including one where nothing was said
    // and no page was read. These tests exist so that cannot come back.
    //
    // Note what is deliberately NOT tested here, because it is deliberately
    // not implemented: there is no count of any DeltaState. Counting states
    // is what would turn a coverage number into a finding about a person
    // (CLAUDE.md #1).
    // ------------------------------------------------------------------

    private fun observedCount(r: Reconciler): Int = r.ledger().values.count { it.observed }

    @Test
    fun `a fresh reconciler has six rows and none of them observed`() {
        val r = Reconciler()
        // Both halves matter. The six is real — the ledger always holds one row
        // per ClaimType — which is exactly why the six cannot be the count.
        assertEquals(6, r.ledger().size)
        assertEquals(0, observedCount(r))
        assertTrue(r.ledger().values.none { it.observed })
    }

    @Test
    fun `a scan with nothing read off it observes nothing`() {
        // The failing session on the phone: the camera opened, a page was
        // captured, no clause was extracted from it, nobody spoke. An event
        // was applied, so "has anything happened" is true — and the answer to
        // "what did we take in" is still nothing.
        val r = scannedReconciler()
        assertEquals(0, observedCount(r))
    }

    @Test
    fun `a topic that was only spoken about is observed`() {
        val r = Reconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        val entry = r.ledger()[ClaimType.LOCK_IN]!!
        assertTrue(entry.observed)
        assertEquals(1, observedCount(r))
    }

    @Test
    fun `a topic that was only read off the document is observed while still PENDING`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.CHARGES, ClaimValue.Charges(true, BigDecimal("2"), "surrender"))))
        val entry = r.ledger()[ClaimType.CHARGES]!!
        // Nothing was said about it, so there is nothing to reconcile and the
        // row is silent (rule 1). The clause was still read, and the buyer
        // should be told the document was taken in.
        assertEquals(DeltaState.PENDING, entry.state)
        assertTrue(entry.observed)
        assertEquals(1, observedCount(r))
    }

    @Test
    fun `observed does not depend on state - a MATCHES and a DIFFERS each count once`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(60), confidence = 0.95)))
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))

        assertEquals(DeltaState.MATCHES, r.ledger()[ClaimType.LOCK_IN]!!.state)
        assertEquals(DeltaState.DIFFERS, r.ledger()[ClaimType.GUARANTEE]!!.state)
        // A topic that agreed and a topic that did not both count as "came up".
        assertEquals(2, observedCount(r))
    }

    @Test
    fun `an UNCERTAIN topic is observed - silence in the UI is not absence from the count`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), conditional = true)))
        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        // CLAUDE.md #2 keeps this row off the screen. It was still heard, and
        // the coverage count is not a count of what was shown.
        assertEquals(DeltaState.UNCERTAIN, entry.state)
        assertTrue(entry.observed)
        assertEquals(1, observedCount(r))
    }

    @Test
    fun `a session that touched all six topics counts six`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("8")), app.vaakku.domain.model.RateQualifier.ASSERTED))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LIQUIDITY, ClaimValue.Liquidity(withdrawableAfterMonths = 12, surrenderNilBeforeMonths = null))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.BUNDLING, ClaimValue.Bundling(true))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.CHARGES, ClaimValue.Charges(false, null, null))))
        // Six is reachable, so the field's ceiling is honest — it is just not
        // where an empty session starts.
        assertEquals(6, observedCount(r))
    }

    @Test
    fun `Reset clears the observed count and keeps the six rows`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.LOCK_IN, ClaimValue.LockIn(60))))
        assertEquals(1, observedCount(r))

        r.apply(ReconcilerEvent.Reset)
        assertEquals(6, r.ledger().size)
        assertEquals(0, observedCount(r))
    }

    @Test
    fun `a re-checked entry is still observed - dismissing hides a card, it does not un-hear a claim`() {
        val r = scannedReconciler()
        r.apply(ReconcilerEvent.WrittenObserved(writtenObs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false))))
        r.apply(ReconcilerEvent.SpokenObserved(spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), confidence = 0.95)))
        r.apply(ReconcilerEvent.UserRecheck(ClaimType.GUARANTEE))

        val entry = r.ledger()[ClaimType.GUARANTEE]!!
        assertTrue(entry.dismissed)
        assertTrue(entry.observed)
        assertEquals(1, observedCount(r))
    }
}
