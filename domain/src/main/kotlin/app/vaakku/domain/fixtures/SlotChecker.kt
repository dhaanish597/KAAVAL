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
