'use strict';

/**
 * Canonical JSON for the receipt hash chain — build plan §7.1, used by §7.4.
 *
 * ### This is a second implementation, not a port
 *
 * `domain/src/main/kotlin/app/vaakku/domain/receipt/CanonicalJson.kt` does the
 * same job in Kotlin. Nothing here was translated from it, because a
 * translation would reproduce that file's mistakes faithfully and then agree
 * with itself — which is exactly the thing a cross-language check is supposed
 * to be unable to do. Both were written from the same written rule instead, and
 * `:domain:receiptFixture` plus `--in evidence/receipt_fixture` is where they
 * are made to meet.
 *
 * ### The rule
 *
 *  - Object keys sorted by UTF-16 code unit. No whitespace anywhere.
 *  - Every leaf is a string, a boolean or null. There are no JSON numbers in a
 *    receipt at all — see [assertNoNumbers].
 *  - `"` and `\` take their two-character escapes; U+0008, U+0009, U+000A,
 *    U+000C and U+000D take `\b \t \n \f \r`; anything else below U+0020
 *    becomes lowercase `\u00xx`; everything else is emitted raw, so a Tamil
 *    clause canonicalizes to the clause itself in UTF-8.
 */

/** A receipt that is not in the shape §7.1 guarantees. Not a chain failure. */
class FormatError extends Error {
  constructor(message) {
    super(message);
    this.name = 'FormatError';
  }
}

const SHORT_ESCAPES = new Map([
  ['"', '\\"'],
  ['\\', '\\\\'],
  ['\b', '\\b'],
  ['\t', '\\t'],
  ['\n', '\\n'],
  ['\f', '\\f'],
  ['\r', '\\r'],
]);

/** `value` as canonical JSON. */
function canonical(value) {
  const out = [];
  write(value, out);
  return out.join('');
}

function write(value, out) {
  if (value === null) {
    out.push('null');
    return;
  }
  if (Array.isArray(value)) {
    out.push('[');
    value.forEach((item, index) => {
      if (index > 0) out.push(',');
      write(item, out);
    });
    out.push(']');
    return;
  }
  switch (typeof value) {
    case 'string':
      out.push(quote(value));
      return;
    case 'boolean':
      out.push(value ? 'true' : 'false');
      return;
    case 'number':
      // The format has no number case on purpose: Java prints `1.0` where
      // JavaScript prints `1`, and a chain hashed over that difference would
      // report a genuine receipt as broken. A number reaching here means the
      // file was not produced by ReceiptJson, and picking digits for it is the
      // one thing that must not happen quietly.
      throw new FormatError(
        `found the JSON number ${value} — every value in a receipt is a string, a boolean or null`,
      );
    case 'object':
      writeObject(value, out);
      return;
    default:
      throw new FormatError(`cannot canonicalize a value of type ${typeof value}`);
  }
}

function writeObject(value, out) {
  // Default `sort()` already compares by UTF-16 code unit; the comparator is
  // written out so the ordering is a stated decision rather than a default
  // somebody could change by passing a locale-aware compare.
  const keys = Object.keys(value).sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
  out.push('{');
  keys.forEach((key, index) => {
    assertAsciiKey(key);
    if (index > 0) out.push(',');
    out.push(quote(key));
    out.push(':');
    write(value[key], out);
  });
  out.push('}');
}

/**
 * The Kotlin canonicalizer sorts keys with `String.compareTo` and its KDoc
 * notes that this matches JavaScript only because every key the format emits is
 * ASCII — then says "the CLI asserts that assumption rather than relying on it
 * silently". This is that assertion.
 *
 * The two orderings genuinely diverge above the BMP: Kotlin compares UTF-16
 * code units, and so does JavaScript, but a future key outside ASCII would make
 * the agreement depend on details neither side has tested. Refusing the key is
 * cheaper than discovering it from a receipt that will not verify.
 */
function assertAsciiKey(key) {
  for (let i = 0; i < key.length; i += 1) {
    const code = key.charCodeAt(i);
    if (code > 0x7e || code < 0x20) {
      throw new FormatError(
        `the key ${JSON.stringify(key)} is not printable ASCII; key ordering is only defined for ASCII keys`,
      );
    }
  }
}

function quote(text) {
  let out = '"';
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    const short = SHORT_ESCAPES.get(char);
    if (short !== undefined) {
      out += short;
    } else if (text.charCodeAt(i) < 0x20) {
      out += '\\u' + text.charCodeAt(i).toString(16).padStart(4, '0');
    } else {
      out += char;
    }
  }
  return out + '"';
}

/**
 * Walks a parsed receipt and rejects any JSON number, before the chain is
 * checked.
 *
 * [canonical] would refuse one anyway, but only on the branch that happens to
 * reach it — a number in a field the chain does not cover would slip through
 * and then be rendered onto paper as whatever JavaScript thinks it looks like.
 * One pass up front says so plainly and names the path.
 */
function assertNoNumbers(value, path = 'receipt') {
  if (value === null) return;
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoNumbers(item, `${path}[${index}]`));
    return;
  }
  if (typeof value === 'number') {
    throw new FormatError(`${path} is the JSON number ${value}; every value in a receipt is a string, a boolean or null`);
  }
  if (typeof value === 'object') {
    for (const key of Object.keys(value)) assertNoNumbers(value[key], `${path}.${key}`);
  }
}

module.exports = { canonical, assertNoNumbers, FormatError };
