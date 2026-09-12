'use strict';

const crypto = require('node:crypto');
const { canonical, FormatError } = require('./canonical');

/**
 * Independent verification of a receipt's hash chain and signature — build plan
 * §7.1, §7.2, driven by §7.4 step 1.
 *
 * ### What a PASSED line means, and what it does not
 *
 * It means this file is byte-for-byte the file the phone exported: no event
 * edited, none dropped, none reordered, and no change to the session details or
 * the final ledger printed beside them.
 *
 * It does not mean anything at all about whether what was said was true, and
 * nothing whatsoever about the person who said it. This tool checks that a
 * record was not altered after it was written. That is the whole claim.
 */

/** The only receipt format this tool knows how to check. */
const SCHEMA = 'vaakku.receipt.1';

const SEPARATOR = '|';

function sha256Hex(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex');
}

/** `h0 = SHA-256(sessionId | startEpochMs | appVersion)`. */
function genesis(sessionId, startEpochMs, appVersion) {
  return sha256Hex(`${sessionId}${SEPARATOR}${startEpochMs}${SEPARATOR}${appVersion}`);
}

/** `h_i = SHA-256(h_{i-1} || canonical(event_i))`, with `h_{i-1}` as its hex text. */
function step(previousHex, canonicalEvent) {
  return sha256Hex(previousHex + canonicalEvent);
}

/**
 * The closing record whose hash is the head.
 *
 * `schema` is this tool's own constant rather than the file's field. That is
 * not an oversight: it means a receipt whose schema line was edited cannot
 * quietly re-derive its own head under new rules. The file's field is checked
 * separately by [verifyChain], which refuses a format it was not written for
 * instead of verifying it under the wrong ones and printing a confident wrong
 * answer.
 */
function closingRecord(receipt) {
  return {
    schema: SCHEMA,
    sessionId: receipt.sessionId,
    appVersion: receipt.appVersion,
    deviceModel: receipt.deviceModel,
    startedAtMs: receipt.startedAtMs,
    endedAtMs: receipt.endedAtMs,
    entries: receipt.entries,
  };
}

function requireString(receipt, field) {
  if (typeof receipt[field] !== 'string') {
    throw new FormatError(`receipt.${field} must be a string, found ${describe(receipt[field])}`);
  }
}

function requireArray(receipt, field) {
  if (!Array.isArray(receipt[field])) {
    throw new FormatError(`receipt.${field} must be an array, found ${describe(receipt[field])}`);
  }
}

function describe(value) {
  if (value === undefined) return 'nothing';
  if (value === null) return 'null';
  return Array.isArray(value) ? 'an array' : typeof value;
}

/**
 * Recomputes the whole chain and returns `{ passed, reason }`.
 *
 * `reason` names bytes and indices only — an event number, a field name. It is
 * printed on the packet verbatim, so it describes the document and never a
 * person.
 */
function verifyChain(receipt) {
  for (const field of ['schema', 'sessionId', 'appVersion', 'deviceModel', 'startedAtMs', 'endedAtMs', 'head']) {
    requireString(receipt, field);
  }
  for (const field of ['events', 'hashes', 'entries']) {
    requireArray(receipt, field);
  }

  if (receipt.schema !== SCHEMA) {
    // Not a chain failure and deliberately not reported as one: this tool
    // cannot say whether such a file is intact, and saying FAILED would read
    // as a claim against a receipt that may be perfectly sound.
    throw new FormatError(
      `this receipt is format ${JSON.stringify(receipt.schema)}; this tool verifies ${JSON.stringify(SCHEMA)}`,
    );
  }

  const expectedHashes = receipt.events.length + 2;
  if (receipt.hashes.length !== expectedHashes) {
    return fail(`expected ${expectedHashes} hashes for ${receipt.events.length} event(s), found ${receipt.hashes.length}`);
  }

  const expectedGenesis = genesis(receipt.sessionId, receipt.startedAtMs, receipt.appVersion);
  if (!sameHash(receipt.hashes[0], expectedGenesis)) {
    return fail('h0 does not match sessionId|startEpochMs|appVersion');
  }

  // Re-canonicalizing each parsed event is the cross-language check itself. The
  // phone hashed the text it wrote; this rebuilds that text from the parsed
  // object using rules written independently of the phone's. If the two
  // canonicalizers disagree anywhere — a sort order, an escape, a stray space —
  // the bytes differ, the hash differs, and this line fails. There is no way to
  // pass it by accident.
  for (let i = 0; i < receipt.events.length; i += 1) {
    const expected = step(receipt.hashes[i], canonical(receipt.events[i]));
    if (!sameHash(receipt.hashes[i + 1], expected)) {
      return fail(`event ${i + 1} of ${receipt.events.length} does not match h${i + 1}`);
    }
  }

  const lastEventHash = receipt.hashes[receipt.hashes.length - 2];
  const expectedHead = step(lastEventHash, canonical(closingRecord(receipt)));
  if (!sameHash(receipt.head, expectedHead)) {
    return fail('the head does not match the session details and the ledger printed with them');
  }
  if (!sameHash(receipt.head, receipt.hashes[receipt.hashes.length - 1])) {
    return fail('the head is not the last hash in the chain');
  }
  return { passed: true, reason: null };
}

