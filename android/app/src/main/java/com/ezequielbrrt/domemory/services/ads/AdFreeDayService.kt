package com.ezequielbrrt.domemory.services.ads

import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * The ad-free day (spec 12.3): progress through the two-ad chain and the 24-hour grant it
 * earns. Port of iOS's `AdFreeDayService` plus the rewarded half of its `PurchaseService`;
 * Android has no Remove Ads purchase, so the grant is the only thing that turns ads off.
 *
 * Progress is scoped to the local day through [DayKey], like lives, so a chain left
 * half-finished resets at midnight instead of lingering. One ad on its own grants nothing.
 */
class AdFreeDayService(
    private val prefs: UserPreferences,
    private val dayProvider: DayProvider,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** When the ad-free window ends, in epoch millis, or null. [AdFreeGate] reads this. */
    val expiryMillis: StateFlow<Long?> =
        prefs.rewardedRemoveAdsExpiry.stateIn(scope, SharingStarted.Eagerly, null)

    fun isGrantActive(atMillis: Long = now()): Boolean = (expiryMillis.value ?: 0L) > atMillis

    /** Ads watched so far today, in `0 until REQUIRED_ADS`. */
    suspend fun adsWatched(): Int =
        prefs.adFreeDayAdsWatchedFor(todayKey()).coerceAtMost(REQUIRED_ADS - 1)

    /**
     * Records one rewarded ad. Returns true when this ad completed the chain; the caller then
     * calls [grant]. Completing also clears the progress, so the next chain starts from zero.
     */
    suspend fun recordAdWatched(): Boolean = prefs.recordAdFreeDayAdWatched(todayKey(), REQUIRED_ADS)

    /** Extends the ad-free window by 24 hours, from its current end if one is running. */
    suspend fun grant(): Long = prefs.extendRewardedRemoveAds(now(), GRANT_MILLIS)

    /** Whether the apologetic first-open copy has been shown; later opens use neutral copy. */
    suspend fun hasSeenIntro(): Boolean = prefs.adFreeDayIntroShown.first()

    suspend fun markIntroSeen() = prefs.setAdFreeDayIntroShown(true)

    /** Debug/QA only: forgets today's progress and the intro flag. */
    suspend fun reset() = prefs.resetAdFreeDay()

    /** Debug/QA only: the debug menu's "Disable ads" toggle, iOS's year-long grant. */
    suspend fun grantForDebug() = prefs.extendRewardedRemoveAds(now(), DEBUG_GRANT_MILLIS)

    /** Debug/QA only: ends any ad-free window immediately. */
    suspend fun clearGrant() = prefs.clearRewardedRemoveAds()

    private fun todayKey(): String = DayKey.of(dayProvider.today())

    companion object {
        /** Ads to watch, each on its own tap, before the day is granted. */
        const val REQUIRED_ADS = 2
        const val GRANT_MILLIS = 24L * 60 * 60 * 1000
        private const val DEBUG_GRANT_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
