package com.ezequielbrrt.domemory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** User-selectable theme, persisted as `themePreference` (spec 13.2). */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * The two named type roles from the spec (14.2). iOS bundles Righteous and PatrickHand
 * but its implementations return rounded system faces, so the roles are what carry
 * meaning, not the files. Kept as two roles here so swapping in the TTFs is one edit.
 */
object DoMemoryType {
    fun display(size: Int) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = size.sp,
    )

    fun handwritten(size: Int) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
    )
}

@Composable
fun DoMemoryTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette

    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            background = palette.appBackground,
            surface = palette.surfacePrimary,
            onPrimary = palette.surfacePrimary,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            background = palette.appBackground,
            surface = palette.surfacePrimary,
            onPrimary = palette.surfacePrimary,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
        )
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
