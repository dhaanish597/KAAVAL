package app.vaakku.domain.receipt

import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.reconcile.ReconcilerEvent
import java.time.Instant

/**
 * §7.2's output, attached to a receipt after the chain is built.
 *
 * Deliberately *not* covered by [Receipt.head]: the signature is over the head,
 * so it cannot also be inside it. Everything else in the file is covered — see
 * [ReceiptBuilder].
 *
 * [strongBox] is a fact about where the key lives, recorded because §7.2 asks
 * for it and because CLAUDE.md #8 forbids claiming hardware the device did not
 * give us. `false` is the ordinary answer on most phones and the packet says so
 * plainly rather than omitting the line.
 */
data class SignatureBlock(
    /** Base64 DER, `SHA256withECDSA` over [Receipt.head]'s ASCII hex characters. */
    val signature: String,
    /** Base64 DER, leaf first, from `KeyStore.getCertificateChain`. */
    val certChain: List<String>,
    val strongBox: Boolean,
)

/**
 * One session, as the record that leaves the phone — build plan §7.1.
 *
 * Construct with [ReceiptBuilder.build]; [toJson] and [canonicalJson] produce
 * the exported `receipt.json`.
 *
 * [canonicalEvents] holds each event's canonical JSON *text*, not the event
 * objects, because the text is what the chain covers. Keeping the exact strings
 * that were hashed means [ReceiptBuilder.verify] re-checks bytes rather than
 * re-deriving them, and it is also what the file embeds — so the app, a JVM
 * verifier and the Node CLI are all looking at the same characters.
 */
data class Receipt(
    val sessionId: String,
    val appVersion: String,
    val deviceModel: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val canonicalEvents: List<String>,
    /** The final ledger, in [ClaimType] declaration order — see [ReceiptBuilder.build]. */
    val entries: List<LedgerEntry>,
    /** `h0`, one hash per event, then [head]. Length is `events + 2`. */
    val hashes: List<String>,
    val signature: SignatureBlock? = null,
) {

    /** The last hash in [hashes] — the value §7.2 signs. */
    val head: String get() = hashes.last()

    /** This receipt with [block] attached. The chain is unchanged by signing. */
    fun signedWith(block: SignatureBlock): Receipt = copy(signature = block)

    /**
     * The exported document.
     *
     * `startedAt`/`endedAt` are ISO-8601 for whoever reads the file; the
     * `…Ms` fields beside them are the load-bearing ones — `h0` is computed
     * from `startedAtMs`, so a verifier that only had the formatted string
     * could not recompute it.
     */
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        buildMap {
            putAll(ReceiptBuilder.closing(this@Receipt).fields)
            put("events", JsonValue.Arr(canonicalEvents.map { embeddedEvent(it) }))
            put("hashes", JsonValue.Arr(hashes.map { JsonValue.Str(it) }))
            put("head", JsonValue.Str(head))
            put("startedAt", JsonValue.Str(iso(startedAtMs)))
            put("endedAt", JsonValue.Str(iso(endedAtMs)))
            put("signature", signature?.let { signatureJson(it) } ?: JsonValue.Null)
        },
    )

    /** [toJson] as canonical JSON — the exact bytes of `receipt.json`. */
    fun canonicalJson(): String = canonical(toJson())

    private fun signatureJson(block: SignatureBlock) = JsonValue.Obj(
        "signature" to JsonValue.Str(block.signature),
        "certChain" to JsonValue.Arr(block.certChain.map { JsonValue.Str(it) }),
        "strongBox" to JsonValue.Bool(block.strongBox),
    )

    private companion object {

        /**
         * A canonical event string, back as the object it encodes.
         *
         * The file embeds events as JSON objects rather than as quoted strings,
         * so `receipt.json` reads as a document and the packet renderer can
         * walk it. Re-parsing here would need a JSON parser in this module for
         * no gain: canonical JSON with sorted ASCII keys and string-only leaves
         * is *already* the object's serialization, so the text can be inlined
         * as-is.
         *
         * The claim that Node agrees byte-for-byte is not left as an assumption:
         * `:domain:receiptFixture` writes a receipt built by this code, and
         * `tools/packet-cli/` re-canonicalizes and re-hashes it with its own
         * independent implementation. If the two ever disagree by one byte, that
         * check fails.
         */
        fun embeddedEvent(canonicalEvent: String): JsonValue = JsonValue.Raw(canonicalEvent)

        fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()
    }
}

/**
 * Builds and verifies a [Receipt] — build plan §7.1.
 *
 * ### The one extension to §7.1's chain
 *
 * §7.1 chains the events and signs the resulting head. Taken literally that
 * leaves `deviceModel`, `endedAt` and the whole of `entries[]` outside the
 * chain: they could be edited and the receipt would still verify. An integrity
 * check that covers only part of the document it is printed on is worse than
 * none, because the person holding the packet cannot see the boundary.
 *
 * So the chain takes one more step of exactly the same shape. After the last
 * event:
 *
 * ```
 * head = SHA-256(h_n || canonical(closing))
 * ```
 *
 * where `closing` is every field of the receipt that is not an event, not a
 * hash and not the signature. `hashes` is `[h0, h1 … h_n, head]`, so `head` is
 * still the last hash in the chain and §7.2 still signs `head` — neither of
 * those sentences changes. What changes is that the signature now covers the
 * entire document except itself.
 *
 * Recorded in STATUS.md as a resolved §7.1 ambiguity.
 */
