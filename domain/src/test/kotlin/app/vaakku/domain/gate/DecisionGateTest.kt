package app.vaakku.domain.gate

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.RateQualifier
import app.vaakku.domain.model.ReasonCode
import app.vaakku.domain.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

/** The "before you sign" list — silence rules and enum ordering. */
class DecisionGateTest {

    private fun obs(type: ClaimType, value: ClaimValue, source: Source = Source.SPOKEN, confidence: Double = 0.9) = Observation(
        id = UUID.randomUUID().toString(), source = source, type = type, value = value,
        hedged = false, negated = false, conditional = false, confidence = confidence,
        provenance = if (source == Source.SPOKEN) {
            Provenance.Spoken("span", 0, 1000, "test")
        } else {
            Provenance.Written("line", Box(0, 0, 10, 10), "f1", null)
        },
        tMs = 0,
    )

    private fun entry(
        type: ClaimType,
        state: DeltaState,
        spoken: Observation?,
        written: List<Observation> = emptyList(),
        dismissed: Boolean = false,
    ) = LedgerEntry(type, spoken, written, state, ReasonCode.TOLERANCE_EXCEEDED, 1, dismissed)

    /** A DIFFERS entry for [type] with a spoken and a written value of that type. */
    private fun differs(type: ClaimType): LedgerEntry {
        val spoken = obs(type, spokenValueFor(type))
        val written = obs(type, writtenValueFor(type), source = Source.WRITTEN)
        return entry(type, DeltaState.DIFFERS, spoken, listOf(written))
    }

    private fun spokenValueFor(type: ClaimType): ClaimValue = when (type) {
        ClaimType.RETURN_RATE -> ClaimValue.Rate(setOf(BigDecimal("12")), RateQualifier.ASSERTED)
        ClaimType.GUARANTEE -> ClaimValue.Guarantee(true)
        ClaimType.LOCK_IN -> ClaimValue.LockIn(36)
        ClaimType.LIQUIDITY -> ClaimValue.Liquidity(withdrawableAfterMonths = 12, surrenderNilBeforeMonths = null)
        ClaimType.BUNDLING -> ClaimValue.Bundling(requiredForLoan = true)
        ClaimType.CHARGES -> ClaimValue.Charges(anyCharges = false, percent = null, label = null)
    }

