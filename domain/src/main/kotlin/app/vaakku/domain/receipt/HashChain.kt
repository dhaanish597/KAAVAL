package app.vaakku.domain.receipt

import app.vaakku.domain.reconcile.ReconcilerEvent
import java.security.MessageDigest

/**
 * The receipt hash chain — build plan §7.1.
 *
 * ```
 * h0  = SHA-256(sessionId | startEpochMs | appVersion)
 * h_i = SHA-256(h_{i-1} || canonical(event_i))
 * ```
 *
 * ### What this does and does not prove
 *
 * It proves that a receipt has not been *edited after export*: change a byte of
 * any event, drop one, or swap two, and every hash from that point on is
 * different, so [verify] fails. That is all it proves. It says nothing about
 * whether what the microphone heard was true, and nothing whatsoever about the
 * person who was speaking — the chain covers a record of what this app did.
 *
 * ### Why `h_{i-1}` goes in as hex text
 *
 * §7.1 writes `||` without saying whether the previous hash is appended as its
 * 32 raw bytes or as its 64 hex characters. This implementation uses the hex
 * text, so the hashed input is a pure UTF-8 string at every step.
 *
 * Two reasons, both about §7.4. The CLI verifying this chain is Node, and a
 * chain defined over text is reproduced there in one line
 * (`createHash('sha256').update(prev + canonical).digest('hex')`) with no
 * `Buffer.concat`, no encoding argument to get wrong, and nothing that can be
 * silently coerced. And hex is fixed-width: 64 characters always, so the join
 * between `h_{i-1}` and the canonical JSON is unambiguous without a separator.
 * Being self-delimiting is the property that matters — a variable-length prefix
 * joined to attacker-adjacent text is how length-extension confusion starts.
 *
 * ### The `|` in h0
 *
 * Taken literally from §7.1. It is safe here rather than by accident: a session
 * id is `session_<iso8601 stamp>` minted by
 * `SessionEvidence.newSessionId`, `startEpochMs` is decimal digits, and the app
 * version comes from the build file. None can contain `|`, so no two different
 * triples can produce the same joined string. [genesis] states that as a
 * `require` so the assumption is checked rather than trusted.
 */
object HashChain {

    /**
     * `h0` for a session. [appVersion] is `BuildConfig.VERSION_NAME` at the
     * call site; the domain never reads it itself.
     */
    fun genesis(sessionId: String, startEpochMs: Long, appVersion: String): String {
        // Not defence against a hostile input — these three are all minted by
        // this app. It is defence against a *future* id scheme that allows a
        // separator and would make two distinct sessions share an h0.
        require(!sessionId.contains(SEPARATOR)) { "sessionId must not contain '$SEPARATOR'" }
        require(!appVersion.contains(SEPARATOR)) { "appVersion must not contain '$SEPARATOR'" }
        return sha256Hex("$sessionId$SEPARATOR$startEpochMs$SEPARATOR$appVersion")
    }

    /** `h_i` from `h_{i-1}` and one event's canonical JSON. */
    fun step(previousHex: String, canonicalEvent: String): String =
        sha256Hex(previousHex + canonicalEvent)

    /**
     * The whole chain: `h0` first, then one hash per event, in order.
     *
     * The returned list is therefore `events.size + 1` long and its last
     * element is the `head` §7.1 asks to be signed. Keeping `h0` in the list
     * rather than beside it means the verifier walks one loop with no special
     * case for the first element, and the file shows every intermediate hash —
     * which is what lets a CLI report *where* a chain broke rather than only
     * that it did.
     */
    fun chain(sessionId: String, startEpochMs: Long, appVersion: String, events: List<ReconcilerEvent>): List<String> {
        val hashes = ArrayList<String>(events.size + 1)
        hashes += genesis(sessionId, startEpochMs, appVersion)
        events.forEach { event ->
            hashes += step(hashes.last(), canonical(ReceiptJson.event(event)))
        }
        return hashes
    }

    /**
     * Recomputes the chain over [canonicalEvents] and compares it to [hashes].
     *
     * Takes canonical strings rather than [ReconcilerEvent]s so that a verifier
     * can check a receipt it has only *read* — the event objects are gone by
     * then, and re-deriving them from JSON just to re-serialize them would put
     * a parser between the bytes and their hash, which is exactly the step this
     * design removes. The app can hand over `receipt.canonicalEvents`; a future
     * JVM-side verifier can hand over the strings straight from the file.
     */
    fun verify(
        sessionId: String,
        startEpochMs: Long,
        appVersion: String,
        canonicalEvents: List<String>,
        hashes: List<String>,
        head: String,
    ): ChainCheck {
        if (hashes.size != canonicalEvents.size + 1) {
            return ChainCheck.Failed("expected ${canonicalEvents.size + 1} hashes for ${canonicalEvents.size} event(s), found ${hashes.size}")
        }
        val expectedGenesis = genesis(sessionId, startEpochMs, appVersion)
        if (!hashes[0].equals(expectedGenesis, ignoreCase = true)) {
            return ChainCheck.Failed("h0 does not match sessionId|startEpochMs|appVersion")
        }
        canonicalEvents.forEachIndexed { index, canonicalEvent ->
            val expected = step(hashes[index], canonicalEvent)
            if (!hashes[index + 1].equals(expected, ignoreCase = true)) {
                return ChainCheck.Failed("event ${index + 1} of ${canonicalEvents.size} does not match h${index + 1}")
            }
        }
        if (!head.equals(hashes.last(), ignoreCase = true)) {
            return ChainCheck.Failed("head is not the last hash in the chain")
        }
        return ChainCheck.Passed
    }

    /** Lowercase hex SHA-256 of [text] as UTF-8. */
    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    /** §7.1's `|` between the three h0 inputs. */
    const val SEPARATOR = "|"

    private val HEX = "0123456789abcdef".toCharArray()
}

/**
 * The outcome of [HashChain.verify].
 *
 * A sealed result rather than a Boolean because §7.4 has to print
 * `INTEGRITY: FAILED (<reason>)` — and because "which event" is the one piece
 * of information that makes a failed check something a person can act on rather
 * than only worry about. [Failed.reason] describes bytes and indices only;
 * nothing in it is about a person, and the packet prints it verbatim.
 */
sealed interface ChainCheck {
    data object Passed : ChainCheck
    data class Failed(val reason: String) : ChainCheck

    val passed: Boolean get() = this is Passed
}
