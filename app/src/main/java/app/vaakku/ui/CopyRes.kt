package app.vaakku.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.vaakku.BuildConfig
import app.vaakku.R
import app.vaakku.domain.copy.CopyRef
import app.vaakku.domain.copy.LineRef

/**
 * The one bridge from the domain's copy keys to the words a buyer reads —
 * build plan §5.8.
 *
 * ### Why the domain does not just return a string
 *
 * `:domain` emits [CopyRef]`(key, args)` and never prose. That is not stylistic:
 * it makes it *structurally impossible* for a banned word (CLAUDE.md #1) to
 * reach the screen through the reconciler, because there is no sentence in that
 * module for one to hide in. All user-visible wording lives in `strings.xml`,
 * which `checkBannedWords` scans. This object is the only place the two meet,
 * so it is the only place that has to be audited for the join.
 *
 * ### The table is the contract
 *
 * [TABLE] maps every key the domain can emit to its Tamil resource, its English
 * companion, and how many arguments the template expects. `CopyResTest` drives
 * `CopyBuilder` and `ValuePhrase` over every claim type and value shape and
 * fails the build if a key is missing or its arity disagrees — so a key renamed
 * on either side is a red test, not a blank line in front of a buyer.
 *
 * ### What an unresolvable key does
 *
 * Debug builds throw: a missing key is a programming error and the loudest
 * possible failure is the cheapest one to fix. Release builds return the empty
 * string — CLAUDE.md #2, the default failure is silence. Showing a buyer
 * `card_differs_footer` in raw ASCII would be worse than showing them nothing,
 * and guessing at a replacement would be worse still.
 */
object CopyRes {

    /**
     * One row of the contract. [ta] and [en] are both resource ids for the same
     * key — the app's language row picks between them (see [localized]). [args]
     * is the number of `%n$s` placeholders the template carries, checked against
     * `CopyRef.args` at resolution time. [plural] rows live in `R.plurals` and
     * are resolved with a quantity instead of a plain lookup.
     */
    private class Entry(
        val ta: Int,
        val en: Int,
        val args: Int,
        val plural: Boolean = false,
    )

    private val TABLE: Map<String, Entry> = mapOf(
        // --- state labels (build plan §8.2) -----------------------------------
        // state_silent is PENDING/UNCERTAIN rendered as an em dash. It exists so
        // the Details ledger can draw a row per claim type without ever needing
        // a word for "we are not saying anything".
        "state_matches" to Entry(R.string.state_matches, R.string.state_matches_en, 0),
        "state_not_in_document" to Entry(R.string.state_not_in_document, R.string.state_not_in_document_en, 0),
        "state_differs" to Entry(R.string.state_differs, R.string.state_differs_en, 0),
        "state_silent" to Entry(R.string.state_silent, R.string.state_silent_en, 0),

        // --- card templates (§8.2) --------------------------------------------
        "card_differs_spoken_line" to Entry(R.string.card_differs_spoken_line, R.string.card_differs_spoken_line_en, 1),
        "card_differs_written_line" to Entry(R.string.card_differs_written_line, R.string.card_differs_written_line_en, 1),
        "card_differs_footer" to Entry(R.string.card_differs_footer, R.string.card_differs_footer_en, 0),
        // English in both modes on purpose — it *is* the English gloss under the
        // Tamil pair. Two args, and unlike every other key here they are supplied
        // by the card rather than by the domain: the domain hands over one value
        // phrase per line, and the small line puts both on one row.
        "card_differs_english_small_line" to Entry(
            R.string.card_differs_english_small_line,
            R.string.card_differs_english_small_line,
            2,
        ),
        "card_not_in_document_spoken_line" to Entry(
            R.string.card_not_in_document_spoken_line,
            R.string.card_not_in_document_spoken_line_en,
            1,
        ),
        "card_not_in_document_hint_line" to Entry(
            R.string.card_not_in_document_hint_line,
            R.string.card_not_in_document_hint_line_en,
            0,
        ),

        // --- value phrases (§8.3) ---------------------------------------------
        "v_guaranteed_pct" to Entry(R.string.v_guaranteed_pct, R.string.v_guaranteed_pct_en, 1),
        "v_guaranteed" to Entry(R.string.v_guaranteed, R.string.v_guaranteed_en, 0),
        "v_not_guaranteed" to Entry(R.string.v_not_guaranteed, R.string.v_not_guaranteed_en, 0),
        "v_illustrative_pcts" to Entry(R.string.v_illustrative_pcts, R.string.v_illustrative_pcts_en, 1),
        "v_years" to Entry(R.plurals.v_years, R.plurals.v_years_en, 1, plural = true),
        "v_months" to Entry(R.plurals.v_months, R.plurals.v_months_en, 1, plural = true),
        "v_withdraw_after" to Entry(R.plurals.v_withdraw_after, R.plurals.v_withdraw_after_en, 1, plural = true),
        "v_surrender_nil_before" to Entry(
            R.plurals.v_surrender_nil_before,
            R.plurals.v_surrender_nil_before_en,
            1,
            plural = true,
        ),
        "v_liquidity_unspecified" to Entry(R.string.v_liquidity_unspecified, R.string.v_liquidity_unspecified_en, 0),
        "v_required_for_loan" to Entry(R.string.v_required_for_loan, R.string.v_required_for_loan_en, 0),
        "v_voluntary" to Entry(R.string.v_voluntary, R.string.v_voluntary_en, 0),
        "v_no_charges" to Entry(R.string.v_no_charges, R.string.v_no_charges_en, 0),
        "v_charge_pct" to Entry(R.string.v_charge_pct, R.string.v_charge_pct_en, 1),
        "v_charge_present" to Entry(R.string.v_charge_present, R.string.v_charge_present_en, 0),

        // --- follow-up questions (§8.4) ---------------------------------------
        "followup_guarantee" to Entry(R.string.followup_guarantee, R.string.followup_guarantee_en, 0),
        "followup_lockin_liquidity" to Entry(
            R.string.followup_lockin_liquidity,
            R.string.followup_lockin_liquidity_en,
            0,
        ),
        "followup_bundling" to Entry(R.string.followup_bundling, R.string.followup_bundling_en, 0),
        "followup_rate_charges" to Entry(R.string.followup_rate_charges, R.string.followup_rate_charges_en, 0),
    )

