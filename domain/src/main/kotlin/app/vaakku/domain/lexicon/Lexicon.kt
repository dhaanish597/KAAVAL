package app.vaakku.domain.lexicon

import java.math.BigDecimal

/**
 * Query surface over a loaded [LexiconData] — build plan §5.3. Everything a
 * caller needs (number-word lookup, Tamil-digit conversion, fuzzy word-group
 * membership) lives here so `normalize/` and `extract/` never touch raw JSON.
 */
class Lexicon(val data: LexiconData) {

    /** One (surface form, value) pair for every numeric word the lexicon defines. */
    private val numberWordEntries: List<Pair<String, BigDecimal>> = buildList {
        fun addGroup(group: Map<String, List<String>>) {
            for ((value, variants) in group) {
                val bd = value.toBigDecimal()
                for (variant in variants) add(variant to bd)
            }
        }
        addGroup(data.numbersTa)
        addGroup(data.compoundNumbersTa)
        addGroup(data.fractionsTa)
        addGroup(data.halfCompoundsTa)
        addGroup(data.numbersEn)
        addGroup(data.enFractionWords)
    }

    private val exactNumberIndex: Map<String, BigDecimal> =
        numberWordEntries.associate { (variant, value) -> FuzzyMatcher.normalizeToken(variant) to value }

    val hedgeWords: List<String> get() = data.hedgeUpTo + data.hedgeIllustrative + data.hedgeGeneric

    /**
     * Resolves one number-word token to its value, exact match first, then
     * the best fuzzy match (§5.3: distance <= 1 for tokens of >= 5 code
     * points). Returns null rather than guessing when nothing matches.
     */
    fun lookupNumberWord(token: String): BigDecimal? {
        val norm = FuzzyMatcher.normalizeToken(token)
        exactNumberIndex[norm]?.let { return it }
        var best: BigDecimal? = null
        var bestQuality = 0.0
        for ((variant, value) in numberWordEntries) {
            val q = FuzzyMatcher.matchQuality(token, variant) ?: continue
            if (q > bestQuality) {
                bestQuality = q
                best = value
            }
        }
        return best
    }

    /**
     * Converts a token made entirely of Tamil numeral digits (optionally
     * with a decimal point), e.g. "௮%"'s digit run "௮", into a [BigDecimal].
     * Returns null if the token contains anything else.
     */
    fun tamilDigitsToNumber(token: String): BigDecimal? {
        if (token.isEmpty()) return null
        val sb = StringBuilder()
        for (ch in token) {
            when {
                ch == '.' -> sb.append('.')
                data.digitsTa.containsKey(ch.toString()) -> sb.append(data.digitsTa[ch.toString()])
                else -> return null
            }
        }
        return sb.toString().toBigDecimalOrNull()
    }

    /** Best fuzzy-match quality of [token] against every variant in [group], or null. */
    fun bestTokenMatch(token: String, group: Collection<String>): Double? =
        FuzzyMatcher.bestMatch(token, group)

    /** True if any phrase in [group] occurs (as a normalized substring) in [text]. */
    fun textContainsAny(text: String, group: Collection<String>): Boolean =
        group.any { FuzzyMatcher.containsPhrase(text, it) }
}
