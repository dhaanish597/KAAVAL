package app.vaakku.domain.gate

import app.vaakku.domain.copy.CopyBuilder
import app.vaakku.domain.copy.ValuePhrase
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.LedgerEntry

/**
 * One row of the "before you sign" list: what was said, what the document
 * says, and the §8.4 question to ask about it.
 *
 * The two `*TemplateKey` fields hold **value-phrase** keys (§8.3, produced by
 * [ValuePhrase]) with their own args, not the §5.8 line templates. The line
 * templates take a value phrase as their single slot, so the screen wraps
 * these in the existing `card_differs_*_line` / `card_not_in_document_*`
 * strings and no new value phrasing is invented anywhere.
 *
 * [writtenTemplateKey] is null exactly when [state] is NOT_IN_DOCUMENT: there
 * is no written value to phrase, and the screen shows the not-in-document
 * line in that slot instead.
 */
data class GateItem(
    val type: ClaimType,
    val spokenTemplateKey: String,
    val spokenArgs: List<String>,
    val writtenTemplateKey: String?,
    val writtenArgs: List<String>,
    /** Only DIFFERS or NOT_IN_DOCUMENT ever reach a [GateItem]. */
    val state: DeltaState,
    val followUpKey: String,
)

/**
 * Builds the list behind the "before you sign" screen — the same six claim
 * types the rest of the app works in, filtered to the ones where something
 * was said that the document either contradicts or does not mention.
 *
 * Two properties are load-bearing:
 *
 *  1. **The order is [ClaimType] declaration order and nothing else.** Not
 *     confidence, not recency, not mentionCount, not any notion of which
 *     entry matters more. Ordering by importance would be one of the banned
 *     rankings under another name, and this app does not rank (CLAUDE.md #1).
 *     The enum order is fixed, published in §5.1, and carries no meaning
 *     beyond "this is the order the six types are always listed in" — which
 *     is exactly why it is the right one.
 *
 *  2. **The same states are silent here as everywhere else.** MATCHES,
 *     PENDING and UNCERTAIN produce no row, for the same reason
 *     [CopyBuilder.build] returns null for them (CLAUDE.md #2). A dismissed
 *     entry produces no row either: the human has already re-checked it.
 */
object DecisionGate {

    /**
     * @param ledger the reconciler's ledger — one [LedgerEntry] per
     *   [ClaimType], as returned by `Reconciler.ledger()`. A missing key is
     *   treated as nothing to say, not as an error.
     */
    fun build(ledger: Map<ClaimType, LedgerEntry>): List<GateItem> =
        ClaimType.entries.mapNotNull { type -> ledger[type]?.let(::item) }

    private fun item(entry: LedgerEntry): GateItem? {
        if (entry.dismissed) return null
        return when (entry.state) {
            DeltaState.DIFFERS -> differs(entry)
            DeltaState.NOT_IN_DOCUMENT -> notInDocument(entry)
            DeltaState.MATCHES, DeltaState.PENDING, DeltaState.UNCERTAIN -> null
        }
    }

    /**
     * The written observation is the most confident one, matching
     * [CopyBuilder] exactly — the card and this row must never quote
     * different numbers off the same ledger entry.
     *
     * A DIFFERS with no spoken observation, or with no written observation,
     * cannot come out of the reconciler (§5.7 resolves both to PENDING or
     * NOT_IN_DOCUMENT before any comparison runs). If one arrives anyway,
     * this returns no row rather than throwing: this screen opens
     * automatically at the end of a session, and silence is the defined
     * failure (CLAUDE.md #2).
     */
    private fun differs(entry: LedgerEntry): GateItem? {
        val spoken = entry.spoken ?: return null
        val written = entry.written.maxByOrNull { it.confidence } ?: return null
        val spokenPhrase = ValuePhrase.forAny(spoken.value)
        val writtenPhrase = ValuePhrase.forAny(written.value)
        return GateItem(
            type = entry.type,
            spokenTemplateKey = spokenPhrase.key,
            spokenArgs = spokenPhrase.args,
            writtenTemplateKey = writtenPhrase.key,
            writtenArgs = writtenPhrase.args,
            state = DeltaState.DIFFERS,
            followUpKey = CopyBuilder.followUpFor(entry.type).key,
        )
    }

    private fun notInDocument(entry: LedgerEntry): GateItem? {
        val spoken = entry.spoken ?: return null
        val spokenPhrase = ValuePhrase.forAny(spoken.value)
        return GateItem(
            type = entry.type,
            spokenTemplateKey = spokenPhrase.key,
            spokenArgs = spokenPhrase.args,
            writtenTemplateKey = null,
            writtenArgs = emptyList(),
            state = DeltaState.NOT_IN_DOCUMENT,
            followUpKey = CopyBuilder.followUpFor(entry.type).key,
        )
    }
}
