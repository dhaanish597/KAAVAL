package app.vaakku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography.
 *
 * The size that matters here is [SessionAlert]: Session Mode requires alert text
 * of at least 34 sp, targeting 40 sp (build plan §6.6), because the reader is
 * holding a phone at arm's length in a room where someone is talking to them.
 * Everything else is sized to stay out of that line's way.
 *
 * Line height is generous on the Tamil styles on purpose: Tamil glyphs carry
 * ascenders and descenders that clip against a tight default line box.
 */
private val tamilLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val VaakkuTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NotoSerifTamil,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 56.sp,
        lineHeightStyle = tamilLineHeightStyle,
    ),
    // The one large Tamil delta line.
    displayMedium = TextStyle(
        fontFamily = NotoSerifTamil,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 48.sp,
        lineHeightStyle = tamilLineHeightStyle,
    ),
    headlineMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        lineHeightStyle = tamilLineHeightStyle,
    ),
    titleLarge = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        lineHeightStyle = tamilLineHeightStyle,
    ),
    titleMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        lineHeightStyle = tamilLineHeightStyle,
    ),
    bodyLarge = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = tamilLineHeightStyle,
    ),
    bodyMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = tamilLineHeightStyle,
    ),
    // English secondary lines for the people watching the demo: small, and Latin-only.
    labelMedium = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/** Monospace style for measurements (latency, timestamps, debug readouts). */
val MonoStyle = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/**
 * The theme.
 *
 * Deliberately light-only. Session Mode specifies a light surface (build plan
 * §6.6), and a dark variant would be a second visual language to review with the
 * Tamil copy in the one event window we have. [isSystemInDarkTheme] is read only
 * so the decision is visible in the code rather than implied by its absence.
 */
@Composable
fun VaakkuTheme(content: @Composable () -> Unit) {
    // Session Mode is light-surface by specification; the system setting is
    // intentionally not consulted for colours yet.
    @Suppress("UNUSED_VARIABLE")
    val darkRequested = isSystemInDarkTheme()

    val scheme = lightColorScheme(
        primary = StampViolet,
        onPrimary = PaperRaised,
        secondary = InkMuted,
        onSecondary = PaperRaised,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = PaperRaised,
        onSurfaceVariant = InkMuted,
        outline = Rule,
        outlineVariant = Rule,
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = VaakkuTypography,
        content = content,
    )
}
