package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val DarkPalette = PsCamPalette(
    background = PsDarkBackground,
    surface = PsDarkSurface,
    surfaceLight = PsDarkSurfaceLight,
    surfaceBorder = PsDarkSurfaceBorder,
    textPrimary = PsDarkTextPrimary,
    textSecondary = PsDarkTextSecondary,
    textMuted = PsDarkTextMuted,
    primary = PsOrangePrimary,
    secondary = PsOrangeLight,
    isDark = true
)

val LightPalette = PsCamPalette(
    background = PsLightBackground,
    surface = PsLightSurface,
    surfaceLight = PsLightSurfaceLight,
    surfaceBorder = PsLightSurfaceBorder,
    textPrimary = PsLightTextPrimary,
    textSecondary = PsLightTextSecondary,
    textMuted = PsLightTextMuted,
    primary = PsOrangePrimary,
    secondary = PsOrangeDark,
    isDark = false
)

val LocalPsPalette = staticCompositionLocalOf { DarkPalette }

object PsCamTheme {
    val colors: PsCamPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPsPalette.current
}

private val DarkMaterialColorScheme = darkColorScheme(
    primary = PsOrangePrimary,
    onPrimary = Color.Black,
    primaryContainer = PsOrangeDark,
    onPrimaryContainer = PsDarkTextPrimary,
    secondary = PsOrangeLight,
    onSecondary = Color.Black,
    secondaryContainer = PsDarkSurfaceLight,
    onSecondaryContainer = PsDarkTextPrimary,
    tertiary = PsOrangeLight,
    background = PsDarkBackground,
    onBackground = PsDarkTextPrimary,
    surface = PsDarkSurface,
    onSurface = PsDarkTextPrimary,
    surfaceVariant = PsDarkSurfaceLight,
    onSurfaceVariant = PsDarkTextSecondary,
    outline = PsDarkSurfaceBorder,
    error = PsDanger,
    onError = PsDarkTextPrimary,
    errorContainer = PsDangerBg,
    onErrorContainer = PsDanger
)

private val LightMaterialColorScheme = lightColorScheme(
    primary = PsOrangePrimary,
    onPrimary = Color.White,
    primaryContainer = PsOrangeLight,
    onPrimaryContainer = PsLightTextPrimary,
    secondary = PsOrangeDark,
    onSecondary = Color.White,
    secondaryContainer = PsLightSurfaceLight,
    onSecondaryContainer = PsLightTextPrimary,
    tertiary = PsOrangeDark,
    background = PsLightBackground,
    onBackground = PsLightTextPrimary,
    surface = PsLightSurface,
    onSurface = PsLightTextPrimary,
    surfaceVariant = PsLightSurfaceLight,
    onSurfaceVariant = PsLightTextSecondary,
    outline = PsLightSurfaceBorder,
    error = PsDanger,
    onError = PsLightSurface,
    errorContainer = PsDangerBg,
    onErrorContainer = PsDanger
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val materialScheme = if (darkTheme) DarkMaterialColorScheme else LightMaterialColorScheme

    CompositionLocalProvider(LocalPsPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content
        )
    }
}
