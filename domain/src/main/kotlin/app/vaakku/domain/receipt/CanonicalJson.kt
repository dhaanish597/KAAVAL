package app.vaakku.domain.receipt

/**
 * Canonical JSON — build plan §7.1: "sorted keys, no whitespace, UTF-8".
 *
 * ### Why this exists at all
 *
 * The hash chain hashes *text*. Two programs therefore have to agree on the
 * exact bytes of that text: this module, and `tools/packet-cli/` in Node
 * (§7.4). Anywhere they disagree by one byte, a genuine receipt verifies as
 * broken — which is the worst possible failure for an integrity check, because
 * it teaches the person holding it to ignore the result.
 *
 * `kotlinx.serialization` is already on this module's classpath and is not used
 * here on purpose. Its output is pretty-printable, key order follows the class
 * declaration, and its number formatting is its own business — none of which is
 * a promise, and all of which would have to keep matching `JSON.stringify` in a
 * different language and runtime. A hundred lines that cannot drift is the
 * cheaper thing to own.
 *
 * ### Every leaf is a string, a boolean, or null
 *
 * There is no number case in [JsonValue]. That is the decision that removes the
 * whole class of cross-language float-formatting bugs: Java prints `1.0` where
 * JavaScript prints `1`, `BigDecimal("8.0")` and `BigDecimal("8")` are equal
 * numbers with different digits, and `toFixed` rounds ties by the binary value
 * while `HALF_UP` rounds by the decimal one. A receipt is a record, not a
 * calculation — nothing downstream does arithmetic on these fields, the packet
 * renders them and the CLI re-hashes them. So the producer decides the digits
 * once, in [app.vaakku.domain.receipt.ReceiptJson], and everyone after that
 * handles exactly those characters.
 *
 * ### The escaping rule
 *
 * Deliberately identical to ECMA-262 `JSON.stringify`, which is what Node will
 * be using on the other side:
 *  - `"` and `\` take their two-character escapes;
 *  - U+0008, U+0009, U+000A, U+000C, U+000D take `\b \t \n \f \r`;
 *  - any other code unit below U+0020 becomes `\u00xx`, lowercase hex;
 *  - **everything else is emitted raw**, including Tamil, so the canonical form
 *    of a Tamil clause is the clause itself in UTF-8 rather than a wall of
 *    `க`.
 *
 * One known limit, recorded rather than papered over: an unpaired surrogate
 * would be written raw here and escaped by `JSON.stringify` (ES2019's
 * well-formed-stringify change). Text in a receipt comes from ASR and OCR,
 * which produce well-formed strings, and [asString] would have to be handed a
 * broken one deliberately. If it ever happens the chain fails closed — it does
 * not verify — which is the correct direction for this to fail in.
 */
sealed interface JsonValue {

    /** A JSON string. The only leaf that carries text or a number's digits. */
    data class Str(val value: String) : JsonValue

    /** A JSON `true`/`false`. Flags stay flags; nothing else is a boolean. */
    data class Bool(val value: Boolean) : JsonValue

    /** A JSON `null` — "this field has no value", never "the value is zero". */
    data object Null : JsonValue

    /** A JSON array. Order is the caller's and is preserved exactly. */
    data class Arr(val items: List<JsonValue>) : JsonValue

    /**
     * A JSON object. Insertion order is irrelevant: [canonical] sorts the keys,
     * so two objects built by different code paths with the same content have
     * the same canonical bytes.
     */
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue {
        constructor(vararg pairs: Pair<String, JsonValue>) : this(pairs.toMap())
    }

    /**
     * Already-canonical JSON text, emitted verbatim.
     *
     * One use only: embedding an event's canonical string into `receipt.json`
     * without parsing it (see `Receipt.embeddedEvent`). The string that was
     * hashed and the string in the file are then the same characters by
     * construction rather than by both sides agreeing — which is the property
     * the chain depends on.
     *
     * This case can obviously break canonicality, so it refuses anything that
     * is not a JSON object or array. That does not prove the content is
     * canonical — nothing short of a parser would — but it does stop this from
     * being used as a general "insert text" escape hatch, which is the way it
     * would actually get misused.
     */
    data class Raw(val json: String) : JsonValue {
        init {
            val trimmed = json.trim()
            require(trimmed.length == json.length) { "raw JSON must not be padded with whitespace" }
            require(
                (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]")),
            ) {
                "raw JSON must be an object or an array, not a bare value"
            }
        }
    }
}

/**
 * [value] as canonical JSON: keys sorted, no whitespace anywhere.
 *
 * Keys are sorted by [String.compareTo], i.e. by UTF-16 code unit, which is
 * also what a bare `Array.prototype.sort()` does in JavaScript. Every key this
 * project emits is ASCII, so the two orderings cannot diverge here; the CLI
 * asserts that assumption rather than relying on it silently.
 */
fun canonical(value: JsonValue): String = StringBuilder().also { write(value, it) }.toString()

private fun write(value: JsonValue, out: StringBuilder) {
    when (value) {
        is JsonValue.Str -> writeString(value.value, out)
        is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
        JsonValue.Null -> out.append("null")
        is JsonValue.Raw -> out.append(value.json)
        is JsonValue.Arr -> {
            out.append('[')
            value.items.forEachIndexed { index, item ->
                if (index > 0) out.append(',')
                write(item, out)
            }
            out.append(']')
        }
        is JsonValue.Obj -> {
            out.append('{')
            value.fields.entries.sortedBy { it.key }.forEachIndexed { index, (key, field) ->
                if (index > 0) out.append(',')
                writeString(key, out)
                out.append(':')
                write(field, out)
            }
            out.append('}')
        }
    }
}

private fun writeString(text: String, out: StringBuilder) {
    out.append('"')
    for (char in text) {
        when {
            char == '"' -> out.append("\\\"")
            char == '\\' -> out.append("\\\\")
            char == '\b' -> out.append("\\b")
            char == '\t' -> out.append("\\t")
            char == '\n' -> out.append("\\n")
            char.code == 0x0C -> out.append("\\f")
            char == '\r' -> out.append("\\r")
            char < ' ' -> {
                out.append("\\u")
                val hex = char.code.toString(16)
                repeat(4 - hex.length) { out.append('0') }
                out.append(hex)
            }
            else -> out.append(char)
        }
    }
    out.append('"')
}
