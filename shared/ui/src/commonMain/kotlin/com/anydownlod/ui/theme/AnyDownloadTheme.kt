package com.anydownlod.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Desktop visual system.
 *
 * The palette is a flat system blue on a cool gray canvas: white grouped
 * surfaces, hairline borders, and no Material tint. Type is a tight sans
 * hierarchy so titles, sections, and metadata stay distinct.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF005FCC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E7FF),
    onPrimaryContainer = Color(0xFF002A5C),
    secondary = Color(0xFF3E5C86),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E7FF),
    onSecondaryContainer = Color(0xFF0A2F5C),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFC6C6CB),
    outlineVariant = Color(0xFFE3E3E8),
    error = Color(0xFFC41C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF5C1010),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEBEDF2),
    surfaceContainer = Color(0xFFE6E8EE),
    surfaceContainerHigh = Color(0xFFDDE0E7),
    surfaceContainerHighest = Color(0xFFD5D8E0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6AADFF),
    onPrimary = Color(0xFF04243F),
    primaryContainer = Color(0xFF0E3A66),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFFB6D0FF),
    onSecondary = Color(0xFF0A2F5C),
    secondaryContainer = Color(0xFF1A3A5C),
    onSecondaryContainer = Color(0xFFD6E8FF),
    background = Color(0xFF141416),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF232326),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF3A3A40),
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF5A5A62),
    outlineVariant = Color(0xFF3A3A40),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0002),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFD7D4),
    surfaceContainerLowest = Color(0xFF101012),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF2C2C31),
    surfaceContainerHigh = Color(0xFF36363C),
    surfaceContainerHighest = Color(0xFF414148),
)

private val AppFont = FontFamily.SansSerif

private fun appText(
    size: TextUnit,
    weight: FontWeight,
    line: TextUnit,
    spacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = AppFont,
    fontSize = size,
    fontWeight = weight,
    lineHeight = line,
    letterSpacing = spacing,
)

private val AnyDownloadTypography = Typography(
    headlineLarge = appText(28.sp, FontWeight.SemiBold, 34.sp, (-0.4).sp),
    headlineMedium = appText(22.sp, FontWeight.SemiBold, 28.sp, (-0.3).sp),
    titleLarge = appText(20.sp, FontWeight.SemiBold, 26.sp, (-0.2).sp),
    titleMedium = appText(16.sp, FontWeight.SemiBold, 22.sp, (-0.1).sp),
    titleSmall = appText(14.sp, FontWeight.SemiBold, 20.sp),
    bodyLarge = appText(16.sp, FontWeight.Normal, 22.sp),
    bodyMedium = appText(14.sp, FontWeight.Normal, 20.sp),
    bodySmall = appText(12.sp, FontWeight.Normal, 16.sp),
    labelLarge = appText(13.sp, FontWeight.Medium, 18.sp),
    labelMedium = appText(12.sp, FontWeight.Medium, 16.sp),
    labelSmall = appText(11.sp, FontWeight.SemiBold, 14.sp, 0.4.sp),
)

private val AnyDownloadShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun AnyDownloadTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AnyDownloadTypography,
        shapes = AnyDownloadShapes,
        content = content,
    )
}
