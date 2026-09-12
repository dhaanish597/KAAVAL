package app.vaakku.domain.normalize

/**
 * Splits raw ASR/OCR text into tokens — build plan §5.5 step 1 ("Tokenize
 * (split on whitespace and punctuation, keep % attached handling)"). Shared
 * by the normalizer's own tests and by `extract/` so both operate on the
 * same notion of a token.
 *
 * A "word" token is one run of Unicode letters plus combining marks, so a
 * Tamil consonant cluster (base consonant + virama + base consonant + vowel
 * sign, e.g. எட்டு) stays one token instead of splitting mid-grapheme. A
 * number token keeps a trailing "%" glued on ("8%" stays one token, not
 * "8" + "%"), and Tamil numeral digits are tokenized the same way as Arabic
 * ones.
 *
 * Hyphens ARE a split point (§5.5 step 1 says split on punctuation), which
 * matters: the labels.json script T09 contains "Guaranteed-ஆ" (an English
 * loanword with a Tamil interrogative-particle suffix hyphenated on). If the
 * hyphen were kept, that token would no longer fuzzy-match "guaranteed"
 * (edit distance 2, over the §5.3 distance-1 budget) and the GUARANTEE
 * anchor would be missed entirely. Splitting it into "Guaranteed" + "ஆ"
 * keeps the anchor exact-matchable, and multi-word lexicon idioms like
 * "lock-in" / "lock in" are matched at the phrase level (extract/ builds a
 * space-joined 2-token phrase and checks it against the lexicon), not by
 * requiring the tokenizer to glue them back together.
 */
object Tokenizer {

    private val TOKEN_REGEX = Regex(
        "[\\p{L}\\p{M}]+" + // words (Tamil consonant clusters stay one token)
            "|[\\u0BE6-\\u0BEF]+(?:\\.[\\u0BE6-\\u0BEF]+)?" + // Tamil numeral digit runs
            "|\\d+(?:\\.\\d+)?%?", // Arabic numbers, optionally with a glued '%'
    )

    fun tokenize(text: String): List<String> = TOKEN_REGEX.findAll(text).map { it.value }.toList()
}
