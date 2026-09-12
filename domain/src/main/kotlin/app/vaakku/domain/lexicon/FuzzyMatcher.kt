package app.vaakku.domain.lexicon

import java.text.Normalizer

/**
 * Tamil–English fuzzy token matching — build plan §5.3.
 *
 * ASR output for code-switched speech is noisy in two specific ways this
 * object exists to absorb: (1) combining marks that render identically but
 * are not the same code points (NFC normalization), (2) stray zero-width
 * joiners/non-joiners some Tamil renderers/keyboards insert, and (3) a
 * single character substitution/insertion/deletion in a longer word ("இல்ல"
 * heard as "இல்ள").
 *
 * Quality bands are exactly the two named in §5.3: exact match = 0.95,
 * distance-1 match = 0.70.
 */
object FuzzyMatcher {

    private const val ZWNJ = '‌'
    private const val ZWJ = '‍'

    const val EXACT_QUALITY = 0.95
    const val FUZZY_QUALITY = 0.70

    /** NFC-normalize and strip ZWJ/ZWNJ. Does not change case. */
    fun normalize(s: String): String {
        val nfc = Normalizer.normalize(s, Normalizer.Form.NFC)
        return nfc.filter { it != ZWNJ && it != ZWJ }
    }

    /** [normalize] plus lower-casing (Tamil has no case, so this only affects Latin). */
    fun normalizeToken(s: String): String = normalize(s).lowercase()

    /**
     * Classic Levenshtein edit distance, computed over Unicode code points
     * (not UTF-16 chars) so that no Tamil grapheme is ever split mid-way
     * through a surrogate/combining sequence.
     */
    fun levenshtein(a: String, b: String): Int {
        val ca = a.codePoints().toArray()
        val cb = b.codePoints().toArray()
        if (ca.isEmpty()) return cb.size
        if (cb.isEmpty()) return ca.size
        var prev = IntArray(cb.size + 1) { it }
        var curr = IntArray(cb.size + 1)
        for (i in 1..ca.size) {
            curr[0] = i
            for (j in 1..cb.size) {
                val cost = if (ca[i - 1] == cb[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost,
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[cb.size]
    }

    private fun codePointLength(s: String): Int = s.codePointCount(0, s.length)

    /**
     * Compares one input [token] against one lexicon [candidate].
     *
     * @return [EXACT_QUALITY] on an exact normalized match, [FUZZY_QUALITY]
     *   if both normalized forms are at least 5 code points long and their
     *   edit distance is <= 1, or null for no match.
     */
    fun matchQuality(token: String, candidate: String): Double? {
        val a = normalizeToken(token)
        val b = normalizeToken(candidate)
        if (a.isEmpty() || b.isEmpty()) return null
        if (a == b) return EXACT_QUALITY
        if (codePointLength(a) >= 5 && codePointLength(b) >= 5 && levenshtein(a, b) <= 1) {
            return FUZZY_QUALITY
        }
        return null
    }

    /** The best quality [token] achieves against any of [candidates], or null. */
    fun bestMatch(token: String, candidates: Collection<String>): Double? =
        candidates.asSequence().mapNotNull { matchQuality(token, it) }.maxOrNull()

    /**
     * True if [text] contains [phrase] as a normalized substring. Used for
     * multi-word lexicon idioms ("up to", "எடுத்தா தான்") which are looked up
     * by containment on the joined text, not per-token fuzzy matching — §5.3
     * only specifies fuzzy matching for single tokens.
     */
    fun containsPhrase(text: String, phrase: String): Boolean =
        normalizeToken(text).contains(normalizeToken(phrase))
}
