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
