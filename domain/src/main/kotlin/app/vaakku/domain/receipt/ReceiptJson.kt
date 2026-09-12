package app.vaakku.domain.receipt

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.reconcile.ReconcilerEvent
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Every domain object the receipt records, as [JsonValue] — build plan §7.1.
 *
 * ### The discriminators are constants, not class names
 *
 * `"spoken_observed"`, `"rate"`, `"written"` and the rest are written out
 * literally instead of derived from `::class.simpleName`. A Kotlin rename is a
 * refactor; changing the bytes that a hash chain covers is a format break that
 * invalidates every receipt already exported. Those two must not be the same
 * keystroke, so the wire names live here and the compiler cannot quietly
 * change them.
 *
 * ### Numbers are digits, decided once
 *
 * [JsonValue] has no number case (see its KDoc). This file is where every
 * numeric field becomes the exact characters the chain covers:
 *  - `Int`/`Long` print as themselves — the two languages agree on integers
 *    well past any value here;
 *  - [BigDecimal] goes through [decimal], which strips trailing zeros, so a
 *    `5` and a `5.00` that are the same rate produce the same bytes;
 *  - [Double] goes through [confidence] at a fixed six decimal places, because
 *    the shortest-round-trip form that `Double.toString` and JavaScript's
 *    `Number.prototype.toString` each produce are *not* the same string (Java
 *    prints `1.0`, JavaScript prints `1`).
 *
 * ### What is recorded and what is rendered are different questions
 *
 * `confidence` and [app.vaakku.domain.model.ReasonCode] are both in here, and
 * both are internal by construction (§2.4 allows the fields and bans ever
 * showing them; §5.1 confines a reason code to the debug overlay). They are
 * recorded because a receipt is the account of what the app actually did, and a
 * chain that covers only the flattering half of that is not an integrity
 * guarantee. They are kept off paper on the other side: `tools/packet-cli/`
 * renders from an explicit field allowlist, so a field being present in the
 * JSON is not enough to put it in front of anybody.
 */
object ReceiptJson {

    // --- wire names (frozen; see the class KDoc) ------------------------------

    const val EVENT_SPOKEN_OBSERVED = "spoken_observed"
    const val EVENT_WRITTEN_OBSERVED = "written_observed"
    const val EVENT_DOCUMENT_SCAN_COMPLETED = "document_scan_completed"
    const val EVENT_USER_RECHECK = "user_recheck"
    const val EVENT_RESET = "reset"

    /** §7.1's `events[]`, one object each, in the order the reconciler saw them. */
    fun event(event: ReconcilerEvent): JsonValue.Obj = when (event) {
        is ReconcilerEvent.SpokenObserved -> JsonValue.Obj(
            "event" to JsonValue.Str(EVENT_SPOKEN_OBSERVED),
            "observation" to observation(event.observation),
        )
        is ReconcilerEvent.WrittenObserved -> JsonValue.Obj(
            "event" to JsonValue.Str(EVENT_WRITTEN_OBSERVED),
            "observation" to observation(event.observation),
        )
        ReconcilerEvent.DocumentScanCompleted -> JsonValue.Obj(
            "event" to JsonValue.Str(EVENT_DOCUMENT_SCAN_COMPLETED),
        )
        is ReconcilerEvent.UserRecheck -> JsonValue.Obj(
            "event" to JsonValue.Str(EVENT_USER_RECHECK),
            "type" to JsonValue.Str(event.type.name),
        )
        ReconcilerEvent.Reset -> JsonValue.Obj("event" to JsonValue.Str(EVENT_RESET))
    }

    fun observation(observation: Observation): JsonValue.Obj = JsonValue.Obj(
        "id" to JsonValue.Str(observation.id),
        "source" to JsonValue.Str(observation.source.name),
        "type" to JsonValue.Str(observation.type.name),
        "value" to claimValue(observation.value),
        "hedged" to JsonValue.Bool(observation.hedged),
        "negated" to JsonValue.Bool(observation.negated),
        "conditional" to JsonValue.Bool(observation.conditional),
        "confidence" to JsonValue.Str(confidence(observation.confidence)),
        "provenance" to provenance(observation.provenance),
        "tMs" to JsonValue.Str(observation.tMs.toString()),
        "ambiguous" to nullableStr(observation.ambiguous?.name),
    )

