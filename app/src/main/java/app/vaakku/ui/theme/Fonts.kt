package app.vaakku.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.vaakku.R

/**
 * Font families (build plan §6.6).
 *
 * Tamil text is set in Noto Sans Tamil / Noto Serif Tamil; English and numbers in
 * IBM Plex Sans / Serif / Mono. The families are named separately rather than
 * merged so that a screen can state which script it is setting, instead of
 * relying on the platform's glyph fallback to guess.
 *
 * All files are static weights downloaded from the fonts' official upstreams
 * (Google Noto, IBM Plex) and are under the SIL Open Font License — see
 * licenses/ at the repo root. Note for whoever revisits this: the font bundle the
 * build plan expected (docs/kaaval-fonts.zip) was not present on this machine, and
 * the copies inside the pre-event prototype repo had all four Tamil weights
 * byte-identical (one file duplicated), so they were not reused. These are fresh.
 *
 * Each family exposes the four weights the UI actually uses. Do not add a weight
 * without adding a matching .ttf to app/src/main/res/font/ — Compose will
 * silently synthesise a faux-bold instead of failing.
 */

/** Tamil script. Also carries Latin glyphs, so it is a safe whole-string face. */
val NotoSansTamil = FontFamily(
    Font(R.font.notosanstamil_regular, FontWeight.Normal),
    Font(R.font.notosanstamil_medium, FontWeight.Medium),
    Font(R.font.notosanstamil_semibold, FontWeight.SemiBold),
    Font(R.font.notosanstamil_bold, FontWeight.Bold),
)

/** Tamil script, serif. Used for the one large delta line — it reads as print. */
val NotoSerifTamil = FontFamily(
    Font(R.font.notoseriftamil_regular, FontWeight.Normal),
    Font(R.font.notoseriftamil_semibold, FontWeight.SemiBold),
    Font(R.font.notoseriftamil_bold, FontWeight.Bold),
)

/** Latin and numerals. */
val IbmPlexSans = FontFamily(
    Font(R.font.ibmplexsans_regular, FontWeight.Normal),
    Font(R.font.ibmplexsans_medium, FontWeight.Medium),
    Font(R.font.ibmplexsans_semibold, FontWeight.SemiBold),
    Font(R.font.ibmplexsans_bold, FontWeight.Bold),
)

/** Latin and numerals, serif. */
val IbmPlexSerif = FontFamily(
    Font(R.font.ibmplexserif_regular, FontWeight.Normal),
    Font(R.font.ibmplexserif_semibold, FontWeight.SemiBold),
    Font(R.font.ibmplexserif_bold, FontWeight.Bold),
)

/**
 * Monospace, for the things that are measurements rather than prose: the NPU
 * latency label, the debug overlay, timestamps. Digits line up, which matters
 * when the reader is comparing two numbers.
 */
val IbmPlexMono = FontFamily(
    Font(R.font.ibmplexmono_regular, FontWeight.Normal),
    Font(R.font.ibmplexmono_medium, FontWeight.Medium),
    Font(R.font.ibmplexmono_semibold, FontWeight.SemiBold),
    Font(R.font.ibmplexmono_bold, FontWeight.Bold),
)
