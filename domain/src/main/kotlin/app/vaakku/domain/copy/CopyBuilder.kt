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

    /**
     * The state label for *any* state, including the silent ones, which resolve
     * to `state_silent` — an em dash.
     *
     * The Details ledger (§6.6 screen 4) draws a row per claim type whether or
     * not there is anything to say about it, so it needs a label for "nothing is
     * being said here" that is not a word. PENDING and UNCERTAIN deliberately
     * share it: the difference between them is internal, and a screen that showed
     * it would be grading the evidence out loud (CLAUDE.md #1, #2).
     *
     * [build] remains the only route onto a *card*, and it still refuses
     * everything but DIFFERS and NOT_IN_DOCUMENT.
     */
    fun stateLabel(state: DeltaState): CopyRef = when (state) {
        DeltaState.MATCHES -> CopyRef("state_matches")
        DeltaState.NOT_IN_DOCUMENT -> CopyRef("state_not_in_document")
        DeltaState.DIFFERS -> CopyRef("state_differs")
        DeltaState.PENDING, DeltaState.UNCERTAIN -> CopyRef("state_silent")
    }
}
