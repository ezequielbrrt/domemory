package com.ezequielbrrt.domemory.feature.debug

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Counts taps toward the hidden debug menu, porting iOS's `DebugMenuTapTrigger`: five taps
 * trigger it, and a tap arriving more than [maxGapMillis] after the previous one starts the
 * count over. A strict five-tap gesture would have to land as one fast, tight burst, which
 * is too finicky for a real QA workflow. Pure, with the clock passed in, so it is testable.
 */
internal class DebugMenuTapCounter(
    private val requiredTaps: Int = 5,
    private val maxGapMillis: Long = 1_500,
) {
    private var count = 0
    private var lastTapMillis: Long? = null

    /** Records a tap at [nowMillis]; returns true on the tap that completes the sequence. */
    fun registerTap(nowMillis: Long): Boolean {
        val last = lastTapMillis
        if (last != null && nowMillis - last > maxGapMillis) count = 0
        lastTapMillis = nowMillis
        count += 1
        if (count < requiredTaps) return false
        count = 0
        lastTapMillis = null
        return true
    }
}

/**
 * Fires [onTrigger] after five taps on this element, each within 1.5s of the last. No ripple:
 * the element it sits on (the Settings title) must not look tappable. Call sites gate it on
 * `BuildConfig.DEBUG`, the counterpart of iOS's `#if DEBUG`; R8 drops the unreachable menu
 * from release builds.
 */
fun Modifier.debugMenuTapTrigger(onTrigger: () -> Unit): Modifier = composed {
    val counter = remember { DebugMenuTapCounter() }
    val currentOnTrigger by rememberUpdatedState(onTrigger)
    pointerInput(Unit) {
        detectTapGestures {
            if (counter.registerTap(SystemClock.uptimeMillis())) currentOnTrigger()
        }
    }
}
