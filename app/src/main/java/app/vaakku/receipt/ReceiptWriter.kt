package app.vaakku.receipt

import android.content.Context
import android.os.Build
import app.vaakku.BuildConfig
import app.vaakku.domain.receipt.ChainCheck
import app.vaakku.domain.receipt.Receipt
import app.vaakku.domain.receipt.ReceiptBuilder
import app.vaakku.session.SessionEvidence
import app.vaakku.session.SessionRuntime
import app.vaakku.session.SessionState

/**
 * Builds, signs and exports the receipt for a finished session — build plan
 * §7.1 → §7.2 → §7.3 in the one order they can happen in.
 *
 * The order is forced, and worth stating once so no call site has to rediscover
 * it: the chain has to exist before there is a `head` to sign, the signature
 * goes inside the JSON, and the JSON is what gets written out. Signing after
 * export would sign a file nobody has.
 *
 * ### Where the numbers come from
 *
 * Everything except the device name and the app version comes from
 * [SessionRuntime]: the event log is the reconciler's own append-only log, and
 * the ledger is `Reconciler.ledger()`. Nothing is recomputed here and nothing is
 * re-derived — a receipt that disagreed with the screen the buyer was just
 * looking at would be worse than no receipt.
 */
object ReceiptWriter {

    /**
     * What happened, in terms a screen can show.
     *
     * [chain] is the app's own verification of the receipt it just built. It
     * should never fail — it is this process checking its own arithmetic a
     * microsecond after doing it — and it is checked anyway, because the
     * alternative is exporting a file that does not verify and letting somebody
     * discover that on a laptop in front of a grievance officer.
     */
    data class Outcome(
        val receipt: Receipt,
        val chain: ChainCheck,
        val signed: Boolean,
        val strongBox: Boolean,
        val export: ReceiptExport.Result?,
    ) {
        val head: String get() = receipt.head
    }

    /**
     * Builds the receipt for [state], signs it if this phone can, writes it to
     * `Download/Vaakku/<sessionId>/`, and reports what happened.
     *
     * Returns null only when there is no session to write — no id, or no start
     * time. A session with an empty ledger is *not* nothing: "we talked for four
     * minutes and nothing was said that differed from the document" is a real
     * result and deserves a receipt saying so.
     */
    suspend fun write(context: Context, state: SessionState): Outcome? {
        if (state.sessionId.isEmpty() || state.startedAtMs == 0L) return null

        val unsigned = ReceiptBuilder.build(
            sessionId = state.sessionId,
            appVersion = BuildConfig.VERSION_NAME,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            startedAtMs = state.startedAtMs,
            // A session whose end has not been recorded is written as ending
            // now. `endSession` sets this, so in practice it is always set by
            // the time a receipt is asked for; falling back to the start would
            // print a zero-length session, which is a wrong statement about how
            // long somebody was spoken to.
            endedAtMs = if (state.endedAtMs != 0L) state.endedAtMs else System.currentTimeMillis(),
            events = SessionRuntime.eventLog(),
            ledger = state.ledger,
        )

        // §7.2: the attestation challenge is h0 — the session's opening hash, so
        // a certificate attested for one session cannot be presented as
        // another's. h0 is `hashes[0]`, and its hex text is what is challenged,
        // matching the convention used for signing the head.
        val challenge = unsigned.hashes.first().toByteArray(Charsets.US_ASCII)
        val signature = ReceiptSigner.sign(unsigned, challenge)
        val receipt = if (signature != null) unsigned.signedWith(signature) else unsigned

        val chain = ReceiptBuilder.verify(receipt)

        val export = ReceiptExport.export(
            context = context,
            sessionId = state.sessionId,
            receiptJson = receipt.canonicalJson(),
            sessionDir = SessionEvidence.forSession(context, state.sessionId).sessionDir,
        )

        return Outcome(
            receipt = receipt,
            chain = chain,
            signed = signature != null,
            strongBox = signature?.strongBox == true,
            export = export,
        )
    }
}
