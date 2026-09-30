package com.ezequielbrrt.domemory.services.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The ads layer's read side of the ad-free window, the Android counterpart of iOS's
 * `PurchaseService.hasRemovedAds` gate. While it is active every *involuntary* placement
 * (banners, natives, the game-finished interstitial) stays off; rewarded placements are
 * opt-in and are never gated.
 *
 * An object rather than a constructor dependency, like [AdsService], because the banner and
 * native composables and [AdsService.notifyGameFinished] all need it and none of them has
 * the app container. [install] is called once from `DoMemoryApplication.onCreate`.
 */
object AdFreeGate {
    private var expiry: StateFlow<Long?> = MutableStateFlow(null)

    val expiryMillis: StateFlow<Long?> get() = expiry

    fun install(expiryMillis: StateFlow<Long?>) {
        expiry = expiryMillis
    }

    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean = (expiry.value ?: 0L) > nowMillis
}

/**
 * Whether involuntary ads should be hidden right now. Recomposes when a grant starts, and
 * again at the moment it ends, so a banner comes back without leaving the screen.
 */
@Composable
fun rememberInvoluntaryAdsSuppressed(): Boolean {
    val expiry by AdFreeGate.expiryMillis.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiry) {
        now = System.currentTimeMillis()
        val end = expiry ?: return@LaunchedEffect
        val remaining = end - now
        if (remaining > 0) {
            delay(remaining + 1)
            now = System.currentTimeMillis()
        }
    }
    return (expiry ?: 0L) > now
}
