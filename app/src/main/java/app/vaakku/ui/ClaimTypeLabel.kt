package app.vaakku.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.vaakku.R
import app.vaakku.domain.model.ClaimType

/**
 * The reader's name for each of the six claim types (§2.1).
 *
 * This lives in the app rather than in `domain/`, and not by accident: the domain
 * emits [CopyRef][app.vaakku.domain.copy.CopyRef] keys for *values* and *states*
 * but has no opinion about how a claim type is titled, because a title is prose
 * and the domain holds no prose (§5.8). A `when` over the enum here means adding a
 * seventh claim type breaks the build at this table instead of silently rendering
 * an enum constant name at a buyer.
 *
 * These are nouns for a subject — "lock-in period" — never a characterisation.
 * What the app has to say *about* a claim is a separate label and comes from the
 * reconciler alone.
 */
@StringRes
fun claimTypeLabelRes(type: ClaimType): Int = when (type) {
    ClaimType.RETURN_RATE -> R.string.claim_return_rate
    ClaimType.GUARANTEE -> R.string.claim_guarantee
    ClaimType.LOCK_IN -> R.string.claim_lock_in
    ClaimType.LIQUIDITY -> R.string.claim_liquidity
    ClaimType.BUNDLING -> R.string.claim_bundling
    ClaimType.CHARGES -> R.string.claim_charges
}

@StringRes
fun claimTypeLabelResEn(type: ClaimType): Int = when (type) {
    ClaimType.RETURN_RATE -> R.string.claim_return_rate_en
    ClaimType.GUARANTEE -> R.string.claim_guarantee_en
    ClaimType.LOCK_IN -> R.string.claim_lock_in_en
    ClaimType.LIQUIDITY -> R.string.claim_liquidity_en
    ClaimType.BUNDLING -> R.string.claim_bundling_en
    ClaimType.CHARGES -> R.string.claim_charges_en
}

/** The claim type's name in the language the user chose. */
@Composable
fun claimTypeLabel(type: ClaimType): String =
    localized(claimTypeLabelRes(type), claimTypeLabelResEn(type))