function fail(reason) {
  return { passed: false, reason };
}

function sameHash(a, b) {
  return typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();
}

/**
 * §7.2's signature over the head, checked with the public key of the first
 * certificate.
 *
 * Returns one of:
 *  - `{ state: 'absent' }` — no signature block. Ordinary for a receipt built
 *    on the laptop, and stated on the packet as plainly as a good one is.
 *  - `{ state: 'verified', strongBox, subject, notAfter }`
 *  - `{ state: 'broken', reason }`
 *
 * ### The limit of this check, written down because the packet has to say it
 *
 * A verified signature proves the head was signed by the private key belonging
 * to the first certificate in the chain. It does not prove that key is inside
 * the phone's secure hardware. That would need the rest of the chain walked to
 * Google's hardware attestation root, and this tool is offline and does not
 * carry that root. So `strongBox` is reported as what it is — a field the phone
 * wrote — and the packet labels it that way rather than as something verified
 * here (CLAUDE.md #8).
 */
function verifySignature(receipt) {
  const block = receipt.signature;
  if (block === null || block === undefined) return { state: 'absent' };
  if (typeof block !== 'object' || Array.isArray(block)) {
    return { state: 'broken', reason: 'the signature block is not an object' };
  }
  if (typeof block.signature !== 'string' || !Array.isArray(block.certChain) || block.certChain.length === 0) {
    return { state: 'broken', reason: 'the signature block has no signature or no certificate' };
  }

  let certificate;
  try {
    certificate = new crypto.X509Certificate(Buffer.from(block.certChain[0], 'base64'));
  } catch (error) {
    return { state: 'broken', reason: `the first certificate could not be read (${error.message})` };
  }

  let ok;
  try {
    ok = crypto
      .createVerify('SHA256')
      // §7.2 signs `head`. `head` is a 64-character hex string, and it is those
      // characters that are signed — not the 32 bytes they spell. The phone
      // signs the same ASCII (ReceiptFixtureRunner.sign), so there is no second
      // convention to get wrong on either side.
      .update(receipt.head, 'ascii')
      .verify(certificate.publicKey, Buffer.from(block.signature, 'base64'));
  } catch (error) {
    return { state: 'broken', reason: `the signature could not be checked (${error.message})` };
  }
  if (!ok) {
    return { state: 'broken', reason: 'the signature does not match the head of this receipt' };
  }
  return {
    state: 'verified',
    strongBox: block.strongBox === true,
    subject: certificate.subject,
    notAfter: certificate.validTo,
  };
}

/**
 * The two checks folded into the one line §7.4 prints.
 *
 * ### Why a broken signature is a failure and a missing one is not
 *
 * The chain proves a file is consistent with its own hashes. It does not prove
 * who computed them — anybody can edit an event and recompute every hash after
 * it, and the result passes [verifyChain] cleanly. What they cannot do is
 * produce a signature over the new head, because that needs a key that never
 * leaves the phone.
 *
 * So a receipt with a good chain and a signature that does not match is not a
 * lesser problem than a broken chain. It is the case the signature exists to
 * catch, and it is reported as a failure.
 *
 * No signature at all is a different thing and stays PASSED: a receipt built on
 * a laptop has none, and §7.2's key lives on a phone. The packet says which one
 * it is rather than implying a missing signature is a broken one.
 */
function overallIntegrity(chain, signature) {
  if (!chain.passed) return { passed: false, reason: chain.reason };
  if (signature.state === 'broken') return { passed: false, reason: signature.reason };
  return { passed: true, reason: null };
}

module.exports = {
  verifyChain,
  verifySignature,
  overallIntegrity,
  genesis,
  step,
  sha256Hex,
  closingRecord,
  SCHEMA,
};
