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
import java.io.File
import java.math.BigDecimal
import java.security.KeyStore
import java.security.Signature
import java.util.Base64

/**
 * Entry point for `:domain:receiptFixture` — writes the receipts that
 * `tools/packet-cli/` is tested against (build plan §7.1, §7.4).
 *
 * ### Why a fixture and not just unit tests
 *
 * [HashChainTest] proves the chain is self-consistent: this code can verify
 * what this code produced. That is worth nothing for §7.4, where a *different
 * implementation in a different language* has to arrive at the same 64 hex
 * characters. The only way to know the two agree is to have one write a file
 * and the other check it, so this runner writes the file.
 *
 * Three artefacts, all committed:
 *  - `receipt.json` — a short session, unsigned. The CLI must print
 *    `INTEGRITY: PASSED`.
 *  - `receipt_tampered.json` — the same session with one digit of one spoken
 *    claim changed, and nothing else. The CLI must print `INTEGRITY: FAILED`,
 *    naming that event. This is §13's G7 tamper test as a command rather than a
 *    paragraph.
 *  - `receipt_signed.json` — written only when a keystore is passed (see
 *    [main]). It carries a real ECDSA signature over a real certificate chain,
 *    so the CLI's signature path is exercised on the laptop instead of waiting
 *    for a phone.
 *
 * ### Deterministic on purpose
 *
 * Every value below is a literal: no `System.currentTimeMillis()`, no random
 * ids, no clock. Re-running this task on any machine must produce byte-identical
 * files, or the diff would be noise and nobody would read it.
 */
fun main(args: Array<String>) {
    val outDir = File(findRepoRootFromCwd(), "evidence/receipt_fixture")
    outDir.mkdirs()

    val receipt = fixtureReceipt()
    val builderCheck = ReceiptBuilder.verify(receipt)
    check(builderCheck.passed) { "the fixture receipt does not verify against its own builder: $builderCheck" }

    File(outDir, "receipt.json").writeText(receipt.canonicalJson(), Charsets.UTF_8)

    val tampered = tamper(receipt)
    check(!ReceiptBuilder.verify(tampered).passed) { "the tampered fixture still verifies — the tamper is not a tamper" }
    File(outDir, "receipt_tampered.json").writeText(tampered.canonicalJson(), Charsets.UTF_8)

    val signedJson = if (args.size >= 2) sign(receipt, keystore = File(args[0]), password = args[1]) else null
    if (signedJson != null) {
        File(outDir, "receipt_signed.json").writeText(signedJson, Charsets.UTF_8)
    }

    println("receiptFixture: wrote ${outDir.path}/receipt.json (${receipt.canonicalEvents.size} events, head=${receipt.head.take(12)}…)")
    println("receiptFixture: wrote ${outDir.path}/receipt_tampered.json (8% → 9% in the spoken rate, hashes untouched)")
    println(
        if (signedJson != null) {
            "receiptFixture: wrote ${outDir.path}/receipt_signed.json (real ECDSA signature over the head)"
        } else {
            "receiptFixture: no keystore argument — receipt_signed.json left as it is. " +
                "Run scripts/receipt_fixture_signed.sh to regenerate it."
        },
    )
}

/**
 * Changes the spoken rate from 8% to 9%, in the event *and* in the matching
 * ledger entry, leaving every hash exactly as it was.
 *
 * Both halves matter. Editing only the event would leave a file that disagrees
 * with itself, which a careful reader could catch without any cryptography —
 * so the chain would not be what found it. Editing both produces a document
 * that is internally consistent and reads as true: 9% spoken against the 4%/8%
 * illustration. The only thing wrong with it is that `h2` is the hash of a
 * sentence that no longer appears in the file, and that is precisely the thing
 * §7.4's `INTEGRITY:` line exists to notice.
 *
 * The edit is anchored on the observation's id rather than on a bare `"8"`,
 * which also appears in the written scenarios and in timestamps.
 */