object ReceiptBuilder {

    /**
     * The format version, inside `closing` and therefore covered by `head`.
     *
     * A verifier reads this first and refuses a receipt it was not written for,
     * rather than verifying it under the wrong rules and reporting a confident
     * wrong answer. Any change to the canonical form, the wire names in
     * [ReceiptJson], or the chain shape is a bump of this string.
     */
    const val SCHEMA = "vaakku.receipt.1"

    /**
     * Builds the receipt for one session.
     *
     * [ledger] is `Reconciler.ledger()`. It arrives as a map and is stored as a
     * list ordered by [ClaimType] declaration order: a map has no order, and
     * `head` covers `entries[]`, so the order has to be pinned to something
     * that cannot drift between two runs on the same data.
     */
    fun build(
        sessionId: String,
        appVersion: String,
        deviceModel: String,
        startedAtMs: Long,
        endedAtMs: Long,
        events: List<ReconcilerEvent>,
        ledger: Map<ClaimType, LedgerEntry>,
    ): Receipt {
        val canonicalEvents = events.map { canonical(ReceiptJson.event(it)) }
        val entries = ClaimType.entries.mapNotNull { ledger[it] }

        val hashes = ArrayList<String>(canonicalEvents.size + 2)
        hashes += HashChain.genesis(sessionId, startEpochMs = startedAtMs, appVersion = appVersion)
        canonicalEvents.forEach { hashes += HashChain.step(hashes.last(), it) }

        // The receipt is assembled once without the closing hash so that
        // `closing()` has a single definition, used identically here and by
        // every verifier. Appending is the last step; nothing reads `head`
        // before it exists.
        val unclosed = Receipt(
            sessionId = sessionId,
            appVersion = appVersion,
            deviceModel = deviceModel,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            canonicalEvents = canonicalEvents,
            entries = entries,
            hashes = hashes,
        )
        hashes += HashChain.step(hashes.last(), canonical(closing(unclosed)))
        return unclosed.copy(hashes = hashes)
    }

    /**
     * Everything `head`'s final step folds in: the session's identity, the
     * device, both timestamps in milliseconds, the schema, and the final
     * ledger.
     *
     * Not included: `events` and `hashes` (already chained), `startedAt` and
     * `endedAt` (formatted views of the `…Ms` fields — a second copy of the
     * same fact, and hashing a derived rendering would make the chain depend on
     * `Instant.toString`), and `signature` (it is over the head).
     */
    fun closing(receipt: Receipt): JsonValue.Obj = JsonValue.Obj(
        "schema" to JsonValue.Str(SCHEMA),
        "sessionId" to JsonValue.Str(receipt.sessionId),
        "appVersion" to JsonValue.Str(receipt.appVersion),
        "deviceModel" to JsonValue.Str(receipt.deviceModel),
        "startedAtMs" to JsonValue.Str(receipt.startedAtMs.toString()),
        "endedAtMs" to JsonValue.Str(receipt.endedAtMs.toString()),
        "entries" to JsonValue.Arr(receipt.entries.map { ReceiptJson.ledgerEntry(it) }),
    )

    /**
     * Recomputes the whole chain and reports the first step that disagrees.
     *
     * This is the JVM half of §7.4's `INTEGRITY:` line. It checks the events,
     * `h0`, and the closing step — so editing a ledger entry or the device name
     * fails here, not only an edited event.
     */
    fun verify(receipt: Receipt): ChainCheck {
        val expectedLength = receipt.canonicalEvents.size + 2
        if (receipt.hashes.size != expectedLength) {
            return ChainCheck.Failed(
                "expected $expectedLength hashes for ${receipt.canonicalEvents.size} event(s), found ${receipt.hashes.size}",
            )
        }
        val eventChain = HashChain.verify(
            sessionId = receipt.sessionId,
            startEpochMs = receipt.startedAtMs,
            appVersion = receipt.appVersion,
            canonicalEvents = receipt.canonicalEvents,
            // Everything except the closing hash, which HashChain does not know about.
            hashes = receipt.hashes.dropLast(1),
            head = receipt.hashes[receipt.hashes.size - 2],
        )
        if (eventChain is ChainCheck.Failed) return eventChain

        val expectedHead = HashChain.step(receipt.hashes[receipt.hashes.size - 2], canonical(closing(receipt)))
        if (!receipt.head.equals(expectedHead, ignoreCase = true)) {
            return ChainCheck.Failed("head does not match the session's closing record (schema, device, timestamps or ledger)")
        }
        return ChainCheck.Passed
    }
}
