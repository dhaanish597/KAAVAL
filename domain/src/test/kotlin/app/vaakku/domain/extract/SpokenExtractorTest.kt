package app.vaakku.domain.extract

import app.vaakku.domain.lexicon.LexiconLoader
import app.vaakku.domain.model.AsrSegment
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.RateQualifier
import app.vaakku.domain.model.ReasonCode
import app.vaakku.domain.normalize.Normalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** Build plan §5.5 — hedge, negation, conditional, the GUARANTEE special rule, "FD மாதிரி" alone. */
class SpokenExtractorTest {

    private val lexicon = LexiconLoader.loadDefault()
    private val extractor = SpokenExtractor(lexicon, Normalizer(lexicon))

    private fun seg(text: String, quality: Double = 0.9) =
        AsrSegment(text = text, startMs = 0, endMs = 1000, engine = "test", segmentQuality = quality)

    private fun single(text: String, quality: Double = 0.9) = extractor.extract(seg(text, quality)).single()
    private fun of(text: String, type: ClaimType, quality: Double = 0.9) =
        extractor.extract(seg(text, quality)).single { it.type == type }

    // ------------------------------------------------------------------
    // §5.5.7 — "FD மாதிரி" alone produces nothing
    // ------------------------------------------------------------------

    @Test
    fun `FD மாதிரி alone produces no GUARANTEE observation`() {
        val obs = extractor.extract(seg("இது FD மாதிரி தான் sir."))
        assertTrue(obs.none { it.type == ClaimType.GUARANTEE }, "expected no GUARANTEE, got $obs")
    }

    @Test
    fun `T01 - FD மாதிரி plus an explicit guaranteed word DOES produce GUARANTEE true and RATE 8`() {
        val obs = extractor.extract(seg("Sir, இது FD மாதிரி தான். Guaranteed எட்டு percent return."))
        val guarantee = obs.single { it.type == ClaimType.GUARANTEE }
        assertEquals(ClaimValue.Guarantee(true), guarantee.value)
        assertNull(guarantee.ambiguous)
        val rate = obs.single { it.type == ClaimType.RETURN_RATE }
        assertEquals(ClaimValue.Rate(setOf(BigDecimal("8")), RateQualifier.ASSERTED), rate.value)
    }

    // ------------------------------------------------------------------
    // GUARANTEE special rule — clear negation vs fuzzy-only negation
    // ------------------------------------------------------------------

    @Test
    fun `an exact negation near guaranteed gives Guarantee false, not ambiguous`() {
        val g = of("Return guarantee இல்லை sir.", ClaimType.GUARANTEE)
        assertEquals(ClaimValue.Guarantee(false), g.value)
        assertTrue(g.negated)
        assertNull(g.ambiguous)
    }

    @Test
    fun `A1 - a fuzzy-only negation near guaranteed is flagged NEGATION_AMBIGUOUS, not asserted false or true`() {
        // ASR drops "not" but leaves a one-edit-distance-corrupted negation
        // token nearby ("கிடையாது" -> "கிடையது", a dropped vowel-length mark).
        val g = of("Guaranteed கிடையது sir", ClaimType.GUARANTEE)
        assertEquals(ReasonCode.NEGATION_AMBIGUOUS, g.ambiguous)
        assertTrue(!g.negated) // the clean/exact negation flag is NOT set — only the ambiguity flag is
    }

    @Test
    fun `no negation nearby leaves Guarantee true and unambiguous`() {
        val g = of("இது உறுதியான எட்டு சதவீத வருமானம் தரும்.", ClaimType.GUARANTEE)
        assertEquals(ClaimValue.Guarantee(true), g.value)
        assertNull(g.ambiguous)
    }

    // ------------------------------------------------------------------
    // Conditional — never leads to a plain assertion
    // ------------------------------------------------------------------

    @Test
    fun `A3 - a hypothetical guarantee is marked conditional`() {
        val g = of("Guaranteed-ஆ இருந்தா எட்டு percent கிடைக்கும்", ClaimType.GUARANTEE)
        assertTrue(g.conditional)
    }

    @Test
    fun `T09 - the conditional half is ignored once a later concrete negation restates it`() {
        val obs = extractor.extract(
            seg("Guaranteed-ஆ இருந்தா எட்டு percent கிடைக்கும், ஆனா இது guaranteed இல்லை."),
        )
        val g = obs.single { it.type == ClaimType.GUARANTEE }
        assertEquals(ClaimValue.Guarantee(false), g.value)
        assertTrue(!g.conditional, "the LATEST mention (a plain negation) must win, not the earlier hypothetical")
        assertNull(g.ambiguous)
    }

    // ------------------------------------------------------------------
    // Hedge — RATE qualifier
    // ------------------------------------------------------------------

    @Test
    fun `A8 T08 - an up-to hedge sets qualifier UP_TO and hedged true`() {
        val r = of("Up to எட்டு percent வரைக்கும் வரலாம் sir.", ClaimType.RETURN_RATE)
        assertEquals(ClaimValue.Rate(setOf(BigDecimal("8")), RateQualifier.UP_TO), r.value)
        assertTrue(r.hedged)
    }

