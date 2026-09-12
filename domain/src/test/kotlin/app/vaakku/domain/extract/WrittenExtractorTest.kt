package app.vaakku.domain.extract

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.OcrLine
import app.vaakku.domain.model.RateQualifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Build plan §5.6 — every regex rule, exercised against the exact prop-
 * document lines named in §11.2 ("Assumed rate of return (illustrative) 4%
 * p.a. and 8% p.a."; "Returns are NOT guaranteed"; "Guaranteed returns: No";
 * "Lock-in period: 5 years"; "Surrender value: Nil before completion of 5th
 * policy year"; "Premium allocation charge: 5% in year 1"; "Policy term 10
 * years"; "Annualised premium ₹1,20,000"; silent on loans).
 */
class WrittenExtractorTest {

    private val extractor = WrittenExtractor()

    private fun line(text: String, left: Int = 40, top: Int = 0, right: Int = 800, bottom: Int = 30, confidence: Double = 0.95) =
        OcrLine(text = text, box = Box(left, top, right, bottom), confidence = confidence, frameId = "f1")

    private fun extractOne(text: String, confidence: Double = 0.95) =
        extractor.extract(listOf(line(text, confidence = confidence)))

    @Test
    fun `RATE - label and value cells on one row give an ILLUSTRATIVE Rate with both percents`() {
        val label = line("Assumed rate of return (illustrative)", left = 40, top = 300, right = 520, bottom = 330)
        val value = line("4% p.a. and 8% p.a.", left = 560, top = 300, right = 820, bottom = 330)
        val obs = extractor.extract(listOf(label, value)).single { it.type == ClaimType.RETURN_RATE }
        assertEquals(ClaimValue.Rate(setOf(BigDecimal("4"), BigDecimal("8")), RateQualifier.ILLUSTRATIVE), obs.value)
        assertEquals(0.95, obs.confidence)
    }

    @Test
    fun `GUARANTEE - "Returns are NOT guaranteed" gives Guarantee false`() {
        val obs = extractOne("Returns are NOT guaranteed").single { it.type == ClaimType.GUARANTEE }
        assertEquals(ClaimValue.Guarantee(false), obs.value)
    }

    @Test
    fun `GUARANTEE - "Guaranteed returns- No" gives Guarantee false`() {
        val obs = extractOne("Guaranteed returns: No").single { it.type == ClaimType.GUARANTEE }
        assertEquals(ClaimValue.Guarantee(false), obs.value)
    }

    @Test
    fun `GUARANTEE - "Guaranteed returns- Yes" gives Guarantee true`() {
        val obs = extractOne("Guaranteed returns: Yes").single { it.type == ClaimType.GUARANTEE }
        assertEquals(ClaimValue.Guarantee(true), obs.value)
    }

    @Test
    fun `LOCK_IN - "Lock-in period- 5 years" gives 60 months`() {
        val obs = extractOne("Lock-in period: 5 years").single { it.type == ClaimType.LOCK_IN }
        assertEquals(ClaimValue.LockIn(60), obs.value)
    }

    @Test
    fun `LIQUIDITY - the surrender-nil-before sentence gives surrenderNilBeforeMonths 60`() {
        val obs = extractOne("Surrender value: Nil before completion of 5th policy year")
            .single { it.type == ClaimType.LIQUIDITY }
        assertEquals(ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 60), obs.value)
    }

    @Test
    fun `BUNDLING - a voluntary clause gives Bundling false`() {
        val obs = extractOne("Purchase is voluntary / not a condition for any loan").single { it.type == ClaimType.BUNDLING }
        assertEquals(ClaimValue.Bundling(requiredForLoan = false), obs.value)
    }

    @Test
    fun `CHARGES - "Premium allocation charge- 5% in year 1" gives Charges true 5`() {
        val obs = extractOne("Premium allocation charge: 5% in year 1").single { it.type == ClaimType.CHARGES }
        val v = obs.value as ClaimValue.Charges
        assertTrue(v.anyCharges)
        assertEquals(BigDecimal("5"), v.percent)
    }

    @Test
    fun `"Policy term 10 years" produces no LOCK_IN - term is not lock-in even on the written side`() {
        assertTrue(extractOne("Policy term 10 years").none { it.type == ClaimType.LOCK_IN })
    }

    @Test
    fun `an unrelated row produces no observations at all`() {
        assertEquals(emptyList<Any>(), extractOne("Annualised premium ₹1,20,000"))
    }

    @Test
    fun `A7 - an implausible percent value (OCR misread) is scored to near-zero confidence, not dropped`() {
        // "40%" is outside the 0-30 plausibility band (§5.6) — the observation
        // still exists (so the reconciler can see and reject it), but its
        // confidence collapses, which is what pushes it to UNCERTAIN rather
        // than a false MATCHES/DIFFERS.
        val obs = extractOne("Assumed rate of return 40%", confidence = 0.4).single { it.type == ClaimType.RETURN_RATE }
        assertEquals(0.0, obs.confidence)
    }

    @Test
    fun `GUARANTEE - real prop doc §6 "Guaranteed Returns on premiums paid- No" gives Guarantee false`() {
        // Known defect (task 1, step 3): the original GUARANTEE_FALSE pattern
        // required "no" immediately after "guaranteed returns"; the real
        // document's "on premiums paid" qualifier broke it.
        val obs = extractOne("Guaranteed Returns on premiums paid: No.").single { it.type == ClaimType.GUARANTEE }
        assertEquals(ClaimValue.Guarantee(false), obs.value)
    }

    @Test
    fun `GUARANTEE - "Guaranteed Returns on premiums paid- Yes" is never read as false`() {
        // Same intervening-words shape as the real document's "No" sentence,
        // but ending in "Yes" — must not be misread as Guarantee(false). It is
        // fine (and expected, per the tight GUARANTEE_TRUE pattern) that this
        // produces no observation at all rather than Guarantee(true) — silence
        // is the safe outcome, never a wrong DIFFERS/MATCHES.
        val obs = extractOne("Guaranteed Returns on premiums paid: Yes.")
        assertTrue(obs.none { it.type == ClaimType.GUARANTEE && it.value == ClaimValue.Guarantee(false) })
    }

    @Test
    fun `GUARANTEE - the §4 table row RowAssembler joins into "Guaranteed Returns No" (no colon) still gives Guarantee false`() {
        // The real §4 "Your Policy at a Glance" table has this as two cells —
        // label "Guaranteed Returns", value "No" — which RowAssembler joins
        // with a single space and no colon. "No" is still the row's very last
        // content, so it is still the value shape, not a determiner.
        val obs = extractOne("Guaranteed Returns No").single { it.type == ClaimType.GUARANTEE }
        assertEquals(ClaimValue.Guarantee(false), obs.value)
    }

    @Test
    fun `GUARANTEE - fix round 1 Critical counterexample 1 - "no exceptions" is a determiner, never Guarantee false`() {
        // Reviewer counterexample: "no" here introduces the noun "exceptions",
        // it is not the row's terminal value — must not fire.
        val obs = extractOne("Guaranteed Returns apply throughout; no exceptions.")
        assertTrue(obs.none { it.type == ClaimType.GUARANTEE })
    }

    @Test
    fun `GUARANTEE - fix round 1 Critical counterexample 2 - "no additional underwriting" is a determiner, never Guarantee false`() {
        // Reviewer counterexample: "no" introduces "additional underwriting",
        // not a terminal "No" value — must not fire.
        val obs = extractOne("Guaranteed Returns require no additional underwriting.")
        assertTrue(obs.none { it.type == ClaimType.GUARANTEE })
    }

    @Test
    fun `CHARGES - the real §7 2 percent tier (years 2-5) is a separate, deliberate observation`() {
        val obs = extractOne("Premium Allocation Charge Years 2-5 2% of Annualised Premium")
            .single { it.type == ClaimType.CHARGES }
        val v = obs.value as ClaimValue.Charges
        assertTrue(v.anyCharges)
        assertEquals(BigDecimal("2"), v.percent)
    }

    @Test
    fun `CHARGES - the real §7 "Nil" tier (year 6 onward) produces no observation, not Charges(false)`() {
        val obs = extractOne("Premium Allocation Charge Year 6 onward Nil")
        assertTrue(obs.none { it.type == ClaimType.CHARGES })
    }

    @Test
    fun `the page-7 glossary's "Lock-in Period" definition has the label but no duration, so it is silent`() {
        val obs = extractOne(
            "Lock-in Period -- The minimum period during which the policy cannot be surrendered for any value.",
        )
        assertTrue(obs.none { it.type == ClaimType.LOCK_IN })
    }

    @Test
    fun `the repeated page header-footer ("Benefit Illustration" and "Page N of 10") produces no observations`() {
        assertEquals(
            emptyList<Any>(),
            extractOne("Nambikkai Life Insurance Company Limited (specimen) Benefit Illustration -- Suraksha Savings Plan"),
        )
        assertEquals(
            emptyList<Any>(),
            extractOne("SPECIMEN DOCUMENT -- prepared for a hackathon demonstration only, not a real product or offer Page 6 of 10"),
        )
    }

    @Test
    fun `no BUNDLING observation at all when the document is silent on loans`() {
        val obs = extractor.extract(
            listOf(
                line("Assumed rate of return (illustrative) 4% p.a. and 8% p.a.", top = 300, bottom = 330),
                line("Returns are NOT guaranteed", top = 350, bottom = 380),
                line("Lock-in period: 5 years", top = 400, bottom = 430),
            ),
        )
        assertTrue(obs.none { it.type == ClaimType.BUNDLING })
    }
}
