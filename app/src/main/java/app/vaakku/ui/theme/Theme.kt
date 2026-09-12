package app.vaakku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// The three voices (design spec §3.3)
//
// This is the product encoded typographically: a spoken claim is set in sans, a
// written clause in serif. On a Delta Card you can tell which half is which
// without reading a word of it. Getting these backwards inverts the metaphor, so
// they are named by role rather than by typeface.
// ---------------------------------------------------------------------------

/** Spoken claims, UI, buttons — everything by default. */
val VoiceSpeech: FontFamily = NotoSansTamil

/** Clause text, quoted document language, the grievance packet. */
val VoiceDocument: FontFamily = NotoSerifTamil

/** Rates, timestamps, clause references, ledger values. Genuinely tabular only. */
val VoiceRecord: FontFamily = IbmPlexMono

// ---------------------------------------------------------------------------
// Colour
// ---------------------------------------------------------------------------

/**
 * The resolved palette for one theme. Held in a [staticCompositionLocalOf] so a
 * screen reads `VaakkuTheme.colors.stamp` rather than a top-level `Stamp`, which
 * would silently stay in Day colours when Night is active.
 */
@Immutable
data class VaakkuColors(
    val paper: Color,
    val sheet: Color,
    val canary: Color,
    val canaryDeep: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val onInk: Color,
    val brand: Color,
    val brandDeep: Color,
    val onBrand: Color,
    val onBrandSoft: Color,
    val rule: Color,
    val ruleStrong: Color,
    val stamp: Color,
    val stampWash: Color,
    val destructive: Color,
    val scrim: Color,
    val scrimSheet: Color,
    val isNight: Boolean,
)

val DayColors = VaakkuColors(
    paper = Paper,
    sheet = Sheet,
    canary = Canary,
    canaryDeep = CanaryDeep,
    ink = Ink,
    inkSoft = InkSoft,
    inkFaint = InkFaint,
    onInk = OnInk,
    brand = Brand,
    brandDeep = BrandDeep,
    onBrand = OnBrand,
    onBrandSoft = OnBrandSoft,
    rule = Rule,
    ruleStrong = RuleStrong,
    stamp = Stamp,
    stampWash = StampWash,
    destructive = Destructive,
    scrim = Scrim,
    scrimSheet = ScrimSheet,
    isNight = false,
)

val NightColors = VaakkuColors(
    paper = PaperNight,
    sheet = SheetNight,
    canary = CanaryNight,
    canaryDeep = CanaryDeepNight,
    ink = InkNight,
    inkSoft = InkSoftNight,
    inkFaint = InkFaintNight,
    onInk = OnInkNight,
    brand = BrandNight,
    brandDeep = BrandDeepNight,
    onBrand = OnBrandNight,
    onBrandSoft = OnBrandSoftNight,
    rule = RuleNight,
    ruleStrong = RuleStrongNight,
    stamp = StampNight,
    stampWash = StampWashNight,
    destructive = DestructiveNight,
    scrim = ScrimNight,
    scrimSheet = ScrimSheetNight,
    isNight = true,
)

val LocalVaakkuColors = staticCompositionLocalOf { DayColors }

// ---------------------------------------------------------------------------
// Type scale (design spec §3.3)
//
// Line heights are the spec's multipliers resolved to sp. Tamil needs them: at
// 1.5 the superscript marks and deep descenders of ஆ ொ ூ ஞ collide. Minimum body
// size is 17sp rather than the usual 16 — the reader skews older and is reading
// under stress.
// ---------------------------------------------------------------------------

/**
 * Tamil must never be letter-spaced (it breaks ligature shaping and looks
 * illiterate) and never uppercased (Tamil has no case). Both are enforced by
 * simply never setting them here — if you find yourself adding `letterSpacing`
 * to a style that can carry Tamil, that is the bug.
 */
private val tamilLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Immutable
data class VaakkuType(
    /** Alert value, Tamil, single line only. */
    val displayXl: TextStyle,
    /** Delta Card statements — the one line the whole product exists to show. */
    val display: TextStyle,
    /** Screen titles in Record Mode. */
    val headline: TextStyle,
    /** Section headers, session summary counts. */
    val title: TextStyle,
    /** Readable claim text in Record Mode. */
    val bodyLg: TextStyle,
    /** Default body. */
    val body: TextStyle,
    /** Tamil body — extra leading. */
    val bodyTamil: TextStyle,
    /** Field labels, buttons. */
    val label: TextStyle,
    /** Metadata, timestamps. */
    val caption: TextStyle,
    /** Status chips only. */
    val micro: TextStyle,
    /** Tabular values: rates, timestamps, clause refs. */
    val mono: TextStyle,
    /** Mono at label size, for a value sitting on a field line. */
    val monoValue: TextStyle,
)

