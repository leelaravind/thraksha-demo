package com.thraksha.guardian.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 11B design tokens — the single source of visual truth.
 *
 * Values are the **Guardian Prime** design system as published by the approved Stitch
 * project (`projects/11183523763892934666`): dark-first Material 3 with Technical Teal
 * (#00D1FF) as the action colour and Deep Indigo (#4D57FF) family for structural accents.
 * Both the dark and the light role sets are Stitch's own — neither is hand-invented.
 *
 * No screen may hardcode a colour, radius or spacing value. Everything comes from here or
 * from `MaterialTheme.colorScheme`.
 */

// ---------------------------------------------------------------------------
// Material 3 colour roles (Stitch "Guardian Prime")
// ---------------------------------------------------------------------------

internal val ThrakshaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA4E6FF),
    onPrimary = Color(0xFF003543),
    primaryContainer = Color(0xFF00D1FF),
    onPrimaryContainer = Color(0xFF00566A),
    inversePrimary = Color(0xFF00677F),
    secondary = Color(0xFFBFC2FF),
    onSecondary = Color(0xFF0000AC),
    secondaryContainer = Color(0xFF1E24DB),
    onSecondaryContainer = Color(0xFFADB2FF),
    tertiary = Color(0xFFDDDCDE),
    onTertiary = Color(0xFF303032),
    tertiaryContainer = Color(0xFFC1C0C3),
    onTertiaryContainer = Color(0xFF4E4E51),
    background = Color(0xFF121315),
    onBackground = Color(0xFFE3E2E5),
    surface = Color(0xFF121315),
    onSurface = Color(0xFFE3E2E5),
    surfaceVariant = Color(0xFF343537),
    onSurfaceVariant = Color(0xFFBBC9CF),
    surfaceTint = Color(0xFF4CD6FF),
    inverseSurface = Color(0xFFE3E2E5),
    inverseOnSurface = Color(0xFF303033),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF859399),
    outlineVariant = Color(0xFF3C494E),
    surfaceBright = Color(0xFF38393B),
    surfaceDim = Color(0xFF121315),
    surfaceContainerLowest = Color(0xFF0D0E10),
    surfaceContainerLow = Color(0xFF1B1C1E),
    surfaceContainer = Color(0xFF1F2022),
    surfaceContainerHigh = Color(0xFF292A2C),
    surfaceContainerHighest = Color(0xFF343537),
)

internal val ThrakshaLightColorScheme = lightColorScheme(
    primary = Color(0xFF00677F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7EAFF),
    onPrimaryContainer = Color(0xFF001F28),
    inversePrimary = Color(0xFF4CD6FF),
    secondary = Color(0xFF575C97),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E0FF),
    onSecondaryContainer = Color(0xFF13175F),
    tertiary = Color(0xFF5C5B5D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E2E4),
    onTertiaryContainer = Color(0xFF19181B),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1A1B1C),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1A1B1C),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF3F484B),
    surfaceTint = Color(0xFF00677F),
    inverseSurface = Color(0xFF2E3032),
    inverseOnSurface = Color(0xFFF1F0F3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFBBC9CF),
    surfaceBright = Color(0xFFFAF9FD),
    surfaceDim = Color(0xFFD9D9DB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F5),
    surfaceContainer = Color(0xFFEDEDF0),
    surfaceContainerHigh = Color(0xFFE8E7EA),
    surfaceContainerHighest = Color(0xFFE2E2E5),
)

// ---------------------------------------------------------------------------
// Semantic status colours
// ---------------------------------------------------------------------------

/**
 * Severity/state colours the Material scheme has no role for.
 *
 * Status is **never** conveyed by colour alone anywhere in the app: every coloured chip
 * also carries a word ("ON", "REVIEW", "VERIFIED", …) and, where it matters, an icon.
 */
@Immutable
data class ThrakshaStatusColors(
    /** Working as intended / verified / active. */
    val ok: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    /** Needs a human decision, or a capability is only partially available. */
    val warn: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    /** A confirmed problem: known-threat match, failure, integrity error. */
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    /** Idle / not applicable / unknown. */
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    /** Card fill above the canvas ("Level 1" in Guardian Prime). */
    val card: Color,
    /** Hairline used for card and divider edges. */
    val cardBorder: Color,
)

internal val DarkStatusColors = ThrakshaStatusColors(
    ok = Color(0xFF4CD6FF),
    okContainer = Color(0xFF00394A),
    onOkContainer = Color(0xFFB7EAFF),
    warn = Color(0xFFFFB95C),
    warnContainer = Color(0xFF4A2E00),
    onWarnContainer = Color(0xFFFFDDB3),
    danger = Color(0xFFFFB4AB),
    dangerContainer = Color(0xFF93000A),
    onDangerContainer = Color(0xFFFFDAD6),
    neutral = Color(0xFFBBC9CF),
    neutralContainer = Color(0xFF292A2C),
    onNeutralContainer = Color(0xFFBBC9CF),
    card = Color(0xFF161719),
    cardBorder = Color(0xFF2D2E30),
)

internal val LightStatusColors = ThrakshaStatusColors(
    ok = Color(0xFF00677F),
    okContainer = Color(0xFFB7EAFF),
    onOkContainer = Color(0xFF001F28),
    warn = Color(0xFF8A5000),
    warnContainer = Color(0xFFFFDDB3),
    onWarnContainer = Color(0xFF2C1700),
    danger = Color(0xFFBA1A1A),
    dangerContainer = Color(0xFFFFDAD6),
    onDangerContainer = Color(0xFF410002),
    neutral = Color(0xFF3F484B),
    neutralContainer = Color(0xFFE8E7EA),
    onNeutralContainer = Color(0xFF3F484B),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDBE4E8),
)

val LocalThrakshaStatusColors = staticCompositionLocalOf { DarkStatusColors }

// ---------------------------------------------------------------------------
// Spacing — Guardian Prime 8 dp grid
// ---------------------------------------------------------------------------

@Immutable
object ThrakshaSpacing {
    /** Grid base. */
    val base: Dp = 8.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp

    /** Lateral screen margin on phones. */
    val screenMargin: Dp = 16.dp
    /** Padding inside a card or list container. */
    val containerPadding: Dp = 20.dp
    /** Vertical rhythm between logical sections ("calm" pillar). */
    val sectionGap: Dp = 24.dp
    /** Gap between sibling cards in a group. */
    val cardGap: Dp = 12.dp
    /** Extra bottom padding so content clears the bottom navigation bar. */
    val bottomNavClearance: Dp = 96.dp
    /** Minimum interactive target (accessibility). */
    val touchTarget: Dp = 48.dp
}

// ---------------------------------------------------------------------------
// Shape — "Premium-Soft" Material 3
// ---------------------------------------------------------------------------

@Immutable
object ThrakshaRadius {
    val sm: Dp = 8.dp
    /** Buttons and input fields. */
    val control: Dp = 12.dp
    /** Chips, status pills, nav indicator. */
    val pill: Dp = 999.dp
    /** Cards and main content containers. */
    val card: Dp = 24.dp
    /** Inner/nested panels within a card. */
    val panel: Dp = 16.dp
}

internal val ThrakshaShapes = Shapes(
    extraSmall = RoundedCornerShape(ThrakshaRadius.sm),
    small = RoundedCornerShape(ThrakshaRadius.control),
    medium = RoundedCornerShape(ThrakshaRadius.panel),
    large = RoundedCornerShape(ThrakshaRadius.card),
    extraLarge = RoundedCornerShape(ThrakshaRadius.card),
)