    private fun writtenValueFor(type: ClaimType): ClaimValue = when (type) {
        ClaimType.RETURN_RATE -> ClaimValue.Rate(setOf(BigDecimal("4"), BigDecimal("8")), RateQualifier.ILLUSTRATIVE)
        ClaimType.GUARANTEE -> ClaimValue.Guarantee(false)
        ClaimType.LOCK_IN -> ClaimValue.LockIn(60)
        ClaimType.LIQUIDITY -> ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 60)
        ClaimType.BUNDLING -> ClaimValue.Bundling(requiredForLoan = false)
        ClaimType.CHARGES -> ClaimValue.Charges(anyCharges = true, percent = BigDecimal("5"), label = "premium allocation charge")
    }

    // ------------------------------------------------------------------
    // Silence
    // ------------------------------------------------------------------

    @Test
    fun `an empty ledger produces an empty list`() {
        assertEquals(emptyList<GateItem>(), DecisionGate.build(emptyMap()))
    }

    @Test
    fun `a ledger of only MATCHES, PENDING and UNCERTAIN produces an empty list`() {
        val ledger = mapOf(
            ClaimType.GUARANTEE to entry(ClaimType.GUARANTEE, DeltaState.MATCHES, obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true))),
            ClaimType.LOCK_IN to entry(ClaimType.LOCK_IN, DeltaState.PENDING, null),
            ClaimType.CHARGES to entry(
                ClaimType.CHARGES,
                DeltaState.UNCERTAIN,
                obs(ClaimType.CHARGES, ClaimValue.Charges(anyCharges = false, percent = null, label = null)),
            ),
        )
        assertEquals(emptyList<GateItem>(), DecisionGate.build(ledger))
    }

    @Test
    fun `a dismissed DIFFERS is excluded`() {
        val spoken = obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true))
        val written = obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false), source = Source.WRITTEN)
        val ledger = mapOf(
            ClaimType.GUARANTEE to entry(ClaimType.GUARANTEE, DeltaState.DIFFERS, spoken, listOf(written), dismissed = true),
        )
        assertEquals(emptyList<GateItem>(), DecisionGate.build(ledger))
    }

    @Test
    fun `a dismissed NOT_IN_DOCUMENT is excluded`() {
        val ledger = mapOf(
            ClaimType.BUNDLING to entry(
                ClaimType.BUNDLING,
                DeltaState.NOT_IN_DOCUMENT,
                obs(ClaimType.BUNDLING, ClaimValue.Bundling(requiredForLoan = true)),
                dismissed = true,
            ),
        )
        assertEquals(emptyList<GateItem>(), DecisionGate.build(ledger))
    }

    // ------------------------------------------------------------------
    // Ordering — ClaimType enum order and nothing else
    // ------------------------------------------------------------------

    @Test
    fun `a mixed ledger is returned in ClaimType enum order`() {
        // Deliberately inserted in reverse enum order, so a pass here cannot be
        // an accident of the map's iteration order.
        val ledger = linkedMapOf(
            ClaimType.CHARGES to differs(ClaimType.CHARGES),
            ClaimType.BUNDLING to entry(
                ClaimType.BUNDLING,
                DeltaState.NOT_IN_DOCUMENT,
                obs(ClaimType.BUNDLING, ClaimValue.Bundling(requiredForLoan = true)),
            ),
            ClaimType.LIQUIDITY to entry(ClaimType.LIQUIDITY, DeltaState.MATCHES, obs(ClaimType.LIQUIDITY, spokenValueFor(ClaimType.LIQUIDITY))),
            ClaimType.LOCK_IN to differs(ClaimType.LOCK_IN),
            ClaimType.GUARANTEE to entry(ClaimType.GUARANTEE, DeltaState.PENDING, null),
            ClaimType.RETURN_RATE to differs(ClaimType.RETURN_RATE),
        )

        val items = DecisionGate.build(ledger)
        assertEquals(
            listOf(ClaimType.RETURN_RATE, ClaimType.LOCK_IN, ClaimType.BUNDLING, ClaimType.CHARGES),
            items.map { it.type },
        )
    }

    /**
     * The ordering is enum order *only*. Confidence, recency (`tMs`) and
     * `mentionCount` all disagree with enum order here; if any of them were
     * used as a tiebreak or a sort key, this fails. Ranking by importance is a
     * severity ranking under another name (CLAUDE.md #1).
     */
    @Test
    fun `ordering ignores confidence, recency and mentionCount`() {
        fun loudEntry(type: ClaimType, confidence: Double, tMs: Long, mentions: Int): LedgerEntry {
            val spoken = obs(type, spokenValueFor(type), confidence = confidence).copy(tMs = tMs)
            val written = obs(type, writtenValueFor(type), source = Source.WRITTEN, confidence = confidence)
            return LedgerEntry(type, spoken, listOf(written), DeltaState.DIFFERS, ReasonCode.TOLERANCE_EXCEEDED, mentions, false)
        }

        val ledger = mapOf(
            // RETURN_RATE is first in the enum and last by every other measure.
            ClaimType.RETURN_RATE to loudEntry(ClaimType.RETURN_RATE, confidence = 0.61, tMs = 10, mentions = 1),
            ClaimType.LOCK_IN to loudEntry(ClaimType.LOCK_IN, confidence = 0.80, tMs = 5_000, mentions = 3),
            ClaimType.CHARGES to loudEntry(ClaimType.CHARGES, confidence = 0.99, tMs = 9_000, mentions = 7),
        )

        assertEquals(
            listOf(ClaimType.RETURN_RATE, ClaimType.LOCK_IN, ClaimType.CHARGES),
            DecisionGate.build(ledger).map { it.type },
        )
    }

    @Test
    fun `every returned item is DIFFERS or NOT_IN_DOCUMENT`() {
        val ledger = ClaimType.entries.associateWith { differs(it) } +
            mapOf(ClaimType.GUARANTEE to entry(ClaimType.GUARANTEE, DeltaState.UNCERTAIN, obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true))))
        val states = DecisionGate.build(ledger).map { it.state }.toSet()
        assertTrue(states.all { it == DeltaState.DIFFERS || it == DeltaState.NOT_IN_DOCUMENT }, "unexpected states: $states")
    }

    // ------------------------------------------------------------------
    // Row content
    // ------------------------------------------------------------------

    @Test
    fun `a NOT_IN_DOCUMENT item has a null written key and a non-null follow-up key`() {
        val ledger = mapOf(
            ClaimType.BUNDLING to entry(
                ClaimType.BUNDLING,
                DeltaState.NOT_IN_DOCUMENT,
                obs(ClaimType.BUNDLING, ClaimValue.Bundling(requiredForLoan = true)),
            ),
        )
        val item = DecisionGate.build(ledger).single()
        assertEquals(ClaimType.BUNDLING, item.type)
        assertEquals(DeltaState.NOT_IN_DOCUMENT, item.state)
        assertEquals("v_required_for_loan", item.spokenTemplateKey)
        assertNull(item.writtenTemplateKey)
        assertEquals(emptyList<String>(), item.writtenArgs)
        assertNotNull(item.followUpKey)
        assertEquals("followup_bundling", item.followUpKey)
    }

    @Test
    fun `a DIFFERS item carries both value phrases and the type's follow-up key`() {
        val spoken = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(36))
        val written = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(60), source = Source.WRITTEN)
        val ledger = mapOf(ClaimType.LOCK_IN to entry(ClaimType.LOCK_IN, DeltaState.DIFFERS, spoken, listOf(written)))

        val item = DecisionGate.build(ledger).single()
        assertEquals("v_years", item.spokenTemplateKey)
        assertEquals(listOf("3"), item.spokenArgs)
        assertEquals("v_years", item.writtenTemplateKey)
        assertEquals(listOf("5"), item.writtenArgs)
        assertEquals("followup_lockin_liquidity", item.followUpKey)
    }

    /** Same rule as the card (§5.8): the row and the card must never quote different numbers. */
    @Test
    fun `a DIFFERS item quotes the most confident written observation`() {
        val spoken = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(12))
        val weak = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(36), source = Source.WRITTEN, confidence = 0.5)
        val strong = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(60), source = Source.WRITTEN, confidence = 0.95)
        val ledger = mapOf(ClaimType.LOCK_IN to entry(ClaimType.LOCK_IN, DeltaState.DIFFERS, spoken, listOf(weak, strong)))

        assertEquals(listOf("5"), DecisionGate.build(ledger).single().writtenArgs)
    }

    /**
     * Silence, not a throw, when a state arrives without the observations it
     * needs. This list opens automatically at the end of a session, so a crash
     * here is worse than an omission (CLAUDE.md #2).
     */
    @Test
    fun `a malformed DIFFERS with no spoken or no written observation produces no row`() {
        val noSpoken = mapOf(
            ClaimType.GUARANTEE to entry(
                ClaimType.GUARANTEE,
                DeltaState.DIFFERS,
                null,
                listOf(obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false), source = Source.WRITTEN)),
            ),
        )
        assertEquals(emptyList<GateItem>(), DecisionGate.build(noSpoken))

        val noWritten = mapOf(
            ClaimType.GUARANTEE to entry(ClaimType.GUARANTEE, DeltaState.DIFFERS, obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true))),
        )
        assertEquals(emptyList<GateItem>(), DecisionGate.build(noWritten))

        val notInDocumentNoSpoken = mapOf(
            ClaimType.BUNDLING to entry(ClaimType.BUNDLING, DeltaState.NOT_IN_DOCUMENT, null),
        )
        assertEquals(emptyList<GateItem>(), DecisionGate.build(notInDocumentNoSpoken))
    }

    /** Every follow-up key the list can emit is one of the four §8.4 keys. */
    @Test
    fun `follow-up keys come from the §8_4 set only`() {
        val ledger = ClaimType.entries.associateWith { differs(it) }
        val keys = DecisionGate.build(ledger).map { it.followUpKey }.toSet()
        assertEquals(
            setOf("followup_rate_charges", "followup_guarantee", "followup_lockin_liquidity", "followup_bundling"),
            keys,
        )
    }
}
