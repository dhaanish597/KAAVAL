package app.vaakku.domain.copy

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

/** Build plan §5.8 / §8 — LedgerEntry -> template key + args, never rendered prose. */
class CopyBuilderTest {

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

    private fun entry(type: ClaimType, state: DeltaState, spoken: Observation?, written: List<Observation> = emptyList()) =
        LedgerEntry(type, spoken, written, state, ReasonCode.TOLERANCE_EXCEEDED, 1, dismissed = false)

    // ------------------------------------------------------------------
    // Silence enforcement
    // ------------------------------------------------------------------

    @Test
    fun `build returns null for MATCHES, PENDING and UNCERTAIN - only DIFFERS and NOT_IN_DOCUMENT get a card`() {
        val spoken = obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true))
        assertNull(CopyBuilder.build(entry(ClaimType.GUARANTEE, DeltaState.MATCHES, spoken)))
        assertNull(CopyBuilder.build(entry(ClaimType.GUARANTEE, DeltaState.PENDING, spoken)))
        assertNull(CopyBuilder.build(entry(ClaimType.GUARANTEE, DeltaState.UNCERTAIN, spoken)))
    }

    // ------------------------------------------------------------------
    // DIFFERS card
    // ------------------------------------------------------------------

    @Test
    fun `DIFFERS card carries the spoken and written value refs and the type's follow-up`() {
        val spoken = obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true))
        val written = obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(false), source = Source.WRITTEN)
        val card = CopyBuilder.build(entry(ClaimType.GUARANTEE, DeltaState.DIFFERS, spoken, listOf(written))) as CardCopy.Differs

        assertEquals(CopyRef("state_differs"), card.stateLabel)
        assertEquals("card_differs_spoken_line", card.spokenLine.templateKey)
        assertEquals(CopyRef("v_guaranteed"), card.spokenLine.value)
        assertEquals("card_differs_written_line", card.writtenLine.templateKey)
        assertEquals(CopyRef("v_not_guaranteed"), card.writtenLine.value)
        assertEquals("card_differs_footer", card.footerKey)
        assertEquals(CopyRef("followup_guarantee"), card.followUp)
    }

    @Test
    fun `DIFFERS card picks the most confident written observation when several exist`() {
        val spoken = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(12))
        val weakWritten = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(36), source = Source.WRITTEN, confidence = 0.5)
        val strongWritten = obs(ClaimType.LOCK_IN, ClaimValue.LockIn(60), source = Source.WRITTEN, confidence = 0.95)
        val card = CopyBuilder.build(entry(ClaimType.LOCK_IN, DeltaState.DIFFERS, spoken, listOf(weakWritten, strongWritten))) as CardCopy.Differs
        assertEquals(CopyRef("v_years", listOf("5")), card.writtenLine.value)
    }

    // ------------------------------------------------------------------
    // NOT_IN_DOCUMENT card
    // ------------------------------------------------------------------

    @Test
    fun `A10 - NOT_IN_DOCUMENT card carries the spoken value ref and a fixed hint key, no written ref`() {
        val spoken = obs(ClaimType.BUNDLING, ClaimValue.Bundling(true))
        val card = CopyBuilder.build(entry(ClaimType.BUNDLING, DeltaState.NOT_IN_DOCUMENT, spoken)) as CardCopy.NotInDocument
        assertEquals(CopyRef("state_not_in_document"), card.stateLabel)
        assertEquals(CopyRef("v_required_for_loan"), card.spokenLine.value)
        assertEquals("card_not_in_document_hint_line", card.hintKey)
        assertEquals(CopyRef("followup_bundling"), card.followUp)
    }

    // ------------------------------------------------------------------
    // Follow-up grouping — §8.4
    // ------------------------------------------------------------------

    @Test
    fun `follow-up keys are grouped exactly as §8_4 groups them`() {
        assertEquals(CopyRef("followup_guarantee"), CopyBuilder.followUpFor(ClaimType.GUARANTEE))
        assertEquals(CopyRef("followup_lockin_liquidity"), CopyBuilder.followUpFor(ClaimType.LOCK_IN))
        assertEquals(CopyRef("followup_lockin_liquidity"), CopyBuilder.followUpFor(ClaimType.LIQUIDITY))
        assertEquals(CopyRef("followup_bundling"), CopyBuilder.followUpFor(ClaimType.BUNDLING))
        assertEquals(CopyRef("followup_rate_charges"), CopyBuilder.followUpFor(ClaimType.RETURN_RATE))
        assertEquals(CopyRef("followup_rate_charges"), CopyBuilder.followUpFor(ClaimType.CHARGES))
    }

    // ------------------------------------------------------------------
    // ValuePhrase — §8.3
    // ------------------------------------------------------------------

    @Test
    fun `GUARANTEE value phrases`() {
        assertEquals(CopyRef("v_not_guaranteed"), ValuePhrase.forGuarantee(ClaimValue.Guarantee(false)))
        assertEquals(CopyRef("v_guaranteed"), ValuePhrase.forGuarantee(ClaimValue.Guarantee(true)))
        assertEquals(
            CopyRef("v_guaranteed_pct", listOf("8")),
            ValuePhrase.forGuarantee(ClaimValue.Guarantee(true), coStatedRatePercent = BigDecimal("8")),
        )
    }

    @Test
    fun `RATE value phrase lists percents sorted and comma-joined`() {
        val rate = ClaimValue.Rate(setOf(BigDecimal("8"), BigDecimal("4")), RateQualifier.ILLUSTRATIVE)
        assertEquals(CopyRef("v_illustrative_pcts", listOf("4, 8")), ValuePhrase.forRate(rate))
    }

    @Test
    fun `LOCK_IN value phrase prefers whole years, falls back to months`() {
        assertEquals(CopyRef("v_years", listOf("5")), ValuePhrase.forLockIn(ClaimValue.LockIn(60)))
        assertEquals(CopyRef("v_months", listOf("18")), ValuePhrase.forLockIn(ClaimValue.LockIn(18)))
    }

    @Test
    fun `LIQUIDITY value phrases pick withdraw-after or surrender-nil-before`() {
        assertEquals(
            CopyRef("v_withdraw_after", listOf("1")),
            ValuePhrase.forLiquidity(ClaimValue.Liquidity(withdrawableAfterMonths = 12, surrenderNilBeforeMonths = null)),
        )
        assertEquals(
            CopyRef("v_surrender_nil_before", listOf("5")),
            ValuePhrase.forLiquidity(ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 60)),
        )
    }

    /**
     * The regression that the fixtures could not catch: every liquidity duration in
     * the suite happens to be an exact number of years, so an unconditional `/ 12`
     * passed everything while turning 18 months into "1 year" on the card.
     */
    @Test
    fun `LIQUIDITY value phrases stay in months when the duration is not whole years`() {
        assertEquals(
            CopyRef("v_withdraw_after_months", listOf("18")),
            ValuePhrase.forLiquidity(ClaimValue.Liquidity(withdrawableAfterMonths = 18, surrenderNilBeforeMonths = null)),
        )
        assertEquals(
            CopyRef("v_surrender_nil_before_months", listOf("30")),
            ValuePhrase.forLiquidity(ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 30)),
        )
        // 1 month must not silently become "0 years".
        assertEquals(
            CopyRef("v_withdraw_after_months", listOf("1")),
            ValuePhrase.forLiquidity(ClaimValue.Liquidity(withdrawableAfterMonths = 1, surrenderNilBeforeMonths = null)),
        )
    }

    @Test
    fun `BUNDLING and CHARGES value phrases`() {
        assertEquals(CopyRef("v_required_for_loan"), ValuePhrase.forBundling(ClaimValue.Bundling(true)))
        assertEquals(CopyRef("v_voluntary"), ValuePhrase.forBundling(ClaimValue.Bundling(false)))
        assertEquals(CopyRef("v_no_charges"), ValuePhrase.forCharges(ClaimValue.Charges(false, null, null)))
        assertEquals(
            CopyRef("v_charge_pct", listOf("5")),
            ValuePhrase.forCharges(ClaimValue.Charges(true, BigDecimal("5"), "premium allocation charge")),
        )
        assertEquals(CopyRef("v_charge_present"), ValuePhrase.forCharges(ClaimValue.Charges(true, null, null)))
    }

    @Test
    fun `formatNumber trims a trailing zero but keeps a real fraction`() {
        assertEquals("8", ValuePhrase.formatNumber(BigDecimal("8.0")))
        assertEquals("4.5", ValuePhrase.formatNumber(BigDecimal("4.5")))
    }

    @Test
    fun `no CopyRef key or template key produced by any test case above contains a banned word`() {
        val banned = listOf("verdict", "risk", "score", "fraud", "suspicious", "mislead", "judge")
        val allKeys = listOf(
            "state_differs", "state_matches", "state_not_in_document", "state_silent",
            "card_differs_spoken_line", "card_differs_written_line", "card_differs_footer",
            "card_differs_english_small_line", "card_not_in_document_spoken_line", "card_not_in_document_hint_line",
            "followup_guarantee", "followup_lockin_liquidity", "followup_bundling", "followup_rate_charges",
            "v_guaranteed_pct", "v_not_guaranteed", "v_guaranteed", "v_illustrative_pcts", "v_years", "v_months",
            "v_withdraw_after", "v_surrender_nil_before", "v_withdraw_after_months",
            "v_surrender_nil_before_months", "v_liquidity_unspecified", "v_required_for_loan",
            "v_voluntary", "v_no_charges", "v_charge_pct", "v_charge_present",
        )
        allKeys.forEach { key -> banned.forEach { b -> assert(!key.contains(b, ignoreCase = true)) { "key '$key' contains banned word '$b'" } } }
    }
}
