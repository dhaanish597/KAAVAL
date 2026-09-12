package app.vaakku.domain.extract

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.OcrLine
import app.vaakku.domain.model.RateQualifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Task 1 (P3 OCR plan) — confronts [WrittenExtractor] with the **real** prop
 * document (`testdata/prop/_Document.pdf`, read with `pdftotext -layout` and
 * transcribed here verbatim), not the hypothesised §11.2 lines the rest of
 * the suite and the demo fixtures (D01-D06, H01-H04) were written against.
 *
 * Pages 5 and 6 are the scan *session* that together carry all five present
 * clauses (constraints-and-context.md, "Consequence for the human's scan
 * protocol": no single page carries all five). BUNDLING is deliberately
 * absent from the whole test: the real document is silent on loans (a
 * case-insensitive grep for `voluntary|loan|mandatory|independent` across
 * all 10 pages of the extracted text finds nothing at all).
 *
 * Every [OcrLine] below is the verbatim text of one visual line of the real
 * document (page 5: §5 year-by-year table [header + 3 representative rows —
 * none of the other 7 data rows contain a "%" sign or any of the other
 * regex keywords, so they carry no extra risk and are not worth transcribing
 * in full] + §6; page 6: §7 charges table + §8 lock-in/surrender), in
 * reading order. Genuine two-column/three-column table rows (§7's charge
 * table) are supplied as *separate* OcrLines sharing one top/bottom and
 * differing only in `left` — exactly what ML Kit hands [RowAssembler] for a
 * real table row — everything else is one OcrLine per printed line, each
 * on its own row (top strides far enough apart that RowAssembler never
 * merges two different printed lines).
 */
class RealPropDocumentTest {

    private val extractor = WrittenExtractor()

    /** Lays out verbatim document lines top-to-bottom, one visual row per call, 1.0 confidence throughout. */
    private class PageBuilder(private val frameId: String, startTop: Int) {
        private var top = startTop
        private val step = 40
        private val height = 30
        val lines = mutableListOf<OcrLine>()

        /** One printed line = one row of its own. */
        fun line(text: String) {
            lines += OcrLine(text, Box(40, top, 40 + text.length * 8, top + height), confidence = 1.0, frameId = frameId)
            top += step
        }

        /** Several cells of one printed table row — same top/bottom, left-to-right. */
        fun row(vararg cells: String) {
            var left = 40
            for (cell in cells) {
                val right = left + maxOf(120, cell.length * 8)
                lines += OcrLine(cell, Box(left, top, right, top + height), confidence = 1.0, frameId = frameId)
                left = right + 20
            }
            top += step
        }
    }

    private val page5 = PageBuilder(frameId = "prop_p5", startTop = 0).apply {
        line("Nambikkai Life Insurance Company Limited (specimen) - Benefit Illustration -- Suraksha Savings Plan")
        line("5. Benefit Illustration -- Year by Year")
        line("The table below shows, for each policy year, the premium paid and the illustrative accumulated")
        line("value at the end of that year, under the two assumed rates of return. Figures under \"At 4% p.a.\" and")
        line("\"At 8% p.a.\" are non-guaranteed and depend on the Company's actual investment and other")
        line("experience -- they are not a forecast.")
        row("Policy Year", "Premium for the Year (Rs.)", "Total Premiums Paid (Rs.)", "At 4% p.a. (non-guaranteed)", "At 8% p.a. (non-guaranteed)")
        row("1", "1,20,000", "1,20,000", "1,20,000", "1,20,000")
        row("5", "1,20,000", "6,00,000", "2,44,800", "2,49,600")
        row("10", "1,20,000", "12,00,000", "14,40,733", "17,38,387")
        line("Returns are NOT guaranteed. The 4% and 8% rates are illustrative only.")
        line("6. Guaranteed & Non-Guaranteed Benefits")
        line("Guaranteed Benefits")
        line("In the event of the death of the life assured during the policy term, while the policy is in force, the")
        line("Company will pay the Sum Assured on Death stated in Section 4, subject to the terms and")
        line("conditions of the policy. Guaranteed Returns on premiums paid: No.")
        line("Non-Guaranteed Benefits")
        line("On survival to the end of the policy term, the maturity benefit payable depends on the Company's")
        line("actual experience with investment returns, mortality, expenses and other factors -- it is not")
        line("guaranteed. The 4% p.a. and 8% p.a. figures in Section 5 are illustrative scenarios prepared in line")
        line("with IRDAI guidelines. They are not a promise, estimate, or guarantee of future benefits.")
        line("SPECIMEN DOCUMENT -- prepared for a hackathon demonstration only, not a real product or offer Page 5 of 10")
    }.lines

    // RowAssembler groups purely by vertical Box position — it has no notion of
    // frameId or page boundaries (that is a real design point of the row
    // assembler, not a test artifact). Two different photos/frames legitimately
    // reuse the same y-coordinate range, so page 6's lines get a top offset far
    // past everything page 5 uses, exactly as two genuinely separate frames
    // would never be mistaken for the same visual row.
    private val page6 = PageBuilder(frameId = "prop_p6", startTop = 5000).apply {
        line("Nambikkai Life Insurance Company Limited (specimen) - Benefit Illustration -- Suraksha Savings Plan")
        line("7. Charges")
        row("Charge", "When Deducted", "Rate")
        row("Premium Allocation Charge", "Year 1", "5% of Annualised Premium")
        row("Premium Allocation Charge", "Years 2-5", "2% of Annualised Premium")
        row("Premium Allocation Charge", "Year 6 onward", "Nil")
        row("Policy Administration Charge", "Throughout the Policy Term", "As per the Company's published rates, deducted monthly")
        row("Mortality Charge", "Throughout the Policy Term", "Based on age, sum at risk and health, deducted monthly")
        line("This table is simplified for this specimen document. The complete charge structure of a real product is set out in its policy")
        line("document -- not in the benefit illustration handed to you at the point of sale.")
        line("8. Surrender Value & Lock-in")
        line("Lock-in Period: 5 years from the date of commencement of the policy.")
        line("Surrender Value: Nil before completion of the 5th policy year. If you discontinue paying premiums")
        line("and surrender the policy on or after completion of the 5th policy year, a Surrender Value becomes")
        line("payable:")
        line("Guaranteed Surrender Value -- a percentage of total premiums paid (excluding the first year's")
        line("premium), increasing with the number of years completed, as specified in the policy document.")
        line("Special Surrender Value, if any -- determined by the Company from time to time based on its")
        line("actual experience, and may be higher than the Guaranteed Surrender Value.")
        line("Surrendering the policy before the end of the Policy Term results in a lower benefit than shown in")
        line("Section 5, and is generally not in your interest as a long-term saver.")
        line("SPECIMEN DOCUMENT -- prepared for a hackathon demonstration only, not a real product or offer Page 6 of 10")
    }.lines

    /** One scan session (§6.4) = page 5 + page 6, in that order — matches the human's scan protocol. */
    private val observations = extractor.extract(page5 + page6)

    @Test
    fun `RETURN_RATE - illustrative 4 and 8 percent is observed`() {
        val rate = observations.filter { it.type == ClaimType.RETURN_RATE }
        assertTrue(rate.isNotEmpty(), "expected at least one RETURN_RATE observation from pages 5+6")
        assertTrue(
            rate.any { it.value == ClaimValue.Rate(setOf(BigDecimal("4"), BigDecimal("8")), RateQualifier.ILLUSTRATIVE) },
            "expected a Rate({4,8}, ILLUSTRATIVE) among: ${rate.map { it.value }}",
        )
    }

    @Test
    fun `GUARANTEE - false is observed (including via the fixed §6 defect sentence)`() {
        val guarantee = observations.filter { it.type == ClaimType.GUARANTEE }
        assertTrue(guarantee.isNotEmpty(), "expected at least one GUARANTEE observation from pages 5+6")
        assertTrue(
            guarantee.any { it.value == ClaimValue.Guarantee(false) },
            "expected a Guarantee(false) among: ${guarantee.map { it.value }}",
        )
        // The real document never states guaranteed returns are Yes anywhere on these
        // pages — a stray Guarantee(true) here would mean a regex is reading
        // something it shouldn't.
        assertTrue(guarantee.none { it.value == ClaimValue.Guarantee(true) })
    }

    @Test
    fun `LOCK_IN - 60 months is observed, exactly once`() {
        val obs = observations.single { it.type == ClaimType.LOCK_IN }
        assertEquals(ClaimValue.LockIn(60), obs.value)
    }

    @Test
    fun `LIQUIDITY - surrenderNilBeforeMonths 60 is observed, exactly once`() {
        val obs = observations.single { it.type == ClaimType.LIQUIDITY }
        assertEquals(ClaimValue.Liquidity(withdrawableAfterMonths = null, surrenderNilBeforeMonths = 60), obs.value)
    }

    @Test
    fun `CHARGES - the §7 table's three Premium Allocation Charge rows behave exactly as documented in matchCharges' KDoc`() {
        val charges = observations.filter { it.type == ClaimType.CHARGES }
        // 5% (year 1) and 2% (years 2-5) each produce their own observation;
        // the "Nil" (year 6 onward) row produces none. Nothing else on pages
        // 5-6 has a "%"/currency figure next to a recognised charge label
        // (Policy Administration and Mortality are both unquantified prose),
        // so this must be the complete list.
        assertEquals(2, charges.size, "expected exactly 2 CHARGES observations, got: ${charges.map { it.value }}")
        assertTrue(charges.any { (it.value as ClaimValue.Charges).percent == BigDecimal("5") })
        assertTrue(charges.any { (it.value as ClaimValue.Charges).percent == BigDecimal("2") })
    }

    @Test
    fun `BUNDLING - no observation at all, the document is silent on loans`() {
        assertTrue(observations.none { it.type == ClaimType.BUNDLING })
    }
}
