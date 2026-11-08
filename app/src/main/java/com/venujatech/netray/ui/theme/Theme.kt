package com.venujatech.netray.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

data class NetrayPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val card: Color,
    val menuSurface: Color,
    val outline: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    val latency: Color,
    val latencySurface: Color,
    val serverIcon: Color,
    val activeTag: Color,
    val inactiveTag: Color,
    val badgeXray: Color,
    val badgeNative: Color,
    val badgeNew: Color,
    val onBadge: Color,
    val controlScrim: Color,
    val qrForeground: Color,
    val qrBackground: Color
)

val LocalNetrayPalette = staticCompositionLocalOf {
    NetrayPalette(
        background = Color(0xFF111111), surface = Color(0xFF1A1A1A), surfaceVariant = Color(0xFF242424),
        card = Color(0xFF1B1B1B), menuSurface = Color(0xFF2C2C2C), outline = Color(0xFF858585),
        divider = Color(0xFF343434), textPrimary = Color(0xFFF5F5F5), textSecondary = Color(0xFFBDBDBD),
        primary = Color(0xFF2788FF), onPrimary = Color.Black, error = Color(0xFFF44336),
        warning = Color(0xFFFFC857), success = Color(0xFF36D399), latency = Color(0xFF60CFFF),
        latencySurface = Color(0xFF111821), serverIcon = Color(0xFF18C9FF), activeTag = Color(0xFF27364A),
        inactiveTag = Color(0xFF222833), badgeXray = Color(0xFF667719), badgeNative = Color(0xFF667719),
        badgeNew = Color(0xFF9C5E0D), onBadge = Color.White,
        controlScrim = Color(0x99000000), qrForeground = Color.Black, qrBackground = Color.White
    )
}

fun colorForAccent(accent: String): Color = when (accent) {
    "green" -> Color(0xFF36D399)
    "purple" -> Color(0xFFB366FF)
    "orange" -> Color(0xFFFF8C42)
    else -> Color(0xFF2788FF)
}

fun onColorFor(background: Color): Color {
    val whiteContrast = contrastRatio(Color.White, background)
    val blackContrast = contrastRatio(Color.Black, background)
    return if (whiteContrast >= blackContrast) Color.White else Color.Black
}

fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

fun paletteForAccent(accentName: String, dark: Boolean): NetrayPalette {
    val accent = colorForAccent(accentName)
    val onAccent = onColorFor(accent)
    return if (dark) {
        NetrayPalette(
            background = Color(0xFF111111), surface = Color(0xFF1A1A1A), surfaceVariant = Color(0xFF242424),
            card = Color(0xFF1B1B1B), menuSurface = Color(0xFF2C2C2C), outline = Color(0xFF858585),
            divider = Color(0xFF343434), textPrimary = Color(0xFFF5F5F5), textSecondary = Color(0xFFBDBDBD),
            primary = accent, onPrimary = onAccent, error = Color(0xFFF44336), warning = Color(0xFFFFC857),
            success = Color(0xFF36D399), latency = Color(0xFF60CFFF), latencySurface = Color(0xFF111821),
            serverIcon = Color(0xFF18C9FF), activeTag = Color(0xFF27364A), inactiveTag = Color(0xFF222833),
            badgeXray = Color(0xFF667719), badgeNative = Color(0xFF667719), badgeNew = Color(0xFF9C5E0D), onBadge = Color.White,
            controlScrim = Color(0x99000000), qrForeground = Color.Black, qrBackground = Color.White
        )
    } else {
        NetrayPalette(
            background = Color(0xFFF7F8FA), surface = Color.White, surfaceVariant = Color(0xFFE8EAEE),
            card = Color.White, menuSurface = Color.White, outline = Color(0xFF667085), divider = Color(0xFFD0D5DD),
            textPrimary = Color(0xFF101828), textSecondary = Color(0xFF475467), primary = accent, onPrimary = onAccent,
            error = Color(0xFFB42318), warning = Color(0xFF9A6700), success = Color(0xFF067647),
            latency = Color(0xFF0677A8), latencySurface = Color(0xFFE6F4F8), serverIcon = Color(0xFF087E9B),
            activeTag = Color(0xFFDCEEFF), inactiveTag = Color(0xFFE8EAEE), badgeXray = Color(0xFF667719),
            badgeNative = Color(0xFF667719), badgeNew = Color(0xFF9C5E0D), onBadge = Color.White, controlScrim = Color(0x99000000),
            qrForeground = Color.Black, qrBackground = Color.White
        )
    }
}

