package com.mhrsdev.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Base schemes (DESIGN.md §2.1). Seed: lapis (Persian tile blue), tertiary turquoise.
 * Values in the spec table are used verbatim; roles the table leaves out come from the same lapis
 * tonal palette (M3 tone mapping), so every ColorScheme slot is defined and nothing falls back to
 * the baseline purple.
 */
object BaseColors {
    /** Lapis seed (tone 40). Also the fallback artwork seed when no artwork/placeholder exists. */
    val Lapis = Color(0xFF2F5F95)

    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFA6C8FF),
        onPrimary = Color(0xFF00315F),
        primaryContainer = Color(0xFF1E4878),
        onPrimaryContainer = Color(0xFFD4E3FF),
        inversePrimary = Color(0xFF2F5F95),
        secondary = Color(0xFFBAC8DB),
        onSecondary = Color(0xFF243141),
        secondaryContainer = Color(0xFF364659),
        onSecondaryContainer = Color(0xFFD6E3F7),
        tertiary = Color(0xFF7FD6CB),
        onTertiary = Color(0xFF003733),
        tertiaryContainer = Color(0xFF00504A),
        onTertiaryContainer = Color(0xFF9EF2E7),
        background = Color(0xFF0F1216),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF0F1216),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF30353C),
        onSurfaceVariant = Color(0xFFBFC6D2),
        surfaceTint = Color(0xFFA6C8FF),
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2E3036),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8A919C),
        outlineVariant = Color(0xFF3F4650),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF353A40),
        surfaceContainer = Color(0xFF1B1F24),
        surfaceContainerHigh = Color(0xFF252A30),
        surfaceContainerHighest = Color(0xFF30353C),
        surfaceContainerLow = Color(0xFF171A1F),
        surfaceContainerLowest = Color(0xFF0A0D10),
        surfaceDim = Color(0xFF0F1216),
        primaryFixed = Color(0xFFD4E3FF),
        primaryFixedDim = Color(0xFFA6C8FF),
        onPrimaryFixed = Color(0xFF001C3A),
        onPrimaryFixedVariant = Color(0xFF1E4878),
        secondaryFixed = Color(0xFFD6E3F7),
        secondaryFixedDim = Color(0xFFBAC8DB),
        onSecondaryFixed = Color(0xFF0F1D2C),
        onSecondaryFixedVariant = Color(0xFF3A4859),
        tertiaryFixed = Color(0xFF9EF2E7),
        tertiaryFixedDim = Color(0xFF7FD6CB),
        onTertiaryFixed = Color(0xFF00201D),
        onTertiaryFixedVariant = Color(0xFF00504A),
    )

    val Light: ColorScheme = lightColorScheme(
        primary = Color(0xFF2F5F95),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD4E3FF),
        onPrimaryContainer = Color(0xFF001C3A),
        inversePrimary = Color(0xFFA6C8FF),
        secondary = Color(0xFF525F70),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6E3F7),
        onSecondaryContainer = Color(0xFF0F1D2C),
        tertiary = Color(0xFF006A62),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF9EF2E7),
        onTertiaryContainer = Color(0xFF00201D),
        background = Color(0xFFF7F9FF),
        onBackground = Color(0xFF181C21),
        surface = Color(0xFFF7F9FF),
        onSurface = Color(0xFF181C21),
        surfaceVariant = Color(0xFFDEE3EA),
        onSurfaceVariant = Color(0xFF414851),
        surfaceTint = Color(0xFF2F5F95),
        inverseSurface = Color(0xFF2D3137),
        inverseOnSurface = Color(0xFFEFF0F7),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF717883),
        outlineVariant = Color(0xFFC1C7D2),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF7F9FF),
        surfaceContainer = Color(0xFFEAEEF5),
        surfaceContainerHigh = Color(0xFFE4E8EF),
        surfaceContainerHighest = Color(0xFFDEE3EA),
        surfaceContainerLow = Color(0xFFF1F3FA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD7DAE1),
        primaryFixed = Color(0xFFD4E3FF),
        primaryFixedDim = Color(0xFFA6C8FF),
        onPrimaryFixed = Color(0xFF001C3A),
        onPrimaryFixedVariant = Color(0xFF1E4878),
        secondaryFixed = Color(0xFFD6E3F7),
        secondaryFixedDim = Color(0xFFBAC8DB),
        onSecondaryFixed = Color(0xFF0F1D2C),
        onSecondaryFixedVariant = Color(0xFF3A4859),
        tertiaryFixed = Color(0xFF9EF2E7),
        tertiaryFixedDim = Color(0xFF7FD6CB),
        onTertiaryFixed = Color(0xFF00201D),
        onTertiaryFixedVariant = Color(0xFF00504A),
    )

    /** AMOLED: the dark scheme with black background and the spec's black surface containers. */
    val Black: ColorScheme = Dark.toBlack()
}

/** Applies the "Black" theme mode to any dark scheme (base, wallpaper or artwork-derived). §2.1: only surfaces change. */
fun ColorScheme.toBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0E11),
    surfaceContainer = Color(0xFF111418),
    surfaceContainerHigh = Color(0xFF1A1E23),
    surfaceContainerHighest = Color(0xFF24292F),
    outlineVariant = Color(0xFF2E343C),
)
