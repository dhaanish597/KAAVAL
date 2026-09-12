package app.vaakku.domain.lexicon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FuzzyMatcherTest {

    @Test
    fun `exact match after NFC normalization scores 0_95`() {
        // "cafe" with "e" + combining acute accent (U+0301) vs the single
        // precomposed e-acute (U+00E9) are canonically equivalent text that
        // different keyboards or ASR post-processors can legitimately emit
        // either way; NFC must fold both to the same comparison key. Built
        // from explicit \\u escapes so the two encodings are unambiguous.
        val decomposed = "café"
        val precomposed = "café"
        assertEquals(decomposed.length, 5) // proof the two inputs really do differ before normalizing
        assertEquals(precomposed.length, 4)
        assertEquals(FuzzyMatcher.EXACT_QUALITY, FuzzyMatcher.matchQuality(decomposed, precomposed))
    }

    @Test
    fun `exact match on plain equal tokens scores 0_95`() {
        assertEquals(FuzzyMatcher.EXACT_QUALITY, FuzzyMatcher.matchQuality("guaranteed", "guaranteed"))
        assertEquals(FuzzyMatcher.EXACT_QUALITY, FuzzyMatcher.matchQuality("GUARANTEED", "guaranteed"))
    }

    @Test
    fun `ZWJ and ZWNJ are stripped before comparison`() {
        // U+200C (ZWNJ) inserted mid-word must not block a match against the
        // same word without it.
        val withZwnj = "இல்ல‌ை"
        val clean = "இல்லை"
        assertEquals(FuzzyMatcher.EXACT_QUALITY, FuzzyMatcher.matchQuality(withZwnj, clean))
    }

    @Test
    fun `distance-1 match on a token of 5+ code points scores 0_70`() {
        // "guaranteed" (10 letters) vs "guarenteed" (one substitution)
        assertEquals(FuzzyMatcher.FUZZY_QUALITY, FuzzyMatcher.matchQuality("guarenteed", "guaranteed"))
    }

    @Test
    fun `distance-1 on a short (under 5 code point) token does not match`() {
        // "not" vs "nob" is distance 1 at length 3 — must NOT match per the
        // ">= 5 code point" rule, so short words are never fuzzily conflated.
        assertNull(FuzzyMatcher.matchQuality("not", "nob"))
    }

    @Test
    fun `distance-2 does not match regardless of length`() {
        assertNull(FuzzyMatcher.matchQuality("guaranteed", "guarontood"))
    }

    @Test
    fun `unrelated words do not match`() {
        assertNull(FuzzyMatcher.matchQuality("guaranteed", "இல்லை"))
    }

    @Test
    fun `levenshtein handles insertion and deletion, not just substitution`() {
        assertEquals(1, FuzzyMatcher.levenshtein("guaranted", "guaranteed")) // deletion
        assertEquals(1, FuzzyMatcher.levenshtein("guaranteeed", "guaranteed")) // insertion
        assertEquals(0, FuzzyMatcher.levenshtein("guaranteed", "guaranteed"))
    }

    @Test
    fun `containsPhrase is a normalized substring check for multi-word idioms`() {
        // "எட்டு" (eight) embedded in a longer phrase around "up to".
        val eight = "எட்டு"
        assertEquals(true, FuzzyMatcher.containsPhrase("up to $eight percent", "up to"))
        assertEquals(false, FuzzyMatcher.containsPhrase("guaranteed eight percent", "up to"))
    }

    @Test
    fun `bestMatch returns the highest quality across candidates`() {
        val candidates = listOf("guaranteed", "assured", "fixed")
        assertEquals(FuzzyMatcher.EXACT_QUALITY, FuzzyMatcher.bestMatch("guaranteed", candidates))
        assertEquals(FuzzyMatcher.FUZZY_QUALITY, FuzzyMatcher.bestMatch("guarenteed", candidates))
        assertNull(FuzzyMatcher.bestMatch("banana", candidates))
    }
}