    /** Every key this object can resolve. For tests and the debug overlay. */
    fun keys(): Set<String> = TABLE.keys

    /** How many arguments [key]'s template expects, or null if the key is unknown. */
    fun argCount(key: String): Int? = TABLE[key]?.args

    /** Resolves a domain [CopyRef]. */
    fun resolve(resources: Resources, language: AppLanguage, ref: CopyRef): String =
        resolve(resources, language, ref.key, ref.args)

    /**
     * Resolves a [LineRef]: the value phrase first, then the line template that
     * quotes it. Two lookups rather than one because the domain deliberately
     * keeps them apart — the same value phrase appears on a card, in the Details
     * ledger and in the receipt, under three different templates.
     */
    fun resolve(resources: Resources, language: AppLanguage, line: LineRef): String =
        resolve(resources, language, line.templateKey, listOf(resolve(resources, language, line.value)))

    fun resolve(
        resources: Resources,
        language: AppLanguage,
        key: String,
        args: List<String> = emptyList(),
    ): String {
        val entry = TABLE[key] ?: return missing("no strings.xml entry for copy key '$key'")
        if (args.size != entry.args) {
            return missing("copy key '$key' expects ${entry.args} argument(s), got ${args.size}")
        }
        val id = if (language == AppLanguage.ENGLISH_ONLY) entry.en else entry.ta

        @Suppress("SpreadOperator") // Resources' own varargs signature; the arrays are 0-2 long.
        return if (entry.plural) {
            // The quantity and the substituted text come from the same argument:
            // "5" picks the `other` form AND fills the placeholder. A value that
            // is not a whole number cannot pick a bucket, so it takes `other`,
            // which is the generic form in both languages — the number itself is
            // still printed exactly as the domain produced it.
            resources.getQuantityString(id, args[0].toIntOrNull() ?: OTHER_QUANTITY, *args.toTypedArray())
        } else if (entry.args == 0) {
            resources.getString(id)
        } else {
            resources.getString(id, *args.toTypedArray())
        }
    }

    /**
     * Debug builds throw so the Dev menu and the unit tests surface the break at
     * once; release builds fall silent rather than print an identifier at a buyer
     * (CLAUDE.md #2).
     */
    private fun missing(message: String): String {
        if (BuildConfig.DEBUG) error(message)
        return ""
    }

    /** CLDR's catch-all bucket; any count that is not exactly one lands here in both ta and en. */
    private const val OTHER_QUANTITY = 2
}

/** [CopyRes.resolve] against the current language. The form a composable should use. */
@Composable
fun copyText(ref: CopyRef): String =
    CopyRes.resolve(LocalContext.current.resources, LocalAppLanguage.current.value, ref)

/** [CopyRes.resolve] for a [LineRef] — value phrase, then line template. */
@Composable
fun copyText(line: LineRef): String =
    CopyRes.resolve(LocalContext.current.resources, LocalAppLanguage.current.value, line)

/** [CopyRes.resolve] for a bare key the app itself holds, such as a card's footer or hint. */
@Composable
fun copyText(key: String, vararg args: String): String =
    CopyRes.resolve(LocalContext.current.resources, LocalAppLanguage.current.value, key, args.toList())

/**
 * The English rendering of a value phrase regardless of the current language —
 * the two halves of `card_differs_english_small_line`, which stays English in
 * both modes.
 */
@Composable
fun englishCopyText(ref: CopyRef): String =
    CopyRes.resolve(LocalContext.current.resources, AppLanguage.ENGLISH_ONLY, ref)
