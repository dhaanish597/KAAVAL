package app.vaakku.ui

import androidx.annotation.StringRes
import app.vaakku.R

/**
 * The six document kinds a session can be about (design spec frame 07, and the
 * `DOMAINS` table in the v2 prototype).
 *
 * These are **document types, not claim types.** The build plan's six claim types
 * — RETURN_RATE, GUARANTEE, LOCK_IN, LIQUIDITY, BUNDLING, CHARGES — are a
 * different axis: a rental agreement and an insurance illustration can both carry
 * a CHARGES claim. The domain chosen here decides which claim types are armed for
 * the session and which document the camera expects, nothing more.
 *
 * [mark] is a three-letter index mark set in the mono "Record" voice. It stays
 * Latin deliberately: it is a mark on a form, not a word to read.
 *
 * This lives in `ui/` rather than `domain/` because at P0 it carries no rules yet.
 * When the claim-type arming table is written it belongs in `:domain` as pure
 * data, and this enum should become a thin presentation mapping over it.
 */
enum class Domain(
    val mark: String,
    @param:StringRes val label: Int,
    @param:StringRes val source: Int,
) {
    INSURANCE("DOC", R.string.domain_insurance, R.string.domain_src_insurance),
    LOAN("BNK", R.string.domain_loan, R.string.domain_src_loan),
    RENTAL("KEY", R.string.domain_rental, R.string.domain_src_rental),
    PURCHASE("TAG", R.string.domain_purchase, R.string.domain_src_purchase),
    JOB("JOB", R.string.domain_job, R.string.domain_src_job),
    SERVICE("RCP", R.string.domain_service, R.string.domain_src_service),
}
