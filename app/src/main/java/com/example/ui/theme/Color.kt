package com.example.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

// PS Cam Signature Brand Colors: Orange & Black
val PsOrangePrimary = Color(0xFFFF6600)
val PsOrangeLight = Color(0xFFFF8533)
val PsOrangeDark = Color(0xFFE65100)
val PsOrangeBg = Color(0x26FF6600)

val PsCyan = Color(0xFF06B6D4)
val PsBlue = Color(0xFF3B82F6)

val PsSuccess = Color(0xFF22C55E)
val PsSuccessBg = Color(0x2222C55E)

val PsDanger = Color(0xFFEF4444)
val PsDangerBg = Color(0x22EF4444)

val PsWarning = Color(0xFFF59E0B)

// Dark Theme Colors (Deep Black & Pitch Dark)
val PsDarkBackground = Color(0xFF09090B)
val PsDarkSurface = Color(0xFF141417)
val PsDarkSurfaceLight = Color(0xFF1F1F24)
val PsDarkSurfaceBorder = Color(0xFF2E2E36)
val PsDarkTextPrimary = Color(0xFFF8FAFC)
val PsDarkTextSecondary = Color(0xFFA1A1AA)
val PsDarkTextMuted = Color(0xFF71717A)

// Light Theme Colors (Alfred Camera Style)
val PsLightBackground = Color(0xFFF3F4F6)
val PsLightSurface = Color(0xFFFFFFFF)
val PsLightSurfaceLight = Color(0xFFF8FAFC)
val PsLightSurfaceBorder = Color(0xFFE5E7EB)
val PsLightTextPrimary = Color(0xFF111827)
val PsLightTextSecondary = Color(0xFF4B5563)
val PsLightTextMuted = Color(0xFF9CA3AF)

// Legacy alias compatibility
val PsBackground = PsDarkBackground
val PsSurface = PsDarkSurface
val PsSurfaceLight = PsDarkSurfaceLight
val PsSurfaceBorder = PsDarkSurfaceBorder
val PsTextPrimary = PsDarkTextPrimary
val PsTextSecondary = PsDarkTextSecondary
val PsTextMuted = PsDarkTextMuted

// Backward compatibility
val PsSkyPrimary = PsOrangePrimary
val PsSkyLight = PsOrangeLight
val PsSkyDark = PsOrangeDark

@Stable
class PsCamPalette(
    val background: Color,
    val surface: Color,
    val surfaceLight: Color,
    val surfaceBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color = PsOrangePrimary,
    val secondary: Color = PsOrangeLight,
    val isDark: Boolean = true
)