private fun tamper(receipt: Receipt): Receipt {
    val events = receipt.canonicalEvents.map { event ->
        if (event.contains(SPOKEN_RATE_ID)) event.replace(SPOKEN_PERCENTS, TAMPERED_PERCENTS) else event
    }
    check(events != receipt.canonicalEvents) {
        "nothing was changed — the fixture no longer contains $SPOKEN_PERCENTS in event $SPOKEN_RATE_ID"
    }

    val entries = receipt.entries.map { entry ->
        val spoken = entry.spoken
        if (spoken?.id != SPOKEN_RATE_ID) {
            entry
        } else {
            entry.copy(
                spoken = spoken.copy(
                    value = ClaimValue.Rate(setOf(BigDecimal("9")), RateQualifier.ASSERTED),
                ),
            )
        }
    }
    check(entries != receipt.entries) { "the ledger was not edited — no entry has a spoken observation $SPOKEN_RATE_ID" }

    return receipt.copy(canonicalEvents = events, entries = entries)
}

private const val SPOKEN_RATE_ID = "spoken-rate-1"
private const val SPOKEN_PERCENTS = """"percents":["8"]"""
private const val TAMPERED_PERCENTS = """"percents":["9"]"""

/**
 * Signs [receipt]'s head with the first private key in [keystore] and returns
 * the signed document.
 *
 * `SHA256withECDSA` over the head's **ASCII hex characters**, not the 32 bytes
 * they encode. §7.2 says "sign `head`", and `head` is a string in this format —
 * signing the text is the reading that needs no further convention, and it is
 * what the CLI can reproduce from the file without re-deriving anything.
 */
private fun sign(receipt: Receipt, keystore: File, password: String): String? {
    if (!keystore.isFile) {
        System.err.println("receiptFixture: no keystore at ${keystore.path}; skipping the signed fixture")
        return null
    }
    val store = KeyStore.getInstance("PKCS12")
    keystore.inputStream().use { store.load(it, password.toCharArray()) }
    val alias = store.aliases().asSequence().first { store.isKeyEntry(it) }
    val key = store.getKey(alias, password.toCharArray()) as java.security.PrivateKey

    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(key)
    signer.update(receipt.head.toByteArray(Charsets.US_ASCII))
    val signature = Base64.getEncoder().encodeToString(signer.sign())

    val chain = store.getCertificateChain(alias).map { Base64.getEncoder().encodeToString(it.encoded) }
    return receipt.signedWith(SignatureBlock(signature, chain, strongBox = false)).canonicalJson()
}

/**
 * A session with one DIFFERS, one NOT_IN_DOCUMENT, a re-check and a silent
 * claim — the shapes §7.4's packet has to render, so the CLI is tested against
 * the document it will actually be given rather than a minimal one.
 *
 * The numbers are §11.2's demo values: "guaranteed eight percent" spoken
 * against "Guaranteed Returns: No" written.
 */
private fun fixtureReceipt(): Receipt = ReceiptBuilder.build(
    sessionId = "session_2026-09-13T04-12-00",
    appVersion = "0.1.0",
    deviceModel = "vivo I2501",
    // 2026-09-13 04:12 IST, which is what that session id says, and seven
    // minutes later. The session id is minted from local time; these are epoch
    // milliseconds. Picking a round number instead would have printed a packet
    // whose header disagreed with its own file name — a small wrongness, but
    // this is the document somebody carries to a grievance officer.
    startedAtMs = 1_789_252_920_000,
    endedAtMs = 1_789_253_340_000,
    events = listOf(
        ReconcilerEvent.SpokenObserved(guaranteeSpoken()),
        ReconcilerEvent.SpokenObserved(rateSpoken()),
        ReconcilerEvent.SpokenObserved(bundlingSpoken()),
        ReconcilerEvent.WrittenObserved(guaranteeWritten()),
        ReconcilerEvent.WrittenObserved(rateWritten()),
        ReconcilerEvent.DocumentScanCompleted,
        ReconcilerEvent.UserRecheck(ClaimType.GUARANTEE),
    ),
    ledger = mapOf(
        ClaimType.RETURN_RATE to LedgerEntry(
            type = ClaimType.RETURN_RATE,
            spoken = rateSpoken(),
            written = listOf(rateWritten()),
            state = DeltaState.MATCHES,
            reason = ReasonCode.VALUE_IN_SCENARIOS,
            mentionCount = 1,
            dismissed = false,
        ),
        ClaimType.GUARANTEE to LedgerEntry(
            type = ClaimType.GUARANTEE,
            spoken = guaranteeSpoken(),
            written = listOf(guaranteeWritten()),
            state = DeltaState.DIFFERS,
            reason = ReasonCode.TOLERANCE_EXCEEDED,
            mentionCount = 2,
            dismissed = false,
        ),
        ClaimType.LOCK_IN to LedgerEntry(
            type = ClaimType.LOCK_IN,
            spoken = null,
            written = emptyList(),
            state = DeltaState.PENDING,
            reason = ReasonCode.NO_SPOKEN,
            mentionCount = 0,
            dismissed = false,
        ),
        ClaimType.LIQUIDITY to LedgerEntry(
            type = ClaimType.LIQUIDITY,
            spoken = null,
            written = emptyList(),
            state = DeltaState.UNCERTAIN,
            reason = ReasonCode.SPOKEN_LOW_CONF,
            mentionCount = 0,
            dismissed = false,
        ),
        ClaimType.BUNDLING to LedgerEntry(
            type = ClaimType.BUNDLING,
            spoken = bundlingSpoken(),
            written = emptyList(),
            state = DeltaState.NOT_IN_DOCUMENT,
            reason = ReasonCode.DOC_SILENT,
            mentionCount = 1,
            dismissed = false,
        ),
        ClaimType.CHARGES to LedgerEntry(
            type = ClaimType.CHARGES,
            spoken = null,
            written = emptyList(),
            state = DeltaState.PENDING,
            reason = ReasonCode.NO_SPOKEN,
            mentionCount = 0,
            dismissed = false,
        ),
    ),
)

