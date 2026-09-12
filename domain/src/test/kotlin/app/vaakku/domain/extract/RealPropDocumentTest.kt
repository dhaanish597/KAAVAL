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
 * document (`testdata/prop/_Document.pdf`, read with `pdftotext -layout`),
 * not the hypothesised §11.2 lines the rest of the suite and the demo
 * fixtures (D01-D06, H01-H04) were written against.
 *
 * Pages 5 and 6 are the scan *session* that together carry all five present
 * clauses (constraints-and-context.md, "Consequence for the human's scan
 * protocol": no single page carries all five). BUNDLING is deliberately
 * absent from the whole test: the real document is silent on loans (a
 * case-insensitive grep for `voluntary|loan|mandatory|independent` across
 * all 10 pages of the extracted text finds nothing at all).
 *
 * Every [OcrLine] below is the text of one visual line of the real document
 * (page 5: §5 year-by-year table + §6; page 6: §7 charges table + §8
 * lock-in/surrender), in reading order, **verbatim** except for one
 * disclosed simplification (fix round 1, Important #3 — precisely what was
 * normalised and why, since a claim of "verbatim" must be exact or say where
 * it isn't, CLAUDE.md #7):
 *
 * - The §5 table's **header row is transcribed with its genuine two-line
 *   wrap** (`row("Policy", ...)` then `row("Year", "Year ()", "Paid ()")`,
 *   below) rather than the single merged line an earlier draft used. The
 *   trailing "(, non-" / "guaranteed)" split is not a typo: the PDF's ₹
 *   glyph is dropped entirely by `pdftotext -layout` (confirmed with
 *   `od -c` — zero bytes where the glyph should be, not a replacement
 *   character), leaving "(₹, non-guaranteed)" as "(, non-guaranteed)",
 *   itself then wrapped across the two header lines.
 * - The §7 table's **Policy Administration Charge and Mortality Charge
 *   rows are transcribed with their genuine 2-3 line wrap** of the "Rate"
 *   cell (`row(...)` for the first line, plain `line(...)` calls for the
 *   continuation — see below), instead of being flattened to one line.
 * - **Not** reproduced literally: `pdftotext -layout`'s own column-width
 *   reflow staggers the §5 table's "At 4%/8%" *data* cells away from their
 *   true row. Verified two ways: cross-checking the printed rupee figures
 *   against compound growth at 4%/8% on ₹1,20,000/year to attribute each
 *   value to its true year, and separately counting output lines directly in
 *   `pdftotext -layout -f 5 -l 5 ...` (fix round 3, correcting a wrong count
 *   from fix round 2). The offset between year N's label row
 *   (Policy-Year/Premium/Total-Premium cells) and its true non-guaranteed
 *   values **grows linearly with N, with no plateau**: +2, +3, +4, +5, +6,
 *   +7, +8, +9, +10, +11 output lines for years 1-10 respectively. The
 *   reason it never levels off: label rows are one output line apart
 *   throughout (lines 12-21 in that dump), but the six trailing, label-less
 *   value-only lines that hold years 5-10's figures are *two* output lines
 *   apart — a blank line between each (lines 22, 24, 26, 28, 30, 32) — so the
 *   gap widens by one line every single year, both before and after the
 *   handoff from "value shares a line with a later label" (years 1-4) to
 *   "value has no label on its line at all" (years 5-10). Year 1's values
 *   additionally land on the *same* printed line as the header's wrapped
 *   "guaranteed)" continuation (line 12, its offset-+2 destination). That is
 *   an artifact of the extractor's column-width text-flow, not of the
 *   document (a photo of the real page would show every cell of one printed
 *   row at one vertical position) — reproducing it literally would encode a
 *   tool bug as if it were page content. [PageBuilder.row] below instead
 *   uses three representative data rows (years 1, 5, 10) with their correctly
 *   attributed values, at consistent single-row positions; none of the other
 *   7 data rows (or these three) contain a "%" sign or any other regex
 *   keyword, so this carries no risk to any assertion either way.
 *
 * Genuine two/three-column table rows (§7's charge table, the §5 header) are
 * supplied as *separate* OcrLines sharing one top/bottom and differing only
 * in `left` — exactly what ML Kit hands [RowAssembler] for a real table row
 * — everything else is one OcrLine per printed line, each on its own row
 * (top strides far enough apart that RowAssembler never merges two different
 * printed lines). Page 5 and page 6 each get their own `frameId` and may
 * freely reuse the same `top` range: [RowAssembler] partitions by `frameId`
 * before it clusters by vertical position (fix round 1, Important #2 — see
 * `RowAssemblerTest`'s dedicated regression test for that fix).
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
        // Header, genuine 2-line wrap (see class KDoc) — verbatim per pdftotext -layout.
        row("Policy", "Premium for the", "Total Premiums", "At 4% p.a. (, non-", "At 8% p.a. (, non-")
        row("Year", "Year ()", "Paid ()")
        // Representative data rows (years 1, 5, 10), correctly-attributed non-guaranteed
        // values — see class KDoc for why these are not a line-for-line transcription.
        row("1", "1,20,000", "1,20,000", "1,20,000", "1,20,000")
        row("5", "1,20,000", "6,00,000", "6,49,959", "7,03,992")
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

    // Page 6 reuses the same top range as page 5 — safe, because RowAssembler
    // now partitions by frameId before it clusters by vertical position (fix
    // round 1, Important #2), exactly as a real second photo's own pixel
    // space would start near (0,0) independent of the first photo's.
    private val page6 = PageBuilder(frameId = "prop_p6", startTop = 0).apply {
        line("Nambikkai Life Insurance Company Limited (specimen) - Benefit Illustration -- Suraksha Savings Plan")
        line("7. Charges")
        row("Charge", "When Deducted", "Rate")
        row("Premium Allocation Charge", "Year 1", "5% of Annualised Premium")
        row("Premium Allocation Charge", "Years 2-5", "2% of Annualised Premium")
        row("Premium Allocation Charge", "Year 6 onward", "Nil")
        // Genuine 3-line wrap of the Rate cell (see class KDoc) — verbatim.
        row("Policy Administration Charge", "Throughout the Policy Term", "As per the Company's")
        line("published rates, deducted")
        line("monthly")
        // Genuine 2-line wrap of the Rate cell — verbatim.
        row("Mortality Charge", "Throughout the Policy Term", "Based on age, sum at risk and")
        line("health, deducted monthly")
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
