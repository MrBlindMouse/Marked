package com.bmdstudios.marked.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Marked E-Ink palette tokens (Phase 1) — warm paper, graphite ink, hairline rules,
// mirroring the grey levels of e-ink reading hardware. No accent hues on purpose:
// ink is ink, paper is paper.

val MarkedPaperLight = Color(0xFFF4F1EA)
val MarkedPaperDark = Color(0xFF14120D)

val MarkedInkLight = Color(0xFF1B1A16)
val MarkedInkDark = Color(0xFFE9E5D8)

val MarkedInkSoftLight = Color(0xFF57534A)
val MarkedInkSoftDark = Color(0xFFB4AF9F)

val MarkedInkMuteLight = Color(0xFF8A8578)
val MarkedInkMuteDark = Color(0xFF857F70)

val MarkedHairlineLight = Color(0xFFD9D4C7)
val MarkedHairlineDark = Color(0xFF38342C)

val MarkedEInkLightColorScheme = lightColorScheme(
    primary = MarkedInkLight,
    onPrimary = MarkedPaperLight,
    primaryContainer = MarkedInkSoftLight,
    onPrimaryContainer = MarkedPaperLight,
    secondary = MarkedInkSoftLight,
    onSecondary = MarkedPaperLight,
    secondaryContainer = Color(0xFFE9E5D6),
    onSecondaryContainer = MarkedInkLight,
    tertiary = MarkedInkSoftLight,
    onTertiary = MarkedPaperLight,
    tertiaryContainer = Color(0xFFE9E5D6),
    onTertiaryContainer = MarkedInkLight,
    background = MarkedPaperLight,
    onBackground = MarkedInkLight,
    surface = MarkedPaperLight,
    onSurface = MarkedInkLight,
    surfaceContainer = Color(0xFFEDEAE0),
    onSurfaceVariant = MarkedInkSoftLight,
    outline = MarkedHairlineLight,
    outlineVariant = MarkedHairlineLight,
)

val MarkedEInkDarkColorScheme = darkColorScheme(
    primary = MarkedInkDark,
    onPrimary = MarkedPaperDark,
    primaryContainer = MarkedInkSoftDark,
    onPrimaryContainer = MarkedPaperDark,
    secondary = MarkedInkSoftDark,
    onSecondary = MarkedPaperDark,
    secondaryContainer = Color(0xFF2E2B24),
    onSecondaryContainer = MarkedInkDark,
    tertiary = MarkedInkSoftDark,
    onTertiary = MarkedPaperDark,
    tertiaryContainer = Color(0xFF2E2B24),
    onTertiaryContainer = MarkedInkDark,
    background = MarkedPaperDark,
    onBackground = MarkedInkDark,
    surface = MarkedPaperDark,
    onSurface = MarkedInkDark,
    surfaceContainer = Color(0xFF1B1913),
    onSurfaceVariant = MarkedInkSoftDark,
    outline = MarkedHairlineDark,
    outlineVariant = MarkedHairlineDark,
)
