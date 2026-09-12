package app.vaakku.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Carbon Copy palette (build plan §6.6).
 *
 * Paper, ink, rule, and exactly one accent: the violet stamp. The accent exists
 * for one purpose only — marking a DIFFERS card — and is not a status ramp.
 *
 * There is no red, amber or green in this file, and none may be added
 * (CLAUDE.md #9). If a future state feels like it needs a colour, that is the
 * signal that the state is quietly turning into a ruling; keep it ink-only.
 */

val Paper = Color(0xFFFAF7F0)
val PaperRaised = Color(0xFFFFFDF8)
val Ink = Color(0xFF1F1B2E)
val InkMuted = Color(0xFF6B6577)
val Rule = Color(0xFFD9D3C7)

/** The stamp. Marks a DIFFERS card and nothing else. */
val StampViolet = Color(0xFF5E35B1)
