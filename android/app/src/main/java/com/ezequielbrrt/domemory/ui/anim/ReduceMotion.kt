package com.ezequielbrrt.domemory.ui.anim

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android's counterpart to SwiftUI's `accessibilityReduceMotion` environment value.
 *
 * Android has no dedicated "reduce motion" switch; the closest signal is the developer /
 * accessibility "Animator duration scale" set to *off*, which [ValueAnimator.areAnimatorsEnabled]
 * reports as `false`. Decorative animation (Lottie celebrations, staggered reveals) checks
 * this and shows its final static frame instead, exactly as iOS does for
 * `accessibilityReduceMotion` — a player who turned motion off must not get a confetti burst
 * anyway.
 *
 * Read once per composition rather than observed: the setting cannot change while a
 * composable that consults it is on screen without the user leaving the app first.
 */
@Composable
fun rememberReduceMotion(): Boolean = remember { !ValueAnimator.areAnimatorsEnabled() }
