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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R

/** User-selectable theme, persisted as `themePreference` (spec 13.2). */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * The two named type roles from the spec (14.2), backed by the same two fonts iOS bundles:
 * Righteous for display text and Patrick Hand for handwritten text. The TTFs in `res/font`
 * are byte-for-byte copies of `ios/DoMemory/DoMemory/SupportingFiles/Fonts`, both under the
 * SIL Open Font License 1.1 (the copyright and license text travel inside each file).
 *
 * Note that iOS's `Font.righteous`/`Font.patrickHand` currently return `.system(design:
 * .rounded)` rather than these files, so iOS renders SF Rounded; Android renders the fonts
 * themselves. Both fonts ship a single Regular weight, so the styles ask for
 * [FontWeight.Normal] — anything heavier makes Compose synthesize a fake bold. Glyphs the
 * fonts lack (Devanagari, CJK) fall back to the system font per character.
 */
object DoMemoryType {
    private val Righteous = FontFamily(Font(R.font.righteous_regular, FontWeight.Normal))
    private val PatrickHand = FontFamily(Font(R.font.patrick_hand_regular, FontWeight.Normal))

    fun display(size: Int) = TextStyle(
        fontFamily = Righteous,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
    )

    fun handwritten(size: Int) = TextStyle(
        fontFamily = PatrickHand,
        fontWeight = FontWeight.Normal,
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
