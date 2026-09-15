package com.ezequielbrrt.domemory.ui.anim

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Port of iOS's `TileTapStyle` (`ios/.../Modules/Levels/LevelsView/LevelMapView.swift`): a
 * `ButtonStyle` that scales any tappable tile down to 0.92 on press and springs it back on
 * release (`.spring(response: 0.25, dampingFraction: 0.6)`), "so a tap reads as physical
 * contact rather than a silent jump to the next screen."
 *
 * SwiftUI's `response`/`dampingFraction` spring parameterization has no direct Compose
 * equivalent (Compose's [spring] takes `dampingRatio`/`stiffness`). The conversion is
 * `stiffness = (2*PI / response)^2` for a unit-mass spring, which recovers `stiffness ~= 631`
 * for `response = 0.25`; `dampingRatio` carries over unchanged. That keeps this a real port of
 * the physical spring iOS asked for, not just a similar-looking guess.
 */
private const val TileTapDampingRatio = 0.6f
private const val TileTapStiffness = 631f // (2*PI / 0.25)^2, from iOS's response: 0.25

/**
 * Applies [TileTapStyle]-equivalent press feedback to a clickable surface: scales to
 * [pressedScale] while pressed, springing back to 1 on release. Replaces
 * [Modifier.clickable]'s own ripple with this scale (indication is `null`), matching iOS's
 * `TileTapStyle`/`CardView`, which have no separate ripple concept — the scale *is* the
 * feedback.
 *
 * Use in place of a bare `Modifier.clickable(...)` on a "primarily-tappable game surface"
 * (level tiles, game cards) that should read as physically pressed rather than silently
 * activated.
 */
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = TileTapDampingRatio, stiffness = TileTapStiffness),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
