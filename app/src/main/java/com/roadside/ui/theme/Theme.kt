package com.roadside.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadside.R

/**
 * "Kinetic Automotive Instrument" — the Stitch design system, expressed as Compose tokens.
 *
 * DESIGN.md is the source of truth; this file is a translation of its front-matter, not a
 * transcription of the exported HTML. Screens compose these tokens instead of hard-coding
 * colours, radii or spacing, so the system stays consistent rather than being re-invented
 * per screen.
 *
 * Depth here is tonal, not shadow-based: surfaces step from [RoadSideColors.canvas] up
 * through [RoadSideColors.containerHigh]. Outlines are used sparingly — only where an edge
 * carries meaning (inputs, secondary controls) — never as decoration.
 */

// ── Colour ───────────────────────────────────────────────────────────────────

object RoadSideColors {
    /** Chassis base — the recessed ground layer behind everything. */
    val canvas = Color(0xFF111316)

    /** Recessed wells: waveform troughs, camera viewport, inset readouts. */
    val containerLowest = Color(0xFF0C0E11)

    /** Instrument pod — the foundational casing that groups related readouts. */
    val containerLow = Color(0xFF1A1C1F)

    /** Active instrument surface — tactile cards and telemetry modules. */
    val container = Color(0xFF1E2023)

    /** Touch surfaces — buttons, segmented switches, pill selectors. */
    val containerHigh = Color(0xFF282A2D)
    val containerHighest = Color(0xFF333538)

    val onSurface = Color(0xFFE2E2E6)
    val onSurfaceVariant = Color(0xFFA48C7E)

    /** Signature Safety Amber. Primary mechanisms only — never a passive fill. */
    val amber = Color(0xFFE57A24)
    val amberPressed = Color(0xFFCC6518)

    /** Lighter amber for accent text and icons, where the solid fill would shout. */
    val primary = Color(0xFFFFB787)
    val onAmber = Color(0xFF111316)

    val secondary = Color(0xFFB7C8E1)

    /** Telemetry semantics — strict automotive convention. */
    val safe = Color(0xFF10B981)
    val advisory = Color(0xFFD97706)
    val critical = Color(0xFFDC2626)

    val error = Color(0xFFFFB4AB)

    /** Soft ghost perimeter. Used only where an edge is meaningful. */
    val outlineSubtle = Color(0x14FFFFFF)
    val outlinePronounced = Color(0x24FFFFFF)
}

// ── Typography ───────────────────────────────────────────────────────────────

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Technical readouts only: sensor values, durations, codes, pill labels. */
private val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold)
)

@Suppress("unused")
class RoadSideTypography(
    val headlineXl: TextStyle,
    val headlineXlMobile: TextStyle,
    val headlineLg: TextStyle,
    val headlineMd: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val bodySm: TextStyle,
    val labelDataLg: TextStyle,
    val labelDataMd: TextStyle,
    val labelDataSm: TextStyle
)

private fun inter(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = (size * tracking).sp,
    // Keeps glyphs from jittering while a value animates.
    textMotion = TextMotion.Animated
)

private fun mono(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = Mono, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = (size * tracking).sp,
    textMotion = TextMotion.Animated
)

val RoadSideType = RoadSideTypography(
    headlineXl = inter(32, 40, FontWeight.Bold, -0.02),
    headlineXlMobile = inter(26, 34, FontWeight.Bold, -0.01),
    headlineLg = inter(22, 30, FontWeight.SemiBold, -0.01),
    headlineMd = inter(18, 26, FontWeight.SemiBold, -0.005),
    bodyLg = inter(16, 24, FontWeight.Normal, 0.0),
    bodyMd = inter(14, 20, FontWeight.Normal, 0.0),
    bodySm = inter(12, 18, FontWeight.Normal, 0.01),
    labelDataLg = mono(18, 24, FontWeight.SemiBold, -0.02),
    labelDataMd = mono(13, 18, FontWeight.Medium, 0.02),
    labelDataSm = mono(11, 16, FontWeight.SemiBold, 0.04)
)

// ── Shape & spacing ──────────────────────────────────────────────────────────

/** Generous, continuous curvature. Every surface in the app comes from this set. */
object RoadSideShapes {
    val pill = RoundedCornerShape(percent = 50)
    /** Touch controls and inputs. */
    val control = RoundedCornerShape(20.dp)
    /** Nested wells inside a card. */
    val well = RoundedCornerShape(20.dp)
    /** Instrument cards. */
    val card = RoundedCornerShape(24.dp)
    /** Master enclosures — the largest containers on a screen. */
    val enclosure = RoundedCornerShape(28.dp)
}

object Space {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 36.dp
    /** Outer page gutter. */
    val margin: Dp = 20.dp
    /** Minimum touch height — gloved hands, vibrating vehicle. */
    val touchTarget: Dp = 52.dp
}

// ── Theme ────────────────────────────────────────────────────────────────────

val LocalRoadSideType = staticCompositionLocalOf { RoadSideType }

private val DarkScheme = darkColorScheme(
    primary = RoadSideColors.amber,
    onPrimary = RoadSideColors.onAmber,
    primaryContainer = RoadSideColors.amber,
    onPrimaryContainer = RoadSideColors.onAmber,
    secondary = RoadSideColors.secondary,
    tertiary = RoadSideColors.safe,
    background = RoadSideColors.canvas,
    onBackground = RoadSideColors.onSurface,
    surface = RoadSideColors.canvas,
    onSurface = RoadSideColors.onSurface,
    surfaceVariant = RoadSideColors.containerHigh,
    onSurfaceVariant = RoadSideColors.onSurfaceVariant,
    surfaceContainer = RoadSideColors.container,
    surfaceContainerLow = RoadSideColors.containerLow,
    surfaceContainerLowest = RoadSideColors.containerLowest,
    surfaceContainerHigh = RoadSideColors.containerHigh,
    surfaceContainerHighest = RoadSideColors.containerHighest,
    error = RoadSideColors.error,
    outline = RoadSideColors.onSurfaceVariant,
    outlineVariant = RoadSideColors.outlineSubtle
)

/**
 * The app is a dark automotive instrument in every condition — it does not follow the system
 * light theme, and [isSystemInDarkTheme] is deliberately not consulted.
 */
@Composable
fun RoadSideTheme(content: @Composable () -> Unit) {
    val m3 = Typography(
        displayLarge = RoadSideType.headlineXl,
        headlineLarge = RoadSideType.headlineXlMobile,
        headlineMedium = RoadSideType.headlineLg,
        headlineSmall = RoadSideType.headlineMd,
        titleLarge = RoadSideType.headlineMd,
        titleMedium = RoadSideType.bodyLg,
        titleSmall = RoadSideType.labelDataSm,
        bodyLarge = RoadSideType.bodyLg,
        bodyMedium = RoadSideType.bodyMd,
        bodySmall = RoadSideType.bodySm,
        labelLarge = RoadSideType.labelDataMd,
        labelMedium = RoadSideType.labelDataSm,
        labelSmall = RoadSideType.labelDataSm
    )
    CompositionLocalProvider(LocalRoadSideType provides RoadSideType) {
        MaterialTheme(colorScheme = DarkScheme, typography = m3, content = content)
    }
}
