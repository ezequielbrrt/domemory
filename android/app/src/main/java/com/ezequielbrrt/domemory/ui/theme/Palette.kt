package com.ezequielbrrt.domemory.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette from the spec, section 14.1. Every token is a light/dark pair and is
 * resolved from the active appearance — there is no single-value colour in the app.
 */
@Immutable
data class Palette(
    val primary: Color,
    val secondary: Color,
    val easyGreen: Color,
    val hardAmber: Color,
    val freezeBlue: Color,
    val appBackground: Color,
    val surfacePrimary: Color,
    val surfaceSecondary: Color,
    val surfaceBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val shadow: Color,
    val overlayBackdrop: Color,
)

private fun rgb(r: Int, g: Int, b: Int, alpha: Float = 1f) =
    Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = alpha)

val LightPalette = Palette(
    primary = rgb(75, 63, 200),
    secondary = rgb(255, 99, 64),
    easyGreen = rgb(40, 182, 126),
    hardAmber = rgb(245, 166, 35),
    freezeBlue = rgb(0, 145, 199),
    appBackground = rgb(247, 243, 237),
    surfacePrimary = rgb(255, 255, 255),
    surfaceSecondary = rgb(239, 233, 227),
    surfaceBorder = rgb(225, 219, 235),
    textPrimary = rgb(28, 24, 48),
    textSecondary = rgb(122, 114, 145),
    shadow = rgb(28, 24, 48, alpha = 0.08f),
    overlayBackdrop = rgb(247, 243, 237, alpha = 0.88f),
)

val DarkPalette = Palette(
    primary = rgb(142, 129, 255),
    secondary = rgb(255, 134, 109),
    easyGreen = rgb(73, 214, 151),
    hardAmber = rgb(255, 193, 87),
    freezeBlue = rgb(94, 200, 245),
    appBackground = rgb(17, 19, 31),
    surfacePrimary = rgb(30, 34, 52),
    surfaceSecondary = rgb(40, 46, 69),
    surfaceBorder = rgb(65, 72, 98),
    textPrimary = rgb(244, 240, 255),
    textSecondary = rgb(164, 171, 196),
    shadow = rgb(0, 0, 0, alpha = 0.32f),
    overlayBackdrop = rgb(9, 11, 18, alpha = 0.74f),
)
