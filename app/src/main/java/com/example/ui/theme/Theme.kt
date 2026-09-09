package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val CyberDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = ElectricSky,
    secondary = CyberViolet,
    onSecondary = ObsidianVoid,
    secondaryContainer = Color(0xFF1E1438),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = NeonEmerald,
    onTertiary = ObsidianVoid,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderCyan,
    error = NeonRose,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    // Set dynamicColor to false by default so the premium cyber-obsidian identity is preserved on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        else -> CyberDarkColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

