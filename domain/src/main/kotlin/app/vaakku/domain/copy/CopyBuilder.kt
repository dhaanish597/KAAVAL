package app.vaakku.domain.copy

import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.LedgerEntry

/**
 * Turns a [LedgerEntry] into [CardCopy] — build plan §5.8. Returns null for
 * every state except DIFFERS and NOT_IN_DOCUMENT: MATCHES, PENDING and
 * UNCERTAIN are silent by design (CLAUDE.md #2), and this function is the
 * one place that decision is enforced for card rendering — there is no
 * separate "hide it in the UI" step to forget.
 */
object CopyBuilder {

    fun build(entry: LedgerEntry): CardCopy? = when (entry.state) {
        DeltaState.DIFFERS -> buildDiffers(entry)
        DeltaState.NOT_IN_DOCUMENT -> buildNotInDocument(entry)
        DeltaState.MATCHES, DeltaState.PENDING, DeltaState.UNCERTAIN -> null
    }

    private fun buildDiffers(entry: LedgerEntry): CardCopy.Differs {
        val spoken = requireNotNull(entry.spoken) { "DIFFERS requires a spoken observation" }
        val written = entry.written.maxByOrNull { it.confidence }
            ?: error("DIFFERS requires at least one written observation")
        return CardCopy.Differs(
            stateLabel = CopyRef("state_differs"),
            spokenLine = LineRef("card_differs_spoken_line", ValuePhrase.forAny(spoken.value)),
            writtenLine = LineRef("card_differs_written_line", ValuePhrase.forAny(written.value)),
            footerKey = "card_differs_footer",
            englishSmallLineKey = "card_differs_english_small_line",
            followUp = followUpFor(entry.type),
        )
    }

    private fun buildNotInDocument(entry: LedgerEntry): CardCopy.NotInDocument {
        val spoken = requireNotNull(entry.spoken) { "NOT_IN_DOCUMENT requires a spoken observation" }
        return CardCopy.NotInDocument(
            stateLabel = CopyRef("state_not_in_document"),
            spokenLine = LineRef("card_not_in_document_spoken_line", ValuePhrase.forAny(spoken.value)),
            hintKey = "card_not_in_document_hint_line",
            followUp = followUpFor(entry.type),
        )
    }

    /** The §8.4 follow-up question key for one claim type — grouped exactly as §8.4 groups them. */
    fun followUpFor(type: ClaimType): CopyRef = when (type) {
        ClaimType.GUARANTEE -> CopyRef("followup_guarantee")
        ClaimType.LOCK_IN, ClaimType.LIQUIDITY -> CopyRef("followup_lockin_liquidity")
        ClaimType.BUNDLING -> CopyRef("followup_bundling")
        ClaimType.RETURN_RATE, ClaimType.CHARGES -> CopyRef("followup_rate_charges")
    }

    /** The (silent-state-safe) state label for any state, for debug/overlay use — never MATCHES/PENDING/UNCERTAIN on a user-facing card. */
    fun stateLabel(state: DeltaState): CopyRef = when (state) {
        DeltaState.MATCHES -> CopyRef("state_matches")
        DeltaState.NOT_IN_DOCUMENT -> CopyRef("state_not_in_document")
        DeltaState.DIFFERS -> CopyRef("state_differs")
        DeltaState.PENDING, DeltaState.UNCERTAIN -> CopyRef("state_silent")
    }
}
