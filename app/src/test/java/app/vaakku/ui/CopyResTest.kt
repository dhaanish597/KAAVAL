package app.vaakku.ui

import app.vaakku.domain.copy.CopyBuilder
import app.vaakku.domain.copy.CopyRef
import app.vaakku.domain.copy.CardCopy
import app.vaakku.domain.copy.ValuePhrase
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import java.math.BigDecimal
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The copy contract, checked without a phone.
 *
 * `:domain` emits keys; `strings.xml` holds words; [CopyRes] joins them. Nothing
 * in the compiler connects those three, so a key renamed on either side would
 * otherwise surface as a blank Delta Card in front of a buyer — or, worse, as a
 * `MissingFormatArgumentException` thrown from the one large line the whole
 * product exists to draw.
 *
 * Two checks, in the two directions a break can happen:
 *
 *  1. **Domain → table.** Drive `CopyBuilder` and `ValuePhrase` over every claim
 *     type, every state and every value shape, and require that each key they
 *     emit is in [CopyRes] with the same arity.
 *  2. **Table → strings.xml.** Read the resource file as text and count the
 *     `%n$s` placeholders in every template the table names, so the declared
 *     arity is the arity the words actually have.
 *
 * The second check parses the XML rather than calling `Resources`, because unit
 * tests here run against android.jar stubs (`isReturnDefaultValues = true`) where
 * `getString` returns null. The file is the source of truth either way.
 */
class CopyResTest {

    // ---------------------------------------------------------------- domain →

    @Test
    fun `every state label the domain can emit resolves`() {
        DeltaState.entries.forEach { state ->
            assertKnown(CopyBuilder.stateLabel(state), "stateLabel($state)")
        }
    }

    @Test
    fun `every follow-up question the domain can emit resolves`() {
        ClaimType.entries.forEach { type ->
            assertKnown(CopyBuilder.followUpFor(type), "followUpFor($type)")
        }
    }

    @Test
    fun `every value phrase the domain can emit resolves`() {
        valueShapes().forEach { value ->
            assertKnown(ValuePhrase.forAny(value), "forAny($value)")
        }
        // forGuarantee's co-stated-rate branch is unreachable through forAny,
        // which is exactly why it is the branch most likely to be forgotten.
        assertKnown(
            ValuePhrase.forGuarantee(ClaimValue.Guarantee(true), BigDecimal("8")),
            "forGuarantee(true, 8%)",
        )
    }

    @Test
    fun `a DIFFERS card resolves every key it carries`() {
        val card = CopyBuilder.build(entry(DeltaState.DIFFERS)) as CardCopy.Differs
        assertKnown(card.stateLabel, "stateLabel")
        assertKnown(card.spokenLine.value, "spokenLine value")
        assertKnown(card.writtenLine.value, "writtenLine value")
        assertEquals(1, CopyRes.argCount(card.spokenLine.templateKey), card.spokenLine.templateKey)
        assertEquals(1, CopyRes.argCount(card.writtenLine.templateKey), card.writtenLine.templateKey)
        assertEquals(0, CopyRes.argCount(card.footerKey), card.footerKey)
        // The small English line is the one template the card fills itself, with
        // both value phrases on one row.
        assertEquals(2, CopyRes.argCount(card.englishSmallLineKey), card.englishSmallLineKey)
        assertKnown(card.followUp, "followUp")
    }

    @Test
    fun `a NOT_IN_DOCUMENT card resolves every key it carries`() {
        val card = CopyBuilder.build(entry(DeltaState.NOT_IN_DOCUMENT)) as CardCopy.NotInDocument
        assertKnown(card.stateLabel, "stateLabel")
        assertKnown(card.spokenLine.value, "spokenLine value")
        assertEquals(1, CopyRes.argCount(card.spokenLine.templateKey), card.spokenLine.templateKey)
        assertEquals(0, CopyRes.argCount(card.hintKey), card.hintKey)
        assertKnown(card.followUp, "followUp")
    }

    // ----------------------------------------------------------- → strings.xml

    @Test
    fun `every key in the table exists in strings dot xml with the declared arity`() {
        val templates = parseTemplates()
        val missing = CopyRes.keys().filter { it !in templates }
        assertTrue(missing.isEmpty(), "keys in CopyRes with no resource in strings.xml: $missing")

        CopyRes.keys().forEach { key ->
            val declared = CopyRes.argCount(key)!!
            // Both languages, and every plural form within each: a `one` form
            // that lost its placeholder prints the sentence without the number.
            listOfNotNull(key, "${key}_en".takeIf { it in templates }).forEach { name ->
                templates.getValue(name).forEach { (variant, text) ->
                    assertEquals(
                        declared,
                        placeholderCount(text),
                        "$name ($variant): CopyRes declares $declared argument(s) but the template is \"$text\"",
                    )
                }
            }
        }
    }

