package app.vaakku.domain.fixtures

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.RateQualifier
import app.vaakku.domain.model.Source
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class SlotCheckerTest {

    private fun obs(type: ClaimType, value: ClaimValue) = Observation(
        id = UUID.randomUUID().toString(), source = Source.SPOKEN, type = type, value = value,
        hedged = false, negated = false, conditional = false, confidence = 0.9,
        provenance = Provenance.Spoken("span", 0, 1000, "test"), tMs = 0,
    )

    @Test
    fun `GUARANTEE and BUNDLING and CHARGES match on their boolean`() {
        assertTrue(SlotChecker.matches(ClaimType.GUARANTEE, "true", listOf(obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true)))))
        assertFalse(SlotChecker.matches(ClaimType.GUARANTEE, "false", listOf(obs(ClaimType.GUARANTEE, ClaimValue.Guarantee(true)))))
        assertTrue(SlotChecker.matches(ClaimType.BUNDLING, "true", listOf(obs(ClaimType.BUNDLING, ClaimValue.Bundling(true)))))
        assertTrue(SlotChecker.matches(ClaimType.CHARGES, "false", listOf(obs(ClaimType.CHARGES, ClaimValue.Charges(false, null, null)))))
    }

    @Test
    fun `RETURN_RATE matches a comma-separated percent set exactly`() {
        val o = obs(ClaimType.RETURN_RATE, ClaimValue.Rate(setOf(BigDecimal("4"), BigDecimal("8")), RateQualifier.ILLUSTRATIVE))
        assertTrue(SlotChecker.matches(ClaimType.RETURN_RATE, "4,8", listOf(o)))
        assertTrue(SlotChecker.matches(ClaimType.RETURN_RATE, "8,4", listOf(o))) // order-independent
        assertFalse(SlotChecker.matches(ClaimType.RETURN_RATE, "8", listOf(o))) // must match the full set
    }

    @Test
    fun `LOCK_IN matches months as a plain integer string`() {
        assertTrue(SlotChecker.matches(ClaimType.LOCK_IN, "60", listOf(obs(ClaimType.LOCK_IN, ClaimValue.LockIn(60)))))
        assertFalse(SlotChecker.matches(ClaimType.LOCK_IN, "12", listOf(obs(ClaimType.LOCK_IN, ClaimValue.LockIn(60)))))
    }

    @Test
    fun `LIQUIDITY distinguishes withdrawAfter from nilBefore`() {
        val withdraw = obs(ClaimType.LIQUIDITY, ClaimValue.Liquidity(12, null))
        val nilBefore = obs(ClaimType.LIQUIDITY, ClaimValue.Liquidity(null, 60))
        assertTrue(SlotChecker.matches(ClaimType.LIQUIDITY, "withdrawAfter:12", listOf(withdraw)))
        assertFalse(SlotChecker.matches(ClaimType.LIQUIDITY, "nilBefore:12", listOf(withdraw)))
        assertTrue(SlotChecker.matches(ClaimType.LIQUIDITY, "nilBefore:60", listOf(nilBefore)))
    }

    @Test
    fun `no observation of the type at all is never a match`() {
        assertFalse(SlotChecker.matches(ClaimType.GUARANTEE, "true", emptyList()))
    }
}
