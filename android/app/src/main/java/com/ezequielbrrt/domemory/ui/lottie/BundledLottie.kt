package com.ezequielbrrt.domemory.ui.lottie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

/**
 * Plays a bundled Lottie animation — the Compose twin of iOS's `LottieView`
 * (`ios/.../Modules/SharedModules/Views/LottieView.swift`).
 *
 * [name] is the file's base name under the shared `assets/lottie/` directory at the
 * repository root, which `app/build.gradle.kts` adds as an assets root; the same JSON is
 * what iOS bundles, so a name that exists on one platform exists on the other. Call sites
 * only say what to play, whether it loops, and — for one-shot moments like a win
 * celebration — get told when it finished, mirroring the iOS wrapper's surface so the
 * two win screens can be read side by side.
 *
 * [tint] recolours every fill and stroke the clip's author named `tint` (see
 * `assets/lottie/generate_animations.py`) to a palette colour, so one file serves light
 * and dark. Clips authored in several colours, like the confetti, pass none.
 *
 * Reduce motion is the caller's decision, not this composable's: iOS's `WinModal` chooses
 * per moment between skipping the animation entirely (confetti) and jumping to the final
 * static state (stars), and the Android call sites make the same choices through
 * [com.ezequielbrrt.domemory.ui.anim.rememberReduceMotion].
 */
@Composable
fun BundledLottie(
    name: String,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    speed: Float = 1f,
    tint: Color? = null,
    onFinished: (() -> Unit)? = null,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("$name.json"))
    val animationState = animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        speed = speed,
    )
    val dynamicProperties = if (tint != null) {
        // Fills and strokes are separate Lottie properties; the keypath is the same
        // `**.tint` iOS targets, so both platforms recolour exactly the same shapes.
        rememberLottieDynamicProperties(
            rememberLottieDynamicProperty(LottieProperty.COLOR, tint.toArgb(), "**", TINT_SHAPE_NAME),
            rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, tint.toArgb(), "**", TINT_SHAPE_NAME),
        )
    } else {
        null
    }
    if (onFinished != null) {
        // `isAtEnd` alone is also true for a frame before the animation is (re)started, so
        // require the state to have actually stopped playing, matching Lottie-iOS's
        // `play { finished in ... }` semantics of "finished, not interrupted".
        LaunchedEffect(animationState.isAtEnd, animationState.isPlaying) {
            if (animationState.isAtEnd && !animationState.isPlaying && composition != null) {
                onFinished()
            }
        }
    }
    LottieAnimation(
        composition = composition,
        progress = { animationState.progress },
        modifier = modifier,
        dynamicProperties = dynamicProperties,
    )
}

/** The shape name every tintable clip uses; see `assets/lottie/generate_animations.py`. */
const val TINT_SHAPE_NAME = "tint"