private fun guaranteeSpoken() = Observation(
    id = "spoken-guarantee-1",
    source = Source.SPOKEN,
    type = ClaimType.GUARANTEE,
    value = ClaimValue.Guarantee(true),
    hedged = false,
    negated = false,
    conditional = false,
    confidence = 0.91,
    provenance = Provenance.Spoken("guaranteed எட்டு percent return", 12_000, 15_500, "SHERPA_WHISPER_TA"),
    tMs = 12_000,
)

private fun rateSpoken() = Observation(
    id = "spoken-rate-1",
    source = Source.SPOKEN,
    type = ClaimType.RETURN_RATE,
    value = ClaimValue.Rate(setOf(BigDecimal("8")), RateQualifier.ASSERTED),
    hedged = false,
    negated = false,
    conditional = false,
    confidence = 0.88,
    provenance = Provenance.Spoken("guaranteed எட்டு percent return", 12_000, 15_500, "SHERPA_WHISPER_TA"),
    tMs = 12_000,
)

private fun bundlingSpoken() = Observation(
    id = "spoken-bundling-1",
    source = Source.SPOKEN,
    type = ClaimType.BUNDLING,
    value = ClaimValue.Bundling(requiredForLoan = true),
    hedged = false,
    negated = false,
    conditional = false,
    confidence = 0.86,
    provenance = Provenance.Spoken("loan வேண்டும் என்றால் இது compulsory", 96_000, 99_000, "SHERPA_WHISPER_TA"),
    tMs = 96_000,
)

private fun guaranteeWritten() = Observation(
    id = "written-guarantee-1",
    source = Source.WRITTEN,
    type = ClaimType.GUARANTEE,
    value = ClaimValue.Guarantee(false),
    hedged = false,
    negated = false,
    conditional = false,
    confidence = 0.96,
    provenance = Provenance.Written(
        lineText = "Guaranteed Returns: No. The returns shown are illustrative.",
        box = Box(148, 902, 1_612, 968),
        frameId = "page_2",
        cropFile = "crops/page2_row4.jpg",
    ),
    tMs = 180_000,
)

private fun rateWritten() = Observation(
    id = "written-rate-1",
    source = Source.WRITTEN,
    type = ClaimType.RETURN_RATE,
    value = ClaimValue.Rate(setOf(BigDecimal("4"), BigDecimal("8")), RateQualifier.ILLUSTRATIVE),
    hedged = false,
    negated = false,
    conditional = false,
    confidence = 0.94,
    provenance = Provenance.Written(
        lineText = "Illustrative scenarios: 4% p.a. and 8% p.a. are not guaranteed.",
        box = Box(148, 1_004, 1_598, 1_070),
        frameId = "page_2",
        cropFile = "crops/page2_row5.jpg",
    ),
    tMs = 180_000,
)

/** The task sets `workingDir` to the repo root; the walk makes that a default rather than a requirement. */
private fun findRepoRootFromCwd(): File {
    var dir: File? = File(".").absoluteFile
    while (dir != null) {
        if (File(dir, "settings.gradle.kts").isFile) return dir
        dir = dir.parentFile
    }
    error("could not find the repo root from ${File(".").absolutePath}")
}
