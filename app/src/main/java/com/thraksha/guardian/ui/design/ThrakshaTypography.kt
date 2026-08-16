package com.thraksha.guardian.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Guardian Prime type scale, mapped onto Material 3 roles.
 *
 * Stitch specifies Inter for UI text and JetBrains Mono for technical labels. Neither is
 * bundled: shipping two webfonts to satisfy a mockup is not worth ~400 KB in the APK, and
 * the platform sans-serif (Roboto) carries the same neutral, systematic character at these
 * sizes. Monospace labels use the platform monospace family, which preserves the intended
 * "technical accent" contrast. Sizes, weights, line heights and letter spacing are Stitch's.
 */

/** Inter → platform sans-serif. */
private val Ui = FontFamily.SansSerif

/** JetBrains Mono → platform monospace, used only for label-caps. */
internal val TechnicalFontFamily = FontFamily.Monospace

internal val ThrakshaTypography = Typography(
    // headline-lg — 32/40, −0.02em, 700
    displaySmall = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.64).sp,
    ),
    // headline-lg-mobile — 28/36, −0.02em, 700. The hero headline on phones.
    headlineLarge = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.56).sp,
    ),
    // headline-md — 24/32, 600
    headlineMedium = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    // Card and section titles — body-lg at semibold.
    titleMedium = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // body-lg — 16/24, 400
    bodyLarge = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    // body-md — 14/20, 400
    bodyMedium = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // label-md — 12/16, 500 (bottom-nav labels)
    labelMedium = TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // label-caps — JetBrains Mono 12/16, +0.1em, 500. Status chips and technical labels.
    labelSmall = TextStyle(
        fontFamily = TechnicalFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    ),
)
