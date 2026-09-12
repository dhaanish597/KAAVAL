package app.vaakku.domain.receipt

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.RateQualifier
import app.vaakku.domain.model.ReasonCode
import app.vaakku.domain.model.Source
import app.vaakku.domain.reconcile.ReconcilerEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigDecimal

/**
 * Build plan §7.1: "Tests: tamper any byte → verification fails; reorder events
 * → fails."
 *
 * Both are here, plus the two the spec does not name and the format needs more:
 * the canonical form has to be stable across runs and insertion orders, and the
 * closing record (device, timestamps, final ledger) has to be covered by the
 * head — otherwise half the exported document sits outside the chain.
 */
class HashChainTest {

    // ------------------------------------------------------------------
    // Canonical JSON
    // ------------------------------------------------------------------

    @Test
    fun `keys are sorted and no whitespace is emitted`() {
        val json = JsonValue.Obj(
            "zebra" to JsonValue.Str("z"),
            "alpha" to JsonValue.Str("a"),
            "middle" to JsonValue.Bool(true),
        )
        assertEquals("""{"alpha":"a","middle":true,"zebra":"z"}""", canonical(json))
    }

    @Test
    fun `two objects with the same content canonicalize identically whatever order they were built in`() {
        val one = JsonValue.Obj("a" to JsonValue.Str("1"), "b" to JsonValue.Str("2"))
        val other = JsonValue.Obj("b" to JsonValue.Str("2"), "a" to JsonValue.Str("1"))
        assertEquals(canonical(one), canonical(other))
    }

    @Test
    fun `strings escape exactly what JSON stringify escapes and leave Tamil alone`() {
        // The input is built from code points rather than pasted in as
        // literals. The first version of this test put the control
        // characters straight into the source, where they are invisible: the
        // file ended up holding a raw U+0001 that the expected value did not
        // mention, and the failure then read as a bug in the escaper rather
        // than in the test. A hash format cannot be reviewed from a file
        // whose contents do not survive being looked at.
        val text = buildString {
            append('"')
            append('\\')
            listOf(0x08, 0x09, 0x0A, 0x0C, 0x0D, 0x01).forEach { append(it.toChar()) }
            append("உறுதியான 8%")
        }
        assertEquals(
            """"\"\\\b\t\n\f\r\u0001உறுதியான 8%"""",
            canonical(JsonValue.Str(text)),
        )
    }

    @Test
    fun `a raw value must be an object or an array`() {
        assertThrows(IllegalArgumentException::class.java) { JsonValue.Raw("\"just a string\"") }
        assertThrows(IllegalArgumentException::class.java) { JsonValue.Raw(" {\"a\":\"1\"} ") }
        // The legitimate case: an already-canonical event object.
        assertEquals("""{"a":"1"}""", canonical(JsonValue.Raw("""{"a":"1"}""")))
    }

    // ------------------------------------------------------------------
    // Number formatting — the cross-language rules
    // ------------------------------------------------------------------

    @Test
    fun `a decimal is its shortest exact digits, so scale cannot change the hash`() {
        assertEquals("8", ReceiptJson.decimal(BigDecimal("8.0")))
        assertEquals("8", ReceiptJson.decimal(BigDecimal("8")))
        assertEquals("4.5", ReceiptJson.decimal(BigDecimal("4.50")))
        // stripTrailingZeros turns 100 into 1E+2; toPlainString must not.
        assertEquals("100", ReceiptJson.decimal(BigDecimal("100")))
    }

    @Test
    fun `confidence is always six decimal places`() {
        assertEquals("0.900000", ReceiptJson.confidence(0.9))
        assertEquals("1.000000", ReceiptJson.confidence(1.0))
        assertEquals("0.000000", ReceiptJson.confidence(0.0))
    }