    @Test
    fun `T05 - an illustrative multi-scenario rate is qualified ILLUSTRATIVE but not hedged`() {
        val r = of(
            "Return guarantee இல்லை sir. Illustration-ல நாலு percent, எட்டு percent ரெண்டு scenario இருக்கு.",
            ClaimType.RETURN_RATE,
        )
        val rate = r.value as ClaimValue.Rate
        assertEquals(setOf(BigDecimal("4"), BigDecimal("8")), rate.percents)
        assertEquals(RateQualifier.ILLUSTRATIVE, rate.qualifier)
        assertTrue(!r.hedged, "an honest illustrative quote is not an evasive hedge")
    }

    @Test
    fun `a plain assertion with no hedge word is ASSERTED and not hedged`() {
        val r = of("Guaranteed எட்டு percent return.", ClaimType.RETURN_RATE)
        assertEquals(RateQualifier.ASSERTED, (r.value as ClaimValue.Rate).qualifier)
        assertTrue(!r.hedged)
    }

    // ------------------------------------------------------------------
    // LOCK_IN / LIQUIDITY
    // ------------------------------------------------------------------

    @Test
    fun `T02 - two different duration claims in one segment resolve independently`() {
        val obs = extractor.extract(
            seg("Lock-in ஒரு வருஷம் தான் sir. One year கழிச்சு full-ஆ எடுக்கலாம்."),
        )
        val lockIn = obs.single { it.type == ClaimType.LOCK_IN }
        assertEquals(ClaimValue.LockIn(12), lockIn.value)
        val liquidity = obs.single { it.type == ClaimType.LIQUIDITY }
        assertEquals(ClaimValue.Liquidity(withdrawableAfterMonths = 12, surrenderNilBeforeMonths = null), liquidity.value)
    }

    @Test
    fun `A2 - a self-corrected lock-in duration takes the LATEST value`() {
        val obs = extractor.extract(
            seg("lock-in மூணு வருஷம் sir ஆனா இல்ல இல்ல அஞ்சு வருஷம் தான் sir"),
        )
        val lockIn = obs.single { it.type == ClaimType.LOCK_IN }
        assertEquals(ClaimValue.LockIn(60), lockIn.value)
    }

    @Test
    fun `T06 - a same-segment surrender-nil-before refers back to the stated lock-in`() {
        val obs = extractor.extract(
            seg("Lock-in அஞ்சு வருஷம். அதுக்கு முன்னாடி surrender value கிடையாது."),
        )
        val lockIn = obs.single { it.type == ClaimType.LOCK_IN }
        assertEquals(ClaimValue.LockIn(60), lockIn.value)
        val liquidity = obs.single { it.type == ClaimType.LIQUIDITY }
        assertEquals(ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 60), liquidity.value)
    }

    @Test
    fun `A6 T11 - policy term is not lock-in, and produces no LOCK_IN claim`() {
        val obs = extractor.extract(seg("Premium வருஷத்துக்கு ஒரு லட்சம் இருபதாயிரம், term பத்து வருஷம்."))
        assertTrue(obs.none { it.type == ClaimType.LOCK_IN }, "expected no LOCK_IN, got $obs")
        assertTrue(obs.none { it.type == ClaimType.LIQUIDITY }, "expected no LIQUIDITY, got $obs")
    }

    // ------------------------------------------------------------------
    // BUNDLING
    // ------------------------------------------------------------------

    @Test
    fun `T03 A10 - a loan mention with a requirement word is BUNDLING required`() {
        val b = of("இந்த policy எடுத்தா தான் loan sanction ஆகும் sir, இது compulsory.", ClaimType.BUNDLING)
        assertEquals(ClaimValue.Bundling(requiredForLoan = true), b.value)
    }

    @Test
    fun `a bare loan mention with no requirement or voluntary word produces no BUNDLING claim`() {
        val obs = extractor.extract(seg("Loan தான் sir, அது வேற topic."))
        assertTrue(obs.none { it.type == ClaimType.BUNDLING }, "expected no BUNDLING, got $obs")
    }

    // ------------------------------------------------------------------
    // CHARGES
    // ------------------------------------------------------------------

    @Test
    fun `T04 - zero commission and no charges both negate the CHARGES claim`() {
        val c = of("Charges எதுவும் இல்லை sir, zero commission.", ClaimType.CHARGES)
        assertEquals(ClaimValue.Charges(anyCharges = false, percent = null, label = null), c.value)
        assertNull(c.ambiguous)
    }

    // ------------------------------------------------------------------
    // Confidence composition
    // ------------------------------------------------------------------

    @Test
    fun `A9 - a noisy low-quality segment yields a low-confidence observation, still marked, not dropped`() {
        val g = of("Guaranteed", ClaimType.GUARANTEE, quality = 0.3)
        assertTrue(g.confidence <= 0.3, "confidence must be capped by the segment quality: was ${g.confidence}")
    }

    @Test
    fun `an empty segment produces no observations`() {
        assertEquals(emptyList<Any>(), extractor.extract(seg("   ")))
    }
}
