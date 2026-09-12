package app.vaakku.domain.lexicon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LexiconLoaderTest {

    private val lexicon = LexiconLoader.loadDefault()

    @Test
    fun `default resource loads and every §5_3 group is non-empty`() {
        val d = lexicon.data
        assertTrue(d.numbersTa.isNotEmpty())
        assertTrue(d.fractionsTa.isNotEmpty())
        assertTrue(d.digitsTa.isNotEmpty())
        assertTrue(d.numbersEn.isNotEmpty())
        assertTrue(d.percentMarkers.isNotEmpty())
        assertTrue(d.yearUnits.isNotEmpty())
        assertTrue(d.monthUnits.isNotEmpty())
        assertTrue(d.afterMarkers.isNotEmpty())
        assertTrue(d.guaranteeWords.isNotEmpty())
        assertTrue(d.negation.isNotEmpty())
        assertTrue(lexicon.hedgeWords.isNotEmpty())
        assertTrue(d.conditional.isNotEmpty())
        assertTrue(d.lockinWords.isNotEmpty())
        assertTrue(d.liquidityWords.isNotEmpty())
        assertTrue(d.bundlingWords.isNotEmpty())
        assertTrue(d.chargesWords.isNotEmpty())
    }

    @Test
    fun `Tamil number words resolve to the right value`() {
        assertEquals(BigDecimal("8"), lexicon.lookupNumberWord("எட்டு"))
        assertEquals(BigDecimal("1"), lexicon.lookupNumberWord("ஒரு"))
        assertEquals(BigDecimal("1"), lexicon.lookupNumberWord("ஒண்ணு"))
        assertEquals(BigDecimal("5"), lexicon.lookupNumberWord("அஞ்சு"))
        assertEquals(BigDecimal("10"), lexicon.lookupNumberWord("பத்து"))
        assertEquals(BigDecimal("100000"), lexicon.lookupNumberWord("லட்சம்"))
        assertEquals(BigDecimal("10000000"), lexicon.lookupNumberWord("கோடி"))
    }

    @Test
    fun `English number words resolve to the right value`() {
        assertEquals(BigDecimal("8"), lexicon.lookupNumberWord("eight"))
        assertEquals(BigDecimal("1"), lexicon.lookupNumberWord("one"))
        assertEquals(BigDecimal("100000"), lexicon.lookupNumberWord("lakh"))
    }

    @Test
    fun `fraction and half-compound words resolve correctly`() {
        assertEquals(BigDecimal("0.5"), lexicon.lookupNumberWord("அரை"))
        assertEquals(BigDecimal("0.25"), lexicon.lookupNumberWord("கால்"))
        assertEquals(BigDecimal("8.5"), lexicon.lookupNumberWord("எட்டரை"))
        assertEquals(BigDecimal("4.5"), lexicon.lookupNumberWord("நாலரை"))
    }

    @Test
    fun `compound thousands used for lakh-plus-thousands phrases resolve correctly`() {
        assertEquals(BigDecimal("20000"), lexicon.lookupNumberWord("இருபதாயிரம்"))
        assertEquals(BigDecimal("10000"), lexicon.lookupNumberWord("பத்தாயிரம்"))
    }

    @Test
    fun `an unknown token resolves to null, never a guess`() {
        assertNull(lexicon.lookupNumberWord("வாழைப்பழம்")) // "banana" — not a number word
    }

    @Test
    fun `Tamil digit characters convert to Arabic digits`() {
        assertEquals(BigDecimal("8"), lexicon.tamilDigitsToNumber("௮"))
        assertEquals(BigDecimal("12"), lexicon.tamilDigitsToNumber("௧௨"))
        assertEquals(BigDecimal("8.5"), lexicon.tamilDigitsToNumber("௮.௫"))
        assertNull(lexicon.tamilDigitsToNumber("௮x")) // not a pure digit run
        assertNull(lexicon.tamilDigitsToNumber(""))
    }

    @Test
    fun `guarantee and negation word groups contain the documented ASR-Tamil-script spellings`() {
        // §5.3's "important insight": ASR often writes English loanwords in
        // Tamil script. The lexicon must carry those spellings, not just the
        // Latin ones.
        assertTrue(lexicon.data.guaranteeWords.contains("கேரண்டி"))
        assertTrue(lexicon.data.guaranteeWords.contains("உறுதியான"))
        assertTrue(lexicon.data.negation.contains("இல்லை"))
    }

    @Test
    fun `bestTokenMatch and textContainsAny expose fuzzy and phrase matching to callers`() {
        assertEquals(FuzzyMatcher.EXACT_QUALITY, lexicon.bestTokenMatch("guaranteed", lexicon.data.guaranteeWords))
        assertTrue(lexicon.textContainsAny("up to எட்டு percent", lexicon.data.hedgeUpTo))
    }

    @Test
    fun `loading malformed JSON text fails loudly rather than silently returning an empty lexicon`() {
        org.junit.jupiter.api.assertThrows<Exception> {
            LexiconLoader.load("{ not json")
        }
    }
}