    @Test
    fun `a non-finite confidence is refused rather than recorded as a number nobody computed`() {
        assertThrows(IllegalArgumentException::class.java) { ReceiptJson.confidence(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { ReceiptJson.confidence(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `an unordered percent set gives one canonical order and collapses equal values`() {
        val oneWay = ReceiptJson.claimValue(
            ClaimValue.Rate(setOf(BigDecimal("8"), BigDecimal("4.5"), BigDecimal("8.0")), RateQualifier.ILLUSTRATIVE),
        )
        val otherWay = ReceiptJson.claimValue(
            ClaimValue.Rate(setOf(BigDecimal("8.00"), BigDecimal("4.50")), RateQualifier.ILLUSTRATIVE),
        )
        assertEquals(canonical(oneWay), canonical(otherWay))
        assertTrue(canonical(oneWay).contains("""["4.5","8"]"""), canonical(oneWay))
    }

    // ------------------------------------------------------------------
    // The chain
    // ------------------------------------------------------------------

    @Test
    fun `a receipt built from a session verifies`() {
        val receipt = receipt()
        assertTrue(ReceiptBuilder.verify(receipt).passed)
        // h0, one per event, then the closing head.
        assertEquals(receipt.canonicalEvents.size + 2, receipt.hashes.size)
        assertEquals(receipt.hashes.last(), receipt.head)
    }

    @Test
    fun `§7_1 - tampering with any byte of any event fails verification`() {
        val receipt = receipt()
        // Every event, and in each one the first character of the text, the
        // last, and one in the middle. A chain that only notices edits in some
        // positions is not a chain.
        receipt.canonicalEvents.indices.forEach { index ->
            val original = receipt.canonicalEvents[index]
            listOf(0, original.length / 2, original.length - 1).forEach { at ->
                val flipped = original.substring(0, at) + flip(original[at]) + original.substring(at + 1)
                if (flipped == original) return@forEach
                val edited = receipt.canonicalEvents.toMutableList().also { it[index] = flipped }
                val check = ReceiptBuilder.verify(receipt.copy(canonicalEvents = edited))
                assertFalse(check.passed, "editing character $at of event $index went unnoticed")
            }
        }
    }

    @Test
    fun `§7_1 - reordering events fails verification`() {
        val receipt = receipt()
        val swapped = receipt.canonicalEvents.toMutableList()
        // The two spoken observations: different content, same shape, so this
        // is a genuine reorder rather than a disguised edit.
        val first = swapped.indexOfFirst { it.contains(ReceiptJson.EVENT_SPOKEN_OBSERVED) }
        val second = swapped.indexOfLast { it.contains(ReceiptJson.EVENT_SPOKEN_OBSERVED) }
        assertNotEquals(first, second, "the fixture needs two distinct spoken events for this test to mean anything")
        val held = swapped[first]
        swapped[first] = swapped[second]
        swapped[second] = held

        assertFalse(ReceiptBuilder.verify(receipt.copy(canonicalEvents = swapped)).passed)
    }

    @Test
    fun `dropping an event fails verification`() {
        val receipt = receipt()
        val shorter = receipt.canonicalEvents.dropLast(1)
        assertFalse(ReceiptBuilder.verify(receipt.copy(canonicalEvents = shorter)).passed)
    }

    @Test
    fun `h0 covers the session id, the start time and the app version`() {
        val receipt = receipt()
        assertFalse(ReceiptBuilder.verify(receipt.copy(sessionId = "session_other")).passed)
        assertFalse(ReceiptBuilder.verify(receipt.copy(startedAtMs = receipt.startedAtMs + 1)).passed)
        assertFalse(ReceiptBuilder.verify(receipt.copy(appVersion = "9.9.9")).passed)
    }

    @Test
    fun `the head covers the closing record, not only the events`() {
        val receipt = receipt()
        // Each of these is outside §7.1's literal event chain. All four must
        // still break the head — see ReceiptBuilder's KDoc.
        assertFalse(ReceiptBuilder.verify(receipt.copy(deviceModel = "some other phone")).passed)
        assertFalse(ReceiptBuilder.verify(receipt.copy(endedAtMs = receipt.endedAtMs + 1)).passed)

        val editedLedger = receipt.entries.map { entry ->
            if (entry.type == ClaimType.GUARANTEE) entry.copy(state = DeltaState.MATCHES) else entry
        }
        assertFalse(ReceiptBuilder.verify(receipt.copy(entries = editedLedger)).passed)

        val droppedLedger = receipt.entries.filterNot { it.type == ClaimType.GUARANTEE }
        assertFalse(ReceiptBuilder.verify(receipt.copy(entries = droppedLedger)).passed)
    }

    @Test
    fun `editing a hash fails verification, including the head itself`() {
        val receipt = receipt()
        receipt.hashes.indices.forEach { index ->
            val edited = receipt.hashes.toMutableList()
            edited[index] = flipHex(edited[index])
            assertFalse(ReceiptBuilder.verify(receipt.copy(hashes = edited)).passed, "hash $index went unnoticed")
        }
    }

    @Test
    fun `a failed check says which step broke`() {
        val receipt = receipt()
        val edited = receipt.canonicalEvents.toMutableList()
        edited[1] = edited[1].replace("\"8\"", "\"9\"")
        val check = ReceiptBuilder.verify(receipt.copy(canonicalEvents = edited))
        val failed = check as ChainCheck.Failed
        assertTrue(failed.reason.contains("event 2"), failed.reason)
    }

    @Test
    fun `signing does not disturb the chain and the signature is not inside the head`() {
        val receipt = receipt()
        val signed = receipt.signedWith(SignatureBlock("c2ln", listOf("Y2VydA=="), strongBox = false))
        assertEquals(receipt.head, signed.head)
        assertTrue(ReceiptBuilder.verify(signed).passed)
        assertTrue(signed.canonicalJson().contains(""""strongBox":false"""))
    }

    @Test
    fun `building twice from the same session gives byte-identical output`() {
        assertEquals(receipt().canonicalJson(), receipt().canonicalJson())
    }

    @Test
    fun `the exported document embeds the same event text the chain hashed`() {
        val receipt = receipt()
        val json = receipt.canonicalJson()
        receipt.canonicalEvents.forEach { event ->
            assertTrue(json.contains(event), "receipt.json does not contain the hashed text of $event")
        }
        assertTrue(json.contains(""""schema":"${ReceiptBuilder.SCHEMA}""""))
        assertTrue(json.contains(""""head":"${receipt.head}""""))
    }

    @Test
    fun `a session id with the h0 separator in it is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            HashChain.genesis("session_a|b", startEpochMs = 0, appVersion = "1.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HashChain.genesis("session_a", startEpochMs = 0, appVersion = "1.0|beta")
        }
    }

    @Test
    fun `sha256 of a known string matches the published digest`() {
        // The standard SHA-256("abc") test vector — proves the hex encoding is
        // right way round and unpadded, which a self-consistent chain would not.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashChain.sha256Hex("abc"),
        )
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /** A short session: two spoken claims, a written one, the scan, a re-check. */
    private fun receipt(): Receipt = ReceiptBuilder.build(
        sessionId = "session_2026-09-13T04-12-00",
        appVersion = "0.1.0",
        deviceModel = "vivo I2501",
        startedAtMs = 1_789_000_000_000,
        endedAtMs = 1_789_000_420_000,
        events = listOf(
            ReconcilerEvent.SpokenObserved(
                spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), "guaranteed eight percent", 0),
            ),
            ReconcilerEvent.SpokenObserved(
                spoken(
                    ClaimType.RETURN_RATE,
                    ClaimValue.Rate(setOf(BigDecimal("8")), RateQualifier.ASSERTED),
                    "eight percent return",
                    4_000,
                ),
            ),
            ReconcilerEvent.WrittenObserved(
                written(ClaimType.GUARANTEE, ClaimValue.Guarantee(false), "Guaranteed Returns: No"),
            ),
            ReconcilerEvent.DocumentScanCompleted,
            ReconcilerEvent.UserRecheck(ClaimType.GUARANTEE),
        ),
        ledger = mapOf(
            ClaimType.GUARANTEE to LedgerEntry(
                type = ClaimType.GUARANTEE,
                spoken = spoken(ClaimType.GUARANTEE, ClaimValue.Guarantee(true), "guaranteed eight percent", 0),
                written = listOf(written(ClaimType.GUARANTEE, ClaimValue.Guarantee(false), "Guaranteed Returns: No")),
                state = DeltaState.DIFFERS,
                reason = ReasonCode.TOLERANCE_EXCEEDED,
                mentionCount = 1,
                dismissed = false,
            ),
            ClaimType.BUNDLING to LedgerEntry(
                type = ClaimType.BUNDLING,
                spoken = null,
                written = emptyList(),
                state = DeltaState.PENDING,
                reason = ReasonCode.NO_SPOKEN,
                mentionCount = 0,
                dismissed = false,
            ),
        ),
    )

    private fun spoken(type: ClaimType, value: ClaimValue, span: String, at: Long) = Observation(
        id = "spoken-$type-$at",
        source = Source.SPOKEN,
        type = type,
        value = value,
        hedged = false,
        negated = false,
        conditional = false,
        confidence = 0.9,
        provenance = Provenance.Spoken(span, at, at + 2_000, "SHERPA_WHISPER_TA"),
        tMs = at,
    )

    private fun written(type: ClaimType, value: ClaimValue, lineText: String) = Observation(
        id = "written-$type",
        source = Source.WRITTEN,
        type = type,
        value = value,
        hedged = false,
        negated = false,
        conditional = false,
        confidence = 0.95,
        provenance = Provenance.Written(lineText, Box(10, 20, 300, 60), "page_1", "crops/page1_row0.jpg"),
        tMs = 60_000,
    )

    /** A different character of the same kind, so the edit stays valid JSON-ish text. */
    private fun flip(char: Char): Char = when {
        char.isDigit() -> if (char == '9') '8' else char + 1
        char.isLetter() -> if (char == 'z') 'y' else char + 1
        else -> char
    }

    private fun flipHex(hash: String): String =
        (if (hash[0] == '0') '1' else '0') + hash.substring(1)
}