fun darkColorSchemeWithAccent(accentColor: Color): ColorScheme = colorSchemeFromPalette(
    paletteForAccent(when (accentColor) {
        colorForAccent("green") -> "green"
        colorForAccent("purple") -> "purple"
        colorForAccent("orange") -> "orange"
        else -> "blue"
    }, dark = true),
    dark = true
)

fun lightColorSchemeWithAccent(accentColor: Color): ColorScheme = colorSchemeFromPalette(
    paletteForAccent(when (accentColor) {
        colorForAccent("green") -> "green"
        colorForAccent("purple") -> "purple"
        colorForAccent("orange") -> "orange"
        else -> "blue"
    }, dark = false),
    dark = false
)

private fun colorSchemeFromPalette(palette: NetrayPalette, dark: Boolean): ColorScheme {
    val secondary = palette.textSecondary
    return if (dark) darkColorScheme(
        primary = palette.primary, onPrimary = palette.onPrimary,
        primaryContainer = palette.activeTag, onPrimaryContainer = palette.textPrimary,
        secondary = secondary, onSecondary = palette.background,
        secondaryContainer = palette.surfaceVariant, onSecondaryContainer = palette.textPrimary,
        tertiary = palette.success, onTertiary = onColorFor(palette.success),
        tertiaryContainer = palette.surfaceVariant, onTertiaryContainer = palette.textPrimary,
        background = palette.background, onBackground = palette.textPrimary,
        surface = palette.surface, onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant, onSurfaceVariant = palette.textSecondary,
        outline = palette.outline, outlineVariant = palette.divider, error = palette.error
    ) else lightColorScheme(
        primary = palette.primary, onPrimary = palette.onPrimary,
        primaryContainer = palette.activeTag, onPrimaryContainer = palette.textPrimary,
        secondary = secondary, onSecondary = palette.background,
        secondaryContainer = palette.surfaceVariant, onSecondaryContainer = palette.textPrimary,
        tertiary = palette.success, onTertiary = onColorFor(palette.success),
        tertiaryContainer = palette.surfaceVariant, onTertiaryContainer = palette.textPrimary,
        background = palette.background, onBackground = palette.textPrimary,
        surface = palette.surface, onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant, onSurfaceVariant = palette.textSecondary,
        outline = palette.outline, outlineVariant = palette.divider, error = palette.error
    )
}

@Composable
fun NetrayTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    colorScheme: ColorScheme? = null,
    palette: NetrayPalette? = null,
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
        darkTheme -> colorSchemeFromPalette(paletteForAccent("blue", true), true)
        else -> colorSchemeFromPalette(paletteForAccent("blue", false), false)
    }

    val resolvedPalette = palette ?: paletteForAccent("blue", darkTheme)
    val appScheme = resolvedColorScheme.copy(
        background = resolvedPalette.background,
        surface = resolvedPalette.surface,
        surfaceVariant = resolvedPalette.surfaceVariant,
        onSurfaceVariant = resolvedPalette.textSecondary,
        onSurface = resolvedPalette.textPrimary,
        onBackground = resolvedPalette.textPrimary,
        primary = resolvedPalette.primary,
        onPrimary = resolvedPalette.onPrimary,
        error = resolvedPalette.error,
        outline = resolvedPalette.outline,
        outlineVariant = resolvedPalette.divider
    )
    CompositionLocalProvider(LocalNetrayPalette provides resolvedPalette) {
        MaterialTheme(colorScheme = appScheme, content = content)
    }
}
