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
 */
object Tokenizer {

    private val TOKEN_REGEX = Regex(
        "[\\p{L}\\p{M}]+(?:[-'][\\p{L}\\p{M}]+)*" + // words, incl. hyphenated ("lock-in")
            "|[\\u0BE6-\\u0BEF]+(?:\\.[\\u0BE6-\\u0BEF]+)?" + // Tamil numeral digit runs
            "|\\d+(?:\\.\\d+)?%?", // Arabic numbers, optionally with a glued '%'
    )

    fun tokenize(text: String): List<String> = TOKEN_REGEX.findAll(text).map { it.value }.toList()
}
