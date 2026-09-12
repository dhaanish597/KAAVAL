package app.vaakku.domain.copy

import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.RateQualifier
import java.math.BigDecimal

/**
 * Maps a [ClaimValue] to the build plan §8.3 value-phrase key + args. Two
 * resolved gaps in §8.3's table, both because it is not fully orthogonal to
 * the six claim types / [RateQualifier] values:
 *  - `v_guaranteed_pct` ("உறுதியான {x}%") pairs GUARANTEE=true with a
 *    same-utterance rate ("Guaranteed எட்டு percent"). When no rate
 *    co-occurs, [forGuarantee] falls back to a new `v_guaranteed` key in the
 *    same phrase family (plain, no args) rather than forcing a fake percent.
 *  - `v_illustrative_pcts` is the only percent-list phrase §8.3 defines; it
 *    is used for every [RateQualifier], not only ILLUSTRATIVE — the list of
 *    percents is the content that matters on a Delta Card, and the
 *    qualifier itself is never shown as a separate word (CLAUDE.md #1: no
 *    judgement language like "hedged" or "asserted" on screen).
 */
object ValuePhrase {

    fun forGuarantee(value: ClaimValue.Guarantee, coStatedRatePercent: BigDecimal? = null): CopyRef =
        when {
            !value.guaranteed -> CopyRef("v_not_guaranteed")
            coStatedRatePercent != null -> CopyRef("v_guaranteed_pct", listOf(formatNumber(coStatedRatePercent)))
            else -> CopyRef("v_guaranteed")
        }

    fun forRate(value: ClaimValue.Rate): CopyRef =
        CopyRef("v_illustrative_pcts", listOf(formatPercentList(value.percents)))

    /** Prefers whole years when [ClaimValue.LockIn.months] divides evenly by 12, else months. */
    fun forLockIn(value: ClaimValue.LockIn): CopyRef =
        if (value.months % 12 == 0) CopyRef("v_years", listOf((value.months / 12).toString()))
        else CopyRef("v_months", listOf(value.months.toString()))

    /**
     * §8.3 only gives a years-denominated phrase for both liquidity value
     * phrases; months are converted to whole years (our fixtures/labels
     * only ever produce exact-year durations for these two fields).
     */
    fun forLiquidity(value: ClaimValue.Liquidity): CopyRef = when {
        value.withdrawableAfterMonths != null -> CopyRef("v_withdraw_after", listOf((value.withdrawableAfterMonths / 12).toString()))
        value.surrenderNilBeforeMonths != null -> CopyRef("v_surrender_nil_before", listOf((value.surrenderNilBeforeMonths / 12).toString()))
        else -> CopyRef("v_liquidity_unspecified")
    }

    fun forBundling(value: ClaimValue.Bundling): CopyRef =
        if (value.requiredForLoan) CopyRef("v_required_for_loan") else CopyRef("v_voluntary")

    fun forCharges(value: ClaimValue.Charges): CopyRef = when {
        !value.anyCharges -> CopyRef("v_no_charges")
        value.percent != null -> CopyRef("v_charge_pct", listOf(formatNumber(value.percent)))
        else -> CopyRef("v_charge_present")
    }

    fun forAny(value: ClaimValue): CopyRef = when (value) {
        is ClaimValue.Guarantee -> forGuarantee(value)
        is ClaimValue.Rate -> forRate(value)
        is ClaimValue.LockIn -> forLockIn(value)
        is ClaimValue.Liquidity -> forLiquidity(value)
        is ClaimValue.Bundling -> forBundling(value)
        is ClaimValue.Charges -> forCharges(value)
    }

    /** Trims a trailing ".0" (8.0 -> "8") but keeps a real fraction (4.5 -> "4.5"). */
    fun formatNumber(n: BigDecimal): String = n.stripTrailingZeros().let {
        if (it.scale() < 0) it.setScale(0) else it
    }.toPlainString()

    /** Sorted, comma-joined, for the "{list}%" slot ("4, 8"). */
    fun formatPercentList(percents: Set<BigDecimal>): String =
        percents.sorted().joinToString(", ") { formatNumber(it) }
}
