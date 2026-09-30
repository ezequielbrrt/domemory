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
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R

/** User-selectable theme, persisted as `themePreference` (spec 13.2). */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * The app's typography: Nunito everywhere, standing in for the SF Pro Rounded iOS renders.
 *
 * iOS bundles Righteous and Patrick Hand, but its `Font.righteous`/`Font.patrickHand` return
 * `.system(design: .rounded)`, and nearly every other label is `.system(design: .rounded)` too,
 * so the iOS app is set in SF Pro Rounded throughout. Apple licenses that face for Apple
 * platforms only, so Android uses Nunito, the closest open rounded sans (SIL Open Font License
 * 1.1; the license text ships in `assets/licenses/Nunito-OFL.txt`). `res/font/nunito.ttf` is
 * Google Fonts' variable font, and each weight below is one instance of its `wght` axis, so
 * bold text is a real bold, never a synthesized one. Glyphs Nunito lacks (Devanagari, CJK)
 * fall back to the system font per character.
 */
object DoMemoryType {
    val Nunito: FontFamily = FontFamily(
        listOf(300, 400, 500, 600, 700, 800, 900).map { weight ->
            Font(
                resId = R.font.nunito,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

    /** The display/brand role (titles), iOS's `righteous(size:)`: heavy rounded. */
    fun display(size: Int) = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
    )

    /** The secondary role, iOS's `patrickHand(size:)`: semibold rounded. */
    fun handwritten(size: Int) = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
    )

    /** Material's type scale in Nunito, so every `Text`, button and tab label inherits it. */
    val typography: Typography = Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = Nunito),
            displayMedium = displayMedium.copy(fontFamily = Nunito),
            displaySmall = displaySmall.copy(fontFamily = Nunito),
            headlineLarge = headlineLarge.copy(fontFamily = Nunito),
            headlineMedium = headlineMedium.copy(fontFamily = Nunito),
            headlineSmall = headlineSmall.copy(fontFamily = Nunito),
            titleLarge = titleLarge.copy(fontFamily = Nunito),
            titleMedium = titleMedium.copy(fontFamily = Nunito),
            titleSmall = titleSmall.copy(fontFamily = Nunito),
            bodyLarge = bodyLarge.copy(fontFamily = Nunito),
            bodyMedium = bodyMedium.copy(fontFamily = Nunito),
            bodySmall = bodySmall.copy(fontFamily = Nunito),
            labelLarge = labelLarge.copy(fontFamily = Nunito),
            labelMedium = labelMedium.copy(fontFamily = Nunito),
            labelSmall = labelSmall.copy(fontFamily = Nunito),
        )
    }
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
        MaterialTheme(colorScheme = scheme, typography = DoMemoryType.typography, content = content)
    }
}