val VaakkuTypeScale = VaakkuType(
    displayXl = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 59.8.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    display = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 50.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    headline = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 39.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    title = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.4.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    bodyLg = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 32.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    body = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.2.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    bodyTamil = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 29.75.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    label = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    caption = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.2.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    micro = TextStyle(
        fontFamily = VoiceSpeech,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.2.sp,
        lineHeightStyle = tamilLineHeight,
    ),
    mono = TextStyle(
        fontFamily = VoiceRecord,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.2.sp,
    ),
    monoValue = TextStyle(
        fontFamily = VoiceRecord,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
)

val LocalVaakkuType = staticCompositionLocalOf { VaakkuTypeScale }

/**
 * Material3's own scale, mapped onto the tokens above so that stock components
 * (Button, TextField) inherit the right faces instead of falling back to Roboto
 * and quietly breaking the Tamil.
 */
val VaakkuTypography = Typography(
    displayLarge = VaakkuTypeScale.displayXl,
    displayMedium = VaakkuTypeScale.display,
    headlineMedium = VaakkuTypeScale.headline,
    titleLarge = VaakkuTypeScale.title,
    titleMedium = VaakkuTypeScale.label,
    bodyLarge = VaakkuTypeScale.body,
    bodyMedium = VaakkuTypeScale.bodyTamil,
    labelLarge = VaakkuTypeScale.label,
    labelMedium = VaakkuTypeScale.caption,
    labelSmall = VaakkuTypeScale.micro,
)

/** Kept for the debug readouts that were written against it. */
val MonoStyle = VaakkuTypeScale.mono

// ---------------------------------------------------------------------------
// Space, radius, elevation (design spec §3.5)
// ---------------------------------------------------------------------------

/**
 * The near-square radii are load-bearing, not a style preference: uniform 12–16dp
 * rounding is the SaaS-card default and reads as generic. Paper has corners.
 */
@Immutable
data class VaakkuSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val base: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val gutter: Dp = 20.dp,
    val safeTop: Dp = 24.dp,
    val safeBottom: Dp = 48.dp,
    val radiusField: Dp = 2.dp,
    val radiusCard: Dp = 4.dp,
    val radiusButton: Dp = 6.dp,
    val radiusSheet: Dp = 16.dp,
    val hairline: Dp = 1.dp,
    val ruleEmphasis: Dp = 2.dp,
    /** Minimum touch target. Applies even when the glyph is smaller. */
    val touchTarget: Dp = 48.dp,
)

val LocalVaakkuSpacing = staticCompositionLocalOf { VaakkuSpacing() }

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

/**
 * Accessor object so call sites read `VaakkuTheme.colors.stamp`.
 */
object VaakkuTheme {
    val colors: VaakkuColors
        @Composable @ReadOnlyComposable get() = LocalVaakkuColors.current

    val type: VaakkuType
        @Composable @ReadOnlyComposable get() = LocalVaakkuType.current

    val space: VaakkuSpacing
        @Composable @ReadOnlyComposable get() = LocalVaakkuSpacing.current
}

/**
 * Night is a real theme now (the prototype ships both), but it is **not** the demo
 * default: Day looks more like paper and photographs better under stage lighting.
 * [night] is therefore an explicit parameter rather than a straight read of
 * [isSystemInDarkTheme] — the demo must not flip because the phone happened to be
 * in dark mode.
 */
@Composable
fun VaakkuTheme(
    night: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (night) NightColors else DayColors

    val scheme = if (night) {
        darkColorScheme(
            primary = colors.stamp,
            onPrimary = colors.onInk,
            secondary = colors.brand,
            onSecondary = colors.onBrand,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.sheet,
            onSurfaceVariant = colors.inkSoft,
            outline = colors.rule,
            outlineVariant = colors.rule,
            error = colors.destructive,
        )
    } else {
        lightColorScheme(
            primary = colors.stamp,
            onPrimary = colors.onInk,
            secondary = colors.brand,
            onSecondary = colors.onBrand,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.sheet,
            onSurfaceVariant = colors.inkSoft,
            outline = colors.rule,
            outlineVariant = colors.rule,
            error = colors.destructive,
        )
    }

    CompositionLocalProvider(
        LocalVaakkuColors provides colors,
        LocalVaakkuType provides VaakkuTypeScale,
        LocalVaakkuSpacing provides VaakkuSpacing(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = VaakkuTypography,
            content = content,
        )
    }
}
