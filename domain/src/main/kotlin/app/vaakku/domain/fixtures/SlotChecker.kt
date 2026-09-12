package app.vaakku.domain.fixtures

import app.vaakku.domain.copy.ValuePhrase
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.Observation

/**
 * Compares a [LabelEntry.expectedClaims] slot against what SpokenExtractor
 * actually produced from a transcript — build plan §11.3's "slot accuracy."
 * True if ANY observation of that type matches; the point of this pass is
 * "did the ASR engine's text let us recover the claim at all," not
 * confidence/state (that is what the fixtures already cover).
 */
object SlotChecker {

    fun matches(type: ClaimType, expected: String, observations: List<Observation>): Boolean {
        val candidates = observations.filter { it.type == type }
        if (candidates.isEmpty()) return false
        return candidates.any { matchesOne(type, expected, it.value) }
    }

    /**
     * Renders what SpokenExtractor actually produced for [type], in the same
     * string vocabulary `labels.json` uses for its expected values — so a
     * report can put expected and actual side by side.
     *
     * A bare correct/total count says an engine missed a slot but not whether
     * it heard the wrong number or heard nothing at all, and those two failures
     * have opposite fixes (calibrate the lexicon vs change engine — §11.3).
     * Every competing observation is listed, in order, because a self-correction
     * transcript legitimately yields more than one.
     */
    fun describe(type: ClaimType, observations: List<Observation>): String {
        val candidates = observations.filter { it.type == type }
        if (candidates.isEmpty()) return "(nothing extracted)"
        return candidates.joinToString(" | ") { describeOne(type, it.value) }
    }

    private fun describeOne(type: ClaimType, value: ClaimValue): String = when (type) {
        ClaimType.GUARANTEE -> (value as ClaimValue.Guarantee).guaranteed.toString()
        ClaimType.RETURN_RATE -> (value as ClaimValue.Rate).percents
            .map { ValuePhrase.formatNumber(it) }
            .sorted()
            .joinToString(",")
        ClaimType.LOCK_IN -> (value as ClaimValue.LockIn).months.toString()
        ClaimType.LIQUIDITY -> {
            val liquidity = value as ClaimValue.Liquidity
            when {
                liquidity.withdrawableAfterMonths != null -> "withdrawAfter:${liquidity.withdrawableAfterMonths}"
                liquidity.surrenderNilBeforeMonths != null -> "nilBefore:${liquidity.surrenderNilBeforeMonths}"
                else -> "(liquidity with no months)"
            }
        }
        ClaimType.BUNDLING -> (value as ClaimValue.Bundling).requiredForLoan.toString()
        ClaimType.CHARGES -> (value as ClaimValue.Charges).anyCharges.toString()
    }

    private fun matchesOne(type: ClaimType, expected: String, value: ClaimValue): Boolean = when (type) {
        ClaimType.GUARANTEE -> (value as ClaimValue.Guarantee).guaranteed.toString() == expected.trim()
        ClaimType.RETURN_RATE -> {
            val expectedPercents = expected.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val actualPercents = (value as ClaimValue.Rate).percents.map { ValuePhrase.formatNumber(it) }.toSet()
            expectedPercents.isNotEmpty() && expectedPercents == actualPercents
        }
        ClaimType.LOCK_IN -> (value as ClaimValue.LockIn).months.toString() == expected.trim()
        ClaimType.LIQUIDITY -> {
            val liquidity = value as ClaimValue.Liquidity
            when {
                expected.startsWith("withdrawAfter:") -> liquidity.withdrawableAfterMonths?.toString() == expected.removePrefix("withdrawAfter:").trim()
                expected.startsWith("nilBefore:") -> liquidity.surrenderNilBeforeMonths?.toString() == expected.removePrefix("nilBefore:").trim()
                else -> false
            }
        }
        ClaimType.BUNDLING -> (value as ClaimValue.Bundling).requiredForLoan.toString() == expected.trim()
        ClaimType.CHARGES -> (value as ClaimValue.Charges).anyCharges.toString() == expected.trim()
    }
}