    fun claimValue(value: ClaimValue): JsonValue.Obj = when (value) {
        is ClaimValue.Rate -> JsonValue.Obj(
            "kind" to JsonValue.Str("rate"),
            // A Set has no order, and BigDecimal equality counts scale, so
            // `{8, 8.0}` is a two-element set of one number. Normalizing the
            // digits first collapses that pair, and sorting by value then puts
            // the result in one order rather than whatever order the set
            // happened to iterate in — an unordered input cannot be allowed to
            // change the hash.
            "percents" to JsonValue.Arr(
                value.percents.map { decimal(it) }.distinct().sortedBy { BigDecimal(it) }.map { JsonValue.Str(it) },
            ),
            "qualifier" to JsonValue.Str(value.qualifier.name),
        )
        is ClaimValue.Guarantee -> JsonValue.Obj(
            "kind" to JsonValue.Str("guarantee"),
            "guaranteed" to JsonValue.Bool(value.guaranteed),
        )
        is ClaimValue.LockIn -> JsonValue.Obj(
            "kind" to JsonValue.Str("lock_in"),
            "months" to JsonValue.Str(value.months.toString()),
        )
        is ClaimValue.Liquidity -> JsonValue.Obj(
            "kind" to JsonValue.Str("liquidity"),
            "withdrawableAfterMonths" to nullableStr(value.withdrawableAfterMonths?.toString()),
            "surrenderNilBeforeMonths" to nullableStr(value.surrenderNilBeforeMonths?.toString()),
        )
        is ClaimValue.Bundling -> JsonValue.Obj(
            "kind" to JsonValue.Str("bundling"),
            "requiredForLoan" to JsonValue.Bool(value.requiredForLoan),
        )
        is ClaimValue.Charges -> JsonValue.Obj(
            "kind" to JsonValue.Str("charges"),
            "anyCharges" to JsonValue.Bool(value.anyCharges),
            "percent" to nullableStr(value.percent?.let { decimal(it) }),
            "label" to nullableStr(value.label),
        )
    }

    fun provenance(provenance: Provenance): JsonValue.Obj = when (provenance) {
        is Provenance.Spoken -> JsonValue.Obj(
            "kind" to JsonValue.Str("spoken"),
            "span" to JsonValue.Str(provenance.span),
            "startMs" to JsonValue.Str(provenance.startMs.toString()),
            "endMs" to JsonValue.Str(provenance.endMs.toString()),
            "engine" to JsonValue.Str(provenance.engine),
        )
        is Provenance.Written -> JsonValue.Obj(
            "kind" to JsonValue.Str("written"),
            "lineText" to JsonValue.Str(provenance.lineText),
            "box" to box(provenance.box),
            "frameId" to JsonValue.Str(provenance.frameId),
            // §7.1 asks the receipt to carry crop file names. Null is honest
            // here and common: SessionEvidence returns null when a JPEG did not
            // land, and a name pointing at a file that is not there would be
            // worse than no name (CLAUDE.md #2).
            "cropFile" to nullableStr(provenance.cropFile),
        )
    }

    fun box(box: Box): JsonValue.Obj = JsonValue.Obj(
        "left" to JsonValue.Str(box.left.toString()),
        "top" to JsonValue.Str(box.top.toString()),
        "right" to JsonValue.Str(box.right.toString()),
        "bottom" to JsonValue.Str(box.bottom.toString()),
    )

    /** §7.1's `entries[]` — the final ledger, with provenance, as it stood at the end. */
    fun ledgerEntry(entry: LedgerEntry): JsonValue.Obj = JsonValue.Obj(
        "type" to JsonValue.Str(entry.type.name),
        "spoken" to (entry.spoken?.let { observation(it) } ?: JsonValue.Null),
        "written" to JsonValue.Arr(entry.written.map { observation(it) }),
        "state" to JsonValue.Str(entry.state.name),
        "reason" to JsonValue.Str(entry.reason.name),
        "mentionCount" to JsonValue.Str(entry.mentionCount.toString()),
        "dismissed" to JsonValue.Bool(entry.dismissed),
    )

    // --- number formatting ---------------------------------------------------

    /**
     * A [BigDecimal] as its shortest exact digits: `8.0` and `8` both give `8`,
     * `4.50` gives `4.5`.
     *
     * `ValuePhrase.formatNumber` computes the same thing for the screen and is
     * deliberately not called here. That one exists to look right to a reader
     * and may be re-tuned for it; this one is part of a hash format and may not
     * change without invalidating existing receipts. Sharing the function would
     * make a typographic decision into a format break.
     */
    fun decimal(value: BigDecimal): String = value.stripTrailingZeros()
        .let { if (it.scale() < 0) it.setScale(0) else it }
        .toPlainString()

    /** §7.1 number rule for `confidence`: fixed [CONFIDENCE_SCALE] decimals. */
    fun confidence(value: Double): String {
        // BigDecimal(Double.NaN) throws NumberFormatException, which would
        // surface as an unexplained crash while exporting. A NaN here means a
        // recognizer divided by zero upstream; say which field and let it fail,
        // rather than coercing it to 0 and recording a number nobody computed.
        require(!value.isNaN() && !value.isInfinite()) { "confidence must be a finite number, was $value" }
        return BigDecimal(value).setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP).toPlainString()
    }

    /**
     * Six places. `confidence` is a 0..1 ratio produced by a recognizer, so six
     * decimals is far past anything the reconciler's thresholds can act on,
     * while a fixed count is what makes the digits reproducible in another
     * language instead of subject to its float printer.
     */
    const val CONFIDENCE_SCALE = 6

    private fun nullableStr(value: String?): JsonValue =
        if (value == null) JsonValue.Null else JsonValue.Str(value)
}
