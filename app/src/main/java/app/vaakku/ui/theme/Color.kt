package app.vaakku.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Carbon Copy palette — tokens transcribed from the v2 prototype
 * (`docs/vaakku_prototype_v2.html`, the `:root` and `[data-theme="night"]` blocks).
 *
 * The prototype supersedes the fallback palette this file used at P0 and the v1
 * values in `docs/VAAKKU_UI_DESIGN_SPEC.md` §3.1: v2 moves to neutral grey paper
 * (`#F4F3F1`, not the green-tinted `#EDEFE9`), near-black ink (`#111111`, not the
 * blue-black `#17242E`), and adds a brand amber that v1 does not contain.
 *
 * ## Why there is an amber in a file that used to say "no amber"
 *
 * CLAUDE.md #9 forbids colour-coding *states* — no red/amber/green ramp, violet
 * reserved for a DIFFERS card. That rule is intact. In the whole v2 prototype
 * amber appears exactly four times: the wordmark square, the offline chip on the
 * setup screen, the offline chip in Session Mode, and the 3dp stripe above the
 * session status strip. It never marks a claim. Violet appears twenty-five times
 * and every one is a delta.
 *
 * So the two accents carry disjoint meanings, and that separation is the thing to
 * protect:
 *
 *   - [Brand] = "this app, and it is offline". Identity and the offline proof.
 *   - [Stamp] = "these two copies differ". Nothing else, ever.
 *
 * Putting amber on a claim — or violet on anything that is not a delta — collapses
 * that distinction and turns the palette back into the risk ramp §1.1 of the design
 * spec argues against. Do not do it.
 */

// ---------------------------------------------------------------------------
// Day (default — demo in Day; it photographs better under stage lighting)
// ---------------------------------------------------------------------------

/** App background — duplicate-form stock. */
val Paper = Color(0xFFF4F3F1)

/** The original copy: cards, sheets, and the *written* half of a Delta Card. */
val Sheet = Color(0xFFFFFFFF)

/** The buyer's copy: the *spoken* half of a Delta Card, and nothing else. */
val Canary = Color(0xFFFBE7BB)

/** Canary one step deeper, for a rule or edge against [Canary]. */
val CanaryDeep = Color(0xFFF5DCA4)

/** Primary text. */
val Ink = Color(0xFF111111)

/** Secondary text and labels. */
val InkSoft = Color(0xFF3A3A3A)

/** Metadata, placeholder, disabled. Never body text. */
val InkFaint = Color(0xFF6C6C6D)

/** Text on an ink-filled surface. */
val OnInk = Color(0xFFF4F3F1)

/** Brand amber. Identity and the offline proof — never a claim state. */
val Brand = Color(0xFFF0B31C)

/** Amber darkened enough to carry text on [Paper]. */
val BrandDeep = Color(0xFFA8790A)

/** Text on an amber fill. */
val OnBrand = Color(0xFF5A4408)

/** Secondary text on an amber fill. */
val OnBrandSoft = Color(0xFF7A5E0B)

/** 1dp hairline — the primary separator in the whole app. */
val Rule = Color(0xFFCFCBC3)

/** Emphasised rule, active field underline, drag handle. */
val RuleStrong = Color(0xFFB8B4AC)

/** Violet stamp ink. THE DELTA. Nothing else. */
val Stamp = Color(0xFF5B3E8E)

/** Violet tint fill, for a delta's background wash. */
val StampWash = Color(0xFFEFE8F8)

/** Delete actions only. Never a verdict, never a state. */
val Destructive = Color(0xFFA03A2B)

// ---------------------------------------------------------------------------
// Night — retonalised, not inverted. Not the demo default.
// ---------------------------------------------------------------------------

val PaperNight = Color(0xFF121212)
val SheetNight = Color(0xFF1E1E1F)
val CanaryNight = Color(0xFF2E2617)
val CanaryDeepNight = Color(0xFF3A2F1B)
val InkNight = Color(0xFFF2F1EE)
val InkSoftNight = Color(0xFFB4B2AE)
val InkFaintNight = Color(0xFF807E7A)
val OnInkNight = Color(0xFF121212)
val BrandNight = Color(0xFFF0B31C)
val BrandDeepNight = Color(0xFFF0B31C)
val OnBrandNight = Color(0xFF2A1F02)
val OnBrandSoftNight = Color(0xFF4A3806)
val RuleNight = Color(0xFF33322F)
val RuleStrongNight = Color(0xFF4C4A45)

/** Lightened so it still reads on a dark ground. */
val StampNight = Color(0xFFC4A9EE)
val StampWashNight = Color(0xFF2A2338)
val DestructiveNight = Color(0xFFE1806F)

// ---------------------------------------------------------------------------
// Scrims and elevation tints
// ---------------------------------------------------------------------------

val Scrim = Color(0x73111111)
val ScrimSheet = Color(0x80111111)
val ScrimNight = Color(0x99000000)
val ScrimSheetNight = Color(0xA6000000)
