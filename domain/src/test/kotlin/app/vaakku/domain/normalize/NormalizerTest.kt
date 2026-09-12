package app.vaakku.domain.normalize

import app.vaakku.domain.lexicon.LexiconLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.util.stream.Stream

/**
 * Build plan §5.4 — "Pure functions with exhaustive unit tests." >= 40 cases
 * across parseNumber / parsePercent / parseDurationMonths / disambiguation,
 * including every example named in the P1 prompt and the two traps
 * ("ஒரு" = one/a, "ஆறு" = six/river — a number word only counts with a unit
 * within 2 tokens, §5.3).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NormalizerTest {

    private val normalizer = Normalizer(LexiconLoader.loadDefault())

    // ------------------------------------------------------------------
    // parseNumber
    // ------------------------------------------------------------------

    fun parseNumberCases(): Stream<Arguments> = Stream.of(
        // Tamil digit words 0-10 and a few above
        Arguments.of("zero (ta)", listOf("பூஜ்ஜியம்"), BigDecimal("0")),
        Arguments.of("one (ta, ஒரு)", listOf("ஒரு"), BigDecimal("1")),
        Arguments.of("one (ta, ஒண்ணு)", listOf("ஒண்ணு"), BigDecimal("1")),
        Arguments.of("two (ta)", listOf("இரண்டு"), BigDecimal("2")),
        Arguments.of("three (ta)", listOf("மூன்று"), BigDecimal("3")),
        Arguments.of("four (ta)", listOf("நான்கு"), BigDecimal("4")),
        Arguments.of("five (ta)", listOf("ஐந்து"), BigDecimal("5")),
        Arguments.of("five (ta, colloquial அஞ்சு)", listOf("அஞ்சு"), BigDecimal("5")),
        Arguments.of("six (ta)", listOf("ஆறு"), BigDecimal("6")),
        Arguments.of("seven (ta)", listOf("ஏழு"), BigDecimal("7")),
        Arguments.of("eight (ta)", listOf("எட்டு"), BigDecimal("8")),
        Arguments.of("nine (ta)", listOf("ஒன்பது"), BigDecimal("9")),
        Arguments.of("ten (ta)", listOf("பத்து"), BigDecimal("10")),
        Arguments.of("twelve (ta)", listOf("பன்னிரண்டு"), BigDecimal("12")),
        Arguments.of("twenty (ta)", listOf("இருபது"), BigDecimal("20")),
        Arguments.of("thirty (ta)", listOf("முப்பது"), BigDecimal("30")),
        Arguments.of("fifty (ta)", listOf("ஐம்பது"), BigDecimal("50")),
        Arguments.of("hundred (ta)", listOf("நூறு"), BigDecimal("100")),
        Arguments.of("thousand (ta)", listOf("ஆயிரம்"), BigDecimal("1000")),
        Arguments.of("lakh (ta)", listOf("லட்சம்"), BigDecimal("100000")),
        Arguments.of("crore (ta)", listOf("கோடி"), BigDecimal("10000000")),
        // Fractions and half-compounds — §5.3, incl. the two named examples
        Arguments.of("half", listOf("அரை"), BigDecimal("0.5")),
        Arguments.of("quarter", listOf("கால்"), BigDecimal("0.25")),
        Arguments.of("three-quarters", listOf("முக்கால்"), BigDecimal("0.75")),
        Arguments.of("eight and a half (எட்டரை)", listOf("எட்டரை"), BigDecimal("8.5")),
        Arguments.of("four and a half (நாலரை)", listOf("நாலரை"), BigDecimal("4.5")),
        // Compound lakh phrase — the named §5.4 example
        Arguments.of(
            "ஒரு லட்சம் இருபதாயிரம் -> 120000",
            listOf("ஒரு", "லட்சம்", "இருபதாயிரம்"),
            BigDecimal("120000"),
        ),
        Arguments.of("இரண்டு லட்சம் -> 200000", listOf("இரண்டு", "லட்சம்"), BigDecimal("200000")),
        // English number words
        Arguments.of("one (en)", listOf("one"), BigDecimal("1")),
        Arguments.of("eight (en)", listOf("eight"), BigDecimal("8")),
        Arguments.of("twenty (en)", listOf("twenty"), BigDecimal("20")),
        Arguments.of("hundred (en)", listOf("hundred"), BigDecimal("100")),
        Arguments.of("thousand (en)", listOf("thousand"), BigDecimal("1000")),
        Arguments.of("lakh (en)", listOf("lakh"), BigDecimal("100000")),
        // Digits: Arabic and Tamil numerals
        Arguments.of("arabic digit", listOf("8"), BigDecimal("8")),
        Arguments.of("arabic decimal", listOf("4.5"), BigDecimal("4.5")),
        Arguments.of("tamil numeral digit", listOf("௮"), BigDecimal("8")),
        Arguments.of("tamil numeral two-digit", listOf("௧௨"), BigDecimal("12")),
        Arguments.of("number with glued percent still resolves", listOf("8%"), BigDecimal("8")),
        // Never guess
        Arguments.of("unknown word -> null", listOf("வாழைப்பழம்"), null),
        Arguments.of("one unresolvable token fails the whole phrase", listOf("ஒரு", "ரூபாய்"), null),
        Arguments.of("empty token list -> null", emptyList<String>(), null),
    )

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("parseNumberCases")
    fun `parseNumber`(label: String, tokens: List<String>, expected: BigDecimal?) {
        assertEquals(expected, normalizer.parseNumber(tokens))
    }

    // ------------------------------------------------------------------
    // parsePercent
    // ------------------------------------------------------------------

    fun parsePercentCases(): Stream<Arguments> = Stream.of(
        Arguments.of("எட்டு சதவீதம் -> 8", listOf("எட்டு", "சதவீதம்"), BigDecimal("8")),
        Arguments.of("eight percent -> 8", listOf("eight", "percent"), BigDecimal("8")),
        Arguments.of("8% -> 8", listOf("8%"), BigDecimal("8")),
        Arguments.of("நாலு percent -> 4", listOf("நாலு", "percent"), BigDecimal("4")),
        Arguments.of("4.5% -> 4.5", listOf("4.5%"), BigDecimal("4.5")),
        Arguments.of(
            "marker embedded in a longer window",
            listOf("இது", "எட்டு", "சதவீதம்", "தரும்"),
            BigDecimal("8"),
        ),
        Arguments.of(
            "hedge words around the number do not block extraction",
            listOf("up", "to", "எட்டு", "percent"),
            BigDecimal("8"),
        ),
        Arguments.of("\"ஒரு\" trap: no percent marker at all -> null", listOf("ஒரு"), null),
        Arguments.of("\"ஆறு\" trap: no percent marker at all -> null", listOf("ஆறு"), null),
        Arguments.of("no marker anywhere in the window -> null", listOf("guaranteed", "இல்லை"), null),
    )

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("parsePercentCases")
    fun `parsePercent`(label: String, window: List<String>, expected: BigDecimal?) {
        assertEquals(expected, normalizer.parsePercent(window))
    }

    // ------------------------------------------------------------------
    // parseDurationMonths
    // ------------------------------------------------------------------

    fun parseDurationMonthsCases(): Stream<Arguments> = Stream.of(
        Arguments.of("அஞ்சு வருஷம் -> 60", listOf("அஞ்சு", "வருஷம்"), 60),
        Arguments.of("18 months -> 18", listOf("18", "months"), 18),
        Arguments.of("ஒரு வருஷம் -> 12", listOf("ஒரு", "வருஷம்"), 12),
        Arguments.of("மூன்று மாதம் -> 3", listOf("மூன்று", "மாதம்"), 3),
        Arguments.of("பத்து வருஷம் -> 120", listOf("பத்து", "வருஷம்"), 120),
        Arguments.of("five years -> 60", listOf("five", "years"), 60),
        Arguments.of(
            "unit still resolved inside a longer window",
            listOf("lock-in", "ஒரு", "வருஷம்", "தான்"),
            12,
        ),
        Arguments.of(
            "ஆறு with a month unit present legitimately resolves to 6",
            listOf("ஆறு", "மாதம்"),
            6,
        ),
        Arguments.of("\"ஒரு\" trap: no year/month unit at all -> null", listOf("ஒரு"), null),
        Arguments.of("\"ஆறு\" trap: no year/month unit at all -> null", listOf("ஆறு"), null),
    )

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("parseDurationMonthsCases")
    fun `parseDurationMonths`(label: String, window: List<String>, expected: Int?) {
        assertEquals(expected, normalizer.parseDurationMonths(window))
    }

    // ------------------------------------------------------------------
    // disambiguateDuration — §5.4's three named examples
    // ------------------------------------------------------------------

    @Test
    fun `one year lock-in disambiguates to LockIn`() {
        val result = normalizer.disambiguateDuration(Tokenizer.tokenize("one year lock-in"))
        assertEquals(DurationDisambiguation.LockIn(12), result)
    }

    @Test
    fun `one year கழிச்சு எடுக்கலாம் disambiguates to WithdrawableAfter`() {
        val result = normalizer.disambiguateDuration(Tokenizer.tokenize("one year கழிச்சு எடுக்கலாம்"))
        assertEquals(DurationDisambiguation.WithdrawableAfter(12), result)
    }

    @Test
    fun `policy term பத்து வருஷம் disambiguates to no claim`() {
        val result = normalizer.disambiguateDuration(Tokenizer.tokenize("policy term பத்து வருஷம்"))
        assertEquals(DurationDisambiguation.NoClaim, result)
    }

    @Test
    fun `a duration with no lock-in, term or liquidity signal is ambiguous, not guessed`() {
        // A bare "five years" with none of the disambiguating words nearby:
        // the function must not silently default to LOCK_IN.
        val result = normalizer.disambiguateDuration(Tokenizer.tokenize("five years"))
        assertNull(result)
    }

    // ------------------------------------------------------------------
    // Tokenizer — used throughout the above via Tokenizer.tokenize
    // ------------------------------------------------------------------

    @Test
    fun `tokenizer keeps a Tamil consonant cluster as one token`() {
        assertEquals(listOf("எட்டு", "சதவீதம்"), Tokenizer.tokenize("எட்டு சதவீதம்"))
    }

    @Test
    fun `tokenizer keeps a percent sign glued to its number`() {
        assertEquals(listOf("Sir", "8%", "return"), Tokenizer.tokenize("Sir, 8% return!"))
    }

    @Test
    fun `tokenizer splits on a hyphen (punctuation), it does not glue compounds`() {
        // §5.5 step 1: split on whitespace AND punctuation. "lock-in" becomes
        // two tokens; extract/ matches the lexicon's "lock in" phrase variant
        // by joining adjacent tokens, not by asking the tokenizer to fuse them.
        assertEquals(listOf("lock", "in", "ஒரு", "வருஷம்"), Tokenizer.tokenize("lock-in ஒரு வருஷம்"))
        assertEquals(listOf("Guaranteed", "ஆ", "இருந்தா"), Tokenizer.tokenize("Guaranteed-ஆ இருந்தா"))
    }

    @Test
    fun `tokenizer drops punctuation as a delimiter`() {
        assertEquals(listOf("Guaranteed", "eight", "percent", "return"), Tokenizer.tokenize("Guaranteed... eight percent return."))
    }
}
