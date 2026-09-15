package com.ezequielbrrt.domemory.ui.anim

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compose's closest analog to iOS's `.contentTransition(.numericText())`
 * (`ios/.../Modules/Memorize/MemorizeView/Views/PowerUpBar.swift`'s star-balance chip — the
 * only place iOS actually uses it; see that file before adding this elsewhere). SwiftUI's
 * `numericText` is a private system transition with no public Compose equivalent — there is
 * no per-digit rolling API — so this uses the directional slide-and-fade `AnimatedContent`
 * pattern Android's own Compose animation guidance recommends for a changing counter: the
 * incoming value slides in from the direction that matches whether it went up or down, while
 * the outgoing one slides out and fades, giving a comparable "the number is alive" feel
 * without emulating per-digit rolling.
 *
 * Generic over [T] so it works for star balances, lives counts, or any other
 * `Comparable` display value — not just [Int].
 */
@Composable
fun <T : Comparable<T>> NumericTransition(
    targetValue: T,
    modifier: Modifier = Modifier,
    label: String = "numericTransition",
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetValue,
        modifier = modifier,
        transitionSpec = {
            val goingUp = targetState > initialState
            val enter = slideInVertically(animationSpec = tween(220)) { height ->
                if (goingUp) height else -height
            } + fadeIn(animationSpec = tween(220))
            val exit = slideOutVertically(animationSpec = tween(220)) { height ->
                if (goingUp) -height else height
            } + fadeOut(animationSpec = tween(220))
            enter.togetherWith(exit).using(SizeTransform(clip = false))
        },
        label = label,
    ) { value ->
        content(value)
    }
}
