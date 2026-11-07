package com.venujatech.netray.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorSchemeBase = darkColorScheme(
    primary = Color(0xFF2788FF),
    onPrimary = Color(0xFF001F3F),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFF78D7BE),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005144),
    onTertiaryContainer = Color(0xFF95F4DA),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE0E2E8),
    surface = Color(0xFF0B0E13),
    onSurface = Color(0xFFE0E2E8),
    surfaceVariant = Color(0xFF1D2228),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
)

private val LightColorSchemeBase = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF006A5A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF95F4DA),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFE1E5ED),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
)

fun darkColorSchemeWithAccent(accentColor: Color): ColorScheme =
    DarkColorSchemeBase.copy(
        primary = accentColor,
        onPrimary = Color(0xFF001F3F),
        primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFD1E4FF),
    )

fun lightColorSchemeWithAccent(accentColor: Color): ColorScheme =
    LightColorSchemeBase.copy(
        primary = accentColor,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36),
    )

@Composable
fun NetrayTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    colorScheme: ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val resolvedColorScheme = colorScheme ?: when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorSchemeBase
        else -> LightColorSchemeBase
    }

    MaterialTheme(
        colorScheme = resolvedColorScheme,
        content = content
    )
}
