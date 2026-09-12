package app.vaakku.domain.lexicon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The raw shape of `lexicon_ta_en.json` (build plan §5.3). This is a direct
 * mirror of the JSON file's keys — [Lexicon] is the class code actually uses.
 *
 * Two deliberate deviations from §5.3's group list, both documented at their
 * declaration site in the JSON file and restated in STATUS.md:
 *  - `hedge` is split into `hedge_up_to` / `hedge_illustrative` / `hedge_generic`
 *    so the spoken extractor can assign the right [app.vaakku.domain.model.RateQualifier]
 *    instead of a single undifferentiated "hedge" bucket.
 *  - `bundling_words` (loan/கடன்) is paired with a separate `bundling_required_words`
 *    group, because a BUNDLING claim needs both "loan" AND a requirement word
 *    nearby — §5.5's own anchor+window model applied to this claim type.
 */
@Serializable
data class LexiconData(
    @SerialName("numbers_ta") val numbersTa: Map<String, List<String>> = emptyMap(),
    @SerialName("compound_numbers_ta") val compoundNumbersTa: Map<String, List<String>> = emptyMap(),
    @SerialName("fractions_ta") val fractionsTa: Map<String, List<String>> = emptyMap(),
    @SerialName("half_compounds_ta") val halfCompoundsTa: Map<String, List<String>> = emptyMap(),
    @SerialName("digits_ta") val digitsTa: Map<String, String> = emptyMap(),
    @SerialName("numbers_en") val numbersEn: Map<String, List<String>> = emptyMap(),
    @SerialName("en_fraction_words") val enFractionWords: Map<String, List<String>> = emptyMap(),
    @SerialName("percent_markers") val percentMarkers: List<String> = emptyList(),
    @SerialName("year_units") val yearUnits: List<String> = emptyList(),
    @SerialName("month_units") val monthUnits: List<String> = emptyList(),
    @SerialName("after_markers") val afterMarkers: List<String> = emptyList(),
    @SerialName("guarantee_words") val guaranteeWords: List<String> = emptyList(),
    @SerialName("negation") val negation: List<String> = emptyList(),
    @SerialName("hedge_up_to") val hedgeUpTo: List<String> = emptyList(),
    @SerialName("hedge_illustrative") val hedgeIllustrative: List<String> = emptyList(),
    @SerialName("hedge_generic") val hedgeGeneric: List<String> = emptyList(),
    @SerialName("conditional") val conditional: List<String> = emptyList(),
    @SerialName("lockin_words") val lockinWords: List<String> = emptyList(),
    @SerialName("term_words") val termWords: List<String> = emptyList(),
    @SerialName("liquidity_words") val liquidityWords: List<String> = emptyList(),
    @SerialName("bundling_words") val bundlingWords: List<String> = emptyList(),
    @SerialName("bundling_required_words") val bundlingRequiredWords: List<String> = emptyList(),
    @SerialName("bundling_voluntary_words") val bundlingVoluntaryWords: List<String> = emptyList(),
    @SerialName("charges_words") val chargesWords: List<String> = emptyList(),
)
