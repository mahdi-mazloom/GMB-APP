package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundGradientTop: Color,
    val backgroundGradientMid: Color,
    val backgroundGradientBottom: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val surfaceGlass: Color,
    val headerSurface: Color,
    val cardBorder: Color,
    val cardBorderFocused: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val brandCyan: Color,
    val electricSky: Color,
    val brandPurple: Color,
    val neonGreen: Color,
    val neonOrange: Color,
    val neonRed: Color,
    val neonYellow: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val dividerColor: Color
)

val DarkAppColors = AppColors(
    isDark = true,
    background = Color(0xFF060912),
    backgroundGradientTop = Color(0xFF0A1224),
    backgroundGradientMid = Color(0xFF060912),
    backgroundGradientBottom = Color(0xFF04060C),
    surface = Color(0xFF0E1729),
    surfaceElevated = Color(0xFF142036),
    surfaceCard = Color(0xFF0E1626),
    surfaceGlass = Color(0xCC0E192E),
    headerSurface = Color(0xCC0E1729),
    cardBorder = Color(0x3338BDF8),
    cardBorderFocused = Color(0xFF00E5FF),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    textMuted = Color(0xFF475569),
    brandCyan = Color(0xFF00E5FF),
    electricSky = Color(0xFF38BDF8),
    brandPurple = Color(0xFFA855F7),
    neonGreen = Color(0xFF10B981),
    neonOrange = Color(0xFFF97316),
    neonRed = Color(0xFFEF4444),
    neonYellow = Color(0xFFFACC15),
    inputBackground = Color(0xFF0A1224),
    inputBorder = Color(0xFF1E293B),
    dividerColor = Color(0x1F38BDF8)
)

val LightAppColors = AppColors(
    isDark = false,
    background = Color(0xFFF0F4F9),
    backgroundGradientTop = Color(0xFFE2EAF4),
    backgroundGradientMid = Color(0xFFF0F4F9),
    backgroundGradientBottom = Color(0xFFE8EFF8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF8FAFC),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceGlass = Color(0xF0FFFFFF),
    headerSurface = Color(0xEBFFFFFF),
    cardBorder = Color(0x330284C7),
    cardBorderFocused = Color(0xFF0284C7),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF64748B),
    textMuted = Color(0xFF94A3B8),
    brandCyan = Color(0xFF0284C7), // High-contrast deep cyan for light mode readability
    electricSky = Color(0xFF0369A1),
    brandPurple = Color(0xFF7C3AED),
    neonGreen = Color(0xFF059669),
    neonOrange = Color(0xFFEA580C),
    neonRed = Color(0xFFDC2626),
    neonYellow = Color(0xFFD97706),
    inputBackground = Color(0xFFF1F5F9),
    inputBorder = Color(0xFFCBD5E1),
    dividerColor = Color(0x260284C7)
)

val LocalAppColors = compositionLocalOf { DarkAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
