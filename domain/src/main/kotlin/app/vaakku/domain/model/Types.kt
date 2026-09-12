package app.vaakku.domain.model

import java.math.BigDecimal

// -----------------------------------------------------------------------------
// Core types — build plan §5.1.
//
// HARD RULE (CLAUDE.md #1, build plan §5.9): no name here may match the
// §2.4/§5.9 banned-word list — this module never characterizes a person.
// The schema guard test (SchemaGuardTest, §5.9) enforces this by reflection
// over every class in this package; it is not just a comment.
// -----------------------------------------------------------------------------

/** The six things VAAKKU compares. Locked list — build plan §2.1. */
enum class ClaimType { RETURN_RATE, GUARANTEE, LOCK_IN, LIQUIDITY, BUNDLING, CHARGES }

/** Where an [Observation] came from. The two producers are independent. */
enum class Source { SPOKEN, WRITTEN }

/**
 * Internal reconciliation state for one [ClaimType]. Only three of these are
 * ever shown to the user (MATCHES / NOT_IN_DOCUMENT / DIFFERS) — build plan
 * §2.3. PENDING and UNCERTAIN are silent by design (CLAUDE.md #2).
 */
enum class DeltaState { PENDING, UNCERTAIN, MATCHES, NOT_IN_DOCUMENT, DIFFERS }

/** How firmly a spoken or written rate was stated. */
enum class RateQualifier { ASSERTED, UP_TO, ILLUSTRATIVE, EXPECTED }

/** A pixel box for a written line on a document photo. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The typed content of one observation. One case per [ClaimType]. */
sealed interface ClaimValue {
    data class Rate(val percents: Set<BigDecimal>, val qualifier: RateQualifier) : ClaimValue
    data class Guarantee(val guaranteed: Boolean) : ClaimValue
    data class LockIn(val months: Int) : ClaimValue
    data class Liquidity(val withdrawableAfterMonths: Int?, val surrenderNilBeforeMonths: Int?) : ClaimValue
    data class Bundling(val requiredForLoan: Boolean) : ClaimValue
    data class Charges(val anyCharges: Boolean, val percent: BigDecimal?, val label: String?) : ClaimValue
}

/** Where an observation's text came from, so a card can quote it. */
sealed interface Provenance {
    data class Spoken(val span: String, val startMs: Long, val endMs: Long, val engine: String) : Provenance
    data class Written(val lineText: String, val box: Box, val frameId: String, val cropFile: String?) : Provenance
}

/**
 * One thing heard or read. `confidence` is INTERNAL ONLY — build plan §2.4
 * explicitly allows the field but bans ever displaying it.
 *
 * [ambiguous] is a deliberate addition beyond the build plan's suggested shape
 * (§5.1 says "adjust names but keep semantics"). The spoken extractor (§5.5)
 * must be able to flag a negation or normalization ambiguity *at the point of
 * extraction* — e.g. the GUARANTEE special rule's fuzzy-negation case — and
 * the reconciler's decision table step 3 needs to read that flag directly
 * rather than re-deriving it from confidence alone. Recorded in STATUS.md as
 * a resolved spec ambiguity.
 */
data class Observation(
    val id: String,
    val source: Source,
    val type: ClaimType,
    val value: ClaimValue,
    val hedged: Boolean,
    val negated: Boolean,
    val conditional: Boolean,
    val confidence: Double,
    val provenance: Provenance,
    val tMs: Long,
    val ambiguous: ReasonCode? = null,
)

/**
 * Why a [LedgerEntry] is in its current state. Shown only in the debug
 * overlay (§5.1) — it must never describe a person.
 */
enum class ReasonCode {
    NO_SPOKEN, NO_DOC_YET, SPOKEN_LOW_CONF, WRITTEN_LOW_CONF, HEDGED, NEGATION_AMBIGUOUS,
    CONDITIONAL, NORMALIZATION_AMBIGUOUS, VALUE_EQUAL, VALUE_IN_SCENARIOS, TOLERANCE_EXCEEDED,
    DOC_SILENT, NEEDS_CORROBORATION,
}

/** One row of the claim ledger — the reconciler's output for one [ClaimType]. */
data class LedgerEntry(
    val type: ClaimType,
    val spoken: Observation?,
    val written: List<Observation>,
    val state: DeltaState,
    val reason: ReasonCode,
    val mentionCount: Int,
    val dismissed: Boolean,
)
