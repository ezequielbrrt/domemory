package com.ezequielbrrt.domemory.feature.adfree

import android.app.Activity
import com.ezequielbrrt.domemory.services.ads.AdFreeDayService
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The rewarded ad behind the offer, behind a seam so the state machine is testable without AdMob. */
interface AdFreeDayAds {
    /** Fetches the next ad and reports whether one is ready to show. */
    fun load(onReady: (Boolean) -> Unit)

    /** Shows the ready ad. [onReward] fires when it pays out; [onDismissed] always fires after. */
    fun show(onReward: () -> Unit, onDismissed: (rewarded: Boolean) -> Unit)
}

/** [AdsService] behind [AdFreeDayAds]. `ad_lifecycle` is logged inside [AdsService.showRewarded]. */
class AdMobAdFreeDayAds(private val activity: Activity?) : AdFreeDayAds {
    override fun load(onReady: (Boolean) -> Unit) {
        val context = activity ?: return onReady(false)
        AdsService.loadRewarded(context, AdPlacement.AD_FREE_DAY_REWARDED, onReady)
    }

    override fun show(onReward: () -> Unit, onDismissed: (rewarded: Boolean) -> Unit) =
        AdsService.showRewarded(activity, AdPlacement.AD_FREE_DAY_REWARDED, onReward, onDismissed)
}

/**
 * State behind the ad-free day sheet: which copy to show, whether an ad is ready, and the
 * two-ad chain that ends in the 24-hour grant. Port of iOS's `AdFreeDayOfferViewModel`.
 * Progress and the grant live in [AdFreeDayService]; the ad in [AdFreeDayAds].
 */
class AdFreeDayOffer(
    /** Where the sheet was opened from: `menu_levels`, `menu_mine`, `menu_all`,
     * `season_levels`, `multiplayer_lobby` or `settings`. */
    val source: String,
    private val service: AdFreeDayService,
    private val ads: AdFreeDayAds,
    private val scope: CoroutineScope,
    private val onAnalytics: (AnalyticsEvent) -> Unit = AnalyticsService::log,
    private val onHaptic: (HapticIntent) -> Unit = HapticsService::fire,
) {
    enum class Phase {
        /** A rewarded ad is being fetched; the primary button is disabled. */
        LOADING,

        /** An ad is ready and plays on the next tap. */
        READY,

        /** The ad is on screen. */
        PRESENTING,

        /** No ad could be loaded; the primary button offers a retry. Progress is kept. */
        NO_FILL,

        /** The 24-hour grant is running. */
        ACTIVE,
    }

    data class State(
        val phase: Phase = Phase.LOADING,
        val adsWatched: Int = 0,
        /** True for the whole first presentation, even after the intro is marked seen, so the
         * copy does not switch mid-sheet. */
        val isIntro: Boolean = false,
        val expiryMillis: Long? = null,
    ) {
        val requiredAds: Int get() = AdFreeDayService.REQUIRED_ADS
        val nextAdNumber: Int get() = minOf(adsWatched + 1, requiredAds)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var started = false
    private var rewardJob: Job? = null

    /** Called once when the sheet appears — iOS's `onAppear`. */
    suspend fun start() {
        if (started) return
        started = true
        val adsWatched = service.adsWatched()
        val isIntro = !service.hasSeenIntro()
        val active = service.isGrantActive()
        _state.value = State(
            phase = if (active) Phase.ACTIVE else Phase.LOADING,
            adsWatched = adsWatched,
            isIntro = isIntro,
            expiryMillis = service.expiryMillis.value,
        )
        onAnalytics(AnalyticsEvent.AdFreeDayOfferShown(source = source, isIntro = isIntro, adsWatched = adsWatched))
        if (isIntro) service.markIntroSeen()
        prepareAd()
    }

    /** Fetches the next ad, or reports that none is available. */
    fun prepareAd() {
        if (_state.value.phase == Phase.ACTIVE) return
        _state.update { it.copy(phase = Phase.LOADING) }
        ads.load { ready ->
            if (_state.value.phase == Phase.LOADING) {
                _state.update { it.copy(phase = if (ready) Phase.READY else Phase.NO_FILL) }
            }
        }
    }

    /** The primary button. Each ad needs its own tap; the second never auto-launches. */
    fun primaryAction() {
        when (_state.value.phase) {
            Phase.READY -> watchAd()
            Phase.NO_FILL -> prepareAd()
            Phase.LOADING, Phase.PRESENTING, Phase.ACTIVE -> Unit
        }
    }

    private fun watchAd() {
        _state.update { it.copy(phase = Phase.PRESENTING) }
        onAnalytics(AnalyticsEvent.AdFreeDayWatchTapped(source = source, adNumber = _state.value.nextAdNumber))
        var grantedExpiry: Long? = null
        ads.show(
            onReward = {
                rewardJob = scope.launch {
                    // Read before recording: completing the chain clears progress.
                    val adNumber = service.adsWatched() + 1
                    onAnalytics(AnalyticsEvent.AdFreeDayAdWatched(source = source, adNumber = adNumber))
                    val completed = service.recordAdWatched()
                    _state.update { it.copy(adsWatched = if (completed) 0 else adNumber) }
                    if (completed) {
                        grantedExpiry = service.grant()
                        onAnalytics(AnalyticsEvent.AdFreeDayGranted(source = source))
                        onHaptic(HapticIntent.REWARD)
                    }
                }
            },
            onDismissed = {
                scope.launch {
                    // The reward's DataStore writes are suspending; settle them before deciding.
                    rewardJob?.join()
                    val expiry = grantedExpiry
                    if (expiry != null) {
                        _state.update { it.copy(phase = Phase.ACTIVE, expiryMillis = expiry) }
                    } else {
                        // Either the next ad in the chain or a retry after a miss.
                        _state.update { it.copy(phase = Phase.LOADING) }
                        prepareAd()
                    }
                }
            },
        )
    }
}

/** The `state` value of `ad_free_day_entry_tapped`. */
internal fun adFreeDayEntryState(isActive: Boolean, adsWatched: Int): String = when {
    isActive -> "active"
    adsWatched > 0 -> "in_progress"
    else -> "idle"
}