    @Test
    fun `every Tamil template has an English companion`() {
        val templates = parseTemplates()
        // card_differs_english_small_line is English in both modes by design and
        // CopyRes points both ids at it — see the comment on its table entry.
        val exempt = setOf("card_differs_english_small_line")
        val orphans = CopyRes.keys()
            .filter { it !in exempt }
            .filter { "${it}_en" !in templates }
        assertTrue(orphans.isEmpty(), "copy keys with no _en companion in strings.xml: $orphans")
    }

    // ------------------------------------------------------------------ helpers

    private fun assertKnown(ref: CopyRef, what: String) {
        val arity = CopyRes.argCount(ref.key)
        assertNotNull(arity, "$what emits '${ref.key}', which CopyRes cannot resolve")
        assertEquals(ref.args.size, arity, "$what emits '${ref.key}' with ${ref.args.size} arg(s)")
    }

    /** One of every [ClaimValue] branch that [ValuePhrase] treats differently. */
    private fun valueShapes(): List<ClaimValue> = listOf(
        ClaimValue.Rate(setOf(BigDecimal("4"), BigDecimal("8")), RateQualifier.ILLUSTRATIVE),
        ClaimValue.Rate(setOf(BigDecimal("8")), RateQualifier.ASSERTED),
        ClaimValue.Guarantee(true),
        ClaimValue.Guarantee(false),
        ClaimValue.LockIn(60),
        ClaimValue.LockIn(12),
        ClaimValue.LockIn(18),
        ClaimValue.Liquidity(withdrawableAfterMonths = 60, surrenderNilBeforeMonths = null),
        ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 60),
        // Not whole years: a separate key family, and the one this list originally
        // missed — every liquidity shape here was an exact number of years, so the
        // `_months` templates could have been absent from the table without any
        // test noticing. LockIn(18) above is the same thought, three lines earlier.
        ClaimValue.Liquidity(withdrawableAfterMonths = 18, surrenderNilBeforeMonths = null),
        ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 30),
        ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = null),
        ClaimValue.Bundling(requiredForLoan = true),
        ClaimValue.Bundling(requiredForLoan = false),
        ClaimValue.Charges(anyCharges = false, percent = null, label = null),
        ClaimValue.Charges(anyCharges = true, percent = BigDecimal("5"), label = "Premium allocation"),
        ClaimValue.Charges(anyCharges = true, percent = null, label = "Premium allocation"),
    )

    private fun observation(source: Source, value: ClaimValue, type: ClaimType) = Observation(
        id = "${source.name.lowercase()}-1",
        source = source,
        type = type,
        value = value,
        hedged = false,
        negated = false,
        conditional = false,
        confidence = 0.9,
        provenance = when (source) {
            Source.SPOKEN -> Provenance.Spoken("guaranteed eight percent", 0, 1_000, "test")
            Source.WRITTEN -> Provenance.Written("Guaranteed Returns: No", Box(0, 0, 10, 10), "page_1", null)
        },
        tMs = 0,
    )

    private fun entry(state: DeltaState) = LedgerEntry(
        type = ClaimType.GUARANTEE,
        spoken = observation(Source.SPOKEN, ClaimValue.Guarantee(true), ClaimType.GUARANTEE),
        written = if (state == DeltaState.DIFFERS) {
            listOf(observation(Source.WRITTEN, ClaimValue.Guarantee(false), ClaimType.GUARANTEE))
        } else {
            emptyList()
        },
        state = state,
        reason = if (state == DeltaState.DIFFERS) ReasonCode.TOLERANCE_EXCEEDED else ReasonCode.DOC_SILENT,
        mentionCount = 1,
        dismissed = false,
    )

    /**
     * `strings.xml` as `name -> (variant -> template text)`. A `<plurals>`
     * contributes one entry per `<item>`, keyed by its quantity, because every
     * form has to carry the same placeholders.
     */
    private fun parseTemplates(): Map<String, Map<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile())
        val out = mutableMapOf<String, Map<String, String>>()

        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i) as Element
            out[element.getAttribute("name")] = mapOf("string" to element.textContent)
        }

        val plurals = document.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val element = plurals.item(i) as Element
            val items = element.getElementsByTagName("item")
            val forms = mutableMapOf<String, String>()
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                forms[item.getAttribute("quantity")] = item.textContent
            }
            out[element.getAttribute("name")] = forms
        }
        return out
    }

    /**
     * Gradle runs unit tests with the module directory as the working directory,
     * but that is a default rather than a promise; the walk up makes the test
     * independent of it.
     */
    private fun stringsFile(): File {
        val relative = "app/src/main/res/values/strings.xml"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            val inModule = File(dir, "src/main/res/values/strings.xml")
            if (inModule.isFile) return inModule
            dir = dir.parentFile
        }
        error("could not find $relative from ${File(".").absolutePath}")
    }

    /**
     * How many distinct `%n$s` slots a template has. `%%` is a literal percent
     * sign and is deliberately not matched — several value phrases end in one.
     */
    private fun placeholderCount(text: String): Int =
        Regex("""%(\d+)\$[sd]""").findAll(text).map { it.groupValues[1] }.toSet().size
}
