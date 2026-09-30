package com.ezequielbrrt.domemory.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.feature.levels.LivesEffect
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.ui.anim.rememberReduceMotion
import com.ezequielbrrt.domemory.ui.lottie.BundledLottie
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/**
 * Matches iOS's `LivesRow`: a compact row of filled and outline heart glyphs, with an
 * optional one-shot [effect] played over a single heart.
 *
 * The row always draws its real final state underneath — the clips end fully
 * transparent — so a reduce-motion player, who never sees the clip, sees exactly the same
 * hearts. [onEffectFinished] is the caller's cue to clear [effect] so it does not replay
 * on the next recomposition.
 */
@Composable
fun LivesRow(
    remaining: Int,
    modifier: Modifier = Modifier,
    total: Int = LevelLivesService.MAX_LIVES,
    fontSize: TextUnit = 16.sp,
    effect: LivesEffect? = null,
    onEffectFinished: (() -> Unit)? = null,
    /** A still-filled heart a pending loss will take unless the player rescues the game.
     * It pulses rather than breaking, because it hasn't gone yet (iOS's `atRiskSlot`). */
    atRiskSlot: Int? = null,
) {
    val palette = LocalPalette.current
    val reduceMotion = rememberReduceMotion()
    val accessibilityLabel = stringResource(R.string.levels_lives_remaining_format, remaining, total)
    // The heart briefly scaled up by a refill, so the burst has a heart physically popping
    // back in at its centre rather than just decorating one.
    var bumpedSlot by remember { mutableStateOf<Int?>(null) }
    val activeEffect = effect?.takeUnless { reduceMotion }
    val atRiskAlpha = if (atRiskSlot == null) {
        1f
    } else if (reduceMotion) {
        0.45f
    } else {
        val pulse by rememberInfiniteTransition(label = "atRiskHeart").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "atRiskHeartAlpha",
        )
        pulse
    }

    LaunchedEffect(activeEffect) {
        if (activeEffect is LivesEffect.Gained) {
            bumpedSlot = activeEffect.slot
            delay(250)
            bumpedSlot = null
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = accessibilityLabel },
    ) {
        repeat(total) { index ->
            val bump by animateFloatAsState(
                targetValue = if (bumpedSlot == index) 1.35f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 440f),
                label = "lifeBump",
            )
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (index < remaining) "♥" else "♡",
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = if (index < remaining) palette.secondary else palette.textSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.graphicsLayer {
                        val atRisk = index == atRiskSlot && index < remaining
                        // Dims and shrinks together, like iOS's phase-animated heart.
                        val pulse = if (atRisk) atRiskAlpha else 1f
                        alpha = pulse
                        scaleX = bump * (0.85f + 0.15f * pulse)
                        scaleY = bump * (0.85f + 0.15f * pulse)
                    },
                )
                if (activeEffect != null && activeEffect.slot == index) {
                    val glyph = with(androidx.compose.ui.platform.LocalDensity.current) { fontSize.toDp() }
                    when (activeEffect) {
                        // The clip's heart spans 60% of its canvas; 1.7x the glyph size
                        // lands it on top of the text heart before it splits.
                        is LivesEffect.Lost -> BundledLottie(
                            name = "heart-break",
                            tint = palette.secondary,
                            modifier = Modifier.size(glyph * 1.7f),
                            onFinished = onEffectFinished,
                        )
                        is LivesEffect.Gained -> BundledLottie(
                            name = "heart-refill",
                            tint = palette.secondary,
                            modifier = Modifier.size(glyph * 3f),
                            onFinished = onEffectFinished,
                        )
                    }
                }
            }
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
