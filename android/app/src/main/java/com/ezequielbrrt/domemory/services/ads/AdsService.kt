package com.ezequielbrrt.domemory.services.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.ezequielbrrt.domemory.BuildConfig
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/** The Android AdMob placement catalog. Rewarded placements intentionally share one unit. */
enum class AdPlacement(val type: Type) {
    HOME_BANNER(Type.BANNER),
    GAME_BANNER(Type.BANNER),
    GAME_FINISHED_INTERSTITIAL(Type.INTERSTITIAL),
    GAME_REWARDED_EXTRA_TIME(Type.REWARDED),
    GAME_REWARDED_HINT(Type.REWARDED),
    LEVELS_REWARDED_LIFE(Type.REWARDED),
    LEVELS_REWARDED_FORGIVE(Type.REWARDED),
    APP_OPEN(Type.APP_OPEN),
    MULTIPLAYER_FINISHED_NATIVE(Type.NATIVE);

    enum class Type { BANNER, INTERSTITIAL, REWARDED, APP_OPEN, NATIVE }
}

object AdUnitConfiguration {
    private const val HOME_BANNER = "ca-app-pub-4297174845441653/5149706669"
    private const val GAME_BANNER = "ca-app-pub-4297174845441653/3454937887"
    private const val GAME_FINISHED = "ca-app-pub-4297174845441653/5739899454"
    private const val REWARDED = "ca-app-pub-4297174845441653/7306552982"
    private const val APP_OPEN = "ca-app-pub-4297174845441653/4426817783"
    private const val MULTIPLAYER_NATIVE = "ca-app-pub-4297174845441653/7887550646"

    /** Debug builds use Google's public demo units, never the production units. */
    fun unitId(placement: AdPlacement, debug: Boolean = BuildConfig.DEBUG): String = when (placement) {
        AdPlacement.HOME_BANNER -> select(debug, TEST_BANNER, HOME_BANNER)
        AdPlacement.GAME_BANNER -> select(debug, TEST_BANNER, GAME_BANNER)
        AdPlacement.GAME_FINISHED_INTERSTITIAL -> select(debug, TEST_INTERSTITIAL, GAME_FINISHED)
        AdPlacement.GAME_REWARDED_EXTRA_TIME,
        AdPlacement.GAME_REWARDED_HINT,
        AdPlacement.LEVELS_REWARDED_LIFE,
        AdPlacement.LEVELS_REWARDED_FORGIVE -> select(debug, TEST_REWARDED, REWARDED)
        AdPlacement.APP_OPEN -> select(debug, TEST_APP_OPEN, APP_OPEN)
        AdPlacement.MULTIPLAYER_FINISHED_NATIVE -> select(debug, TEST_NATIVE, MULTIPLAYER_NATIVE)
    }

    /**
     * A placement with no configured (blank, in release) ad unit id must not attempt to
     * load or show — mirrors iOS's `AdUnitConfiguration.configuredUnitID` returning `nil`
     * for a release id left empty (see `.settingsRewardedRemoveAds`, which has no Android
     * equivalent). Every Android placement is currently non-blank in both build types, so
     * this is a forward guard, not a live gate today.
     */
    fun isConfigured(placement: AdPlacement, debug: Boolean = BuildConfig.DEBUG): Boolean =
        unitId(placement, debug).isNotBlank()

    private fun select(debug: Boolean, test: String, release: String) = if (debug) test else release

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
}

/**
 * SDK adapter for interstitial, rewarded and native presentation. Banner loading lives in
 * [AdMobBanner], native rendering in [AdMobNativeAdView] — both Compose-lifecycle-owned and
 * stateless from this object's point of view. This object owns everything that must survive
 * *across* a single composable's lifecycle: the cached interstitial, the cached rewarded ads
 * (one per placement), and the in-memory [AdFrequencyState] cadence counter.
 *
 * **App-open is deliberately not implemented.** [AdPlacement.APP_OPEN] and its unit id exist
 * (Phase 7 foundation), but there is no launch-sequence state machine on Android yet for it to
 * hook into (see `ANDROID_PLAN.md` O5, and `LevelsIntroGate`'s own note about the same missing
 * sequence) — wiring it now would mean inventing that sequence, not just ad presentation.
 *
 * **`AdFrequencyState` is in-memory only, not persisted.** iOS persists its interstitial
 * qualifying-completion counter in `UserDefaults` so it survives a relaunch mid-cadence; this
 * resets on every process start. `ANDROID_PLAN.md`'s own Phase 7 scope calls DataStore for
 * this "unlikely" to be needed, and the cadence is a UX nicety, not a correctness rule — a
 * fresh process simply starts a fresh interstitial count, one session-visible divergence from
 * iOS, documented here rather than silently carried.
 *
 * **Remove Ads has no Android entitlement layer.** iOS gates banners, natives, interstitials
 * and app-open on `PurchaseService.shared.hasRemovedAds`; nothing here does, because there is
 * no purchase service to gate on (Phase 7 is explicitly ads-only, IAP deferred). Every
 * `involuntaryAdsSuppressed` parameter below defaults to `false` for the same reason — it
 * exists so the day a purchase layer lands, wiring it is a one-line change here, not a new gate.
 */
object AdsService {
    private val initialized = AtomicBoolean(false)

    private var interstitialAd: InterstitialAd? = null
    private var interstitialPlacement: AdPlacement? = null
    private val rewardedAds = mutableMapOf<AdPlacement, RewardedAd>()

    /** The single cadence counter every full-screen ad decision reads and writes. */
    private var frequencyState = AdFrequencyState()

    /** SDK initialization is idempotent and deliberately happens before any placement load. */
    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            Thread { MobileAds.initialize(context.applicationContext) {} }.start()
        }
    }

    fun isRewardedConfigured(placement: AdPlacement): Boolean =
        placement.type == AdPlacement.Type.REWARDED && AdUnitConfiguration.isConfigured(placement)

    fun isNativeConfigured(placement: AdPlacement): Boolean =
        placement.type == AdPlacement.Type.NATIVE && AdUnitConfiguration.isConfigured(placement)

    // --- Interstitial (spec: game_finished_interstitial) ------------------------------

    /** Loads the next interstitial for [placement]. A no-op while one is already cached. */
    fun loadInterstitial(context: Context, placement: AdPlacement = AdPlacement.GAME_FINISHED_INTERSTITIAL) {
        require(placement.type == AdPlacement.Type.INTERSTITIAL) { "$placement is not an interstitial placement" }
        if (!AdUnitConfiguration.isConfigured(placement)) return
        if (interstitialPlacement == placement && interstitialAd != null) return

        InterstitialAd.load(
            context.applicationContext,
            AdUnitConfiguration.unitId(placement),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.setFullScreenContentCallback(object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            interstitialPlacement = null
                            loadInterstitial(context, placement)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            interstitialPlacement = null
                        }
                    })
                    interstitialAd = ad
                    interstitialPlacement = placement
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    interstitialPlacement = null
                }
            },
        )
    }

    /**
     * The single entry point [com.ezequielbrrt.domemory.feature.game.GameViewModel]'s
     * `onCompletionInterstitial` callback resolves into — combines
     * [GameFinishedInterstitialTrigger]'s pure cadence decision with the actual SDK show/load
     * calls. [allowInterstitial] is `false` only for a paid level skip (see the trigger's own
     * doc). When the decision is to request but no ad is cached yet, this kicks off a load for
     * the *next* eligible completion rather than blocking or queuing an auto-show — the pure
     * cadence state is left at/above its threshold either way (only an actual presentation
     * resets it), so the very next finish retries instead of waiting a full cadence cycle.
     */
    fun notifyGameFinished(
        activity: Activity?,
        difficulty: Difficulty,
        gameDurationMillis: Long,
        allowInterstitial: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val decision = GameFinishedInterstitialTrigger.evaluate(
            state = frequencyState,
            difficulty = difficulty,
            gameDurationMillis = gameDurationMillis,
            nowMillis = nowMillis,
            allowInterstitial = allowInterstitial,
        )
        frequencyState = decision.nextState
        if (!decision.shouldRequestPresentation) return

        if (presentInterstitial(activity, AdPlacement.GAME_FINISHED_INTERSTITIAL)) {
            frequencyState = AdFrequencyCap.recordInterstitialPresented(frequencyState, nowMillis)
        } else {
            activity?.applicationContext?.let { loadInterstitial(it, AdPlacement.GAME_FINISHED_INTERSTITIAL) }
        }
    }

    private fun presentInterstitial(activity: Activity?, placement: AdPlacement): Boolean {
        val ad = interstitialAd
        if (activity == null || ad == null || interstitialPlacement != placement) return false
        ad.show(activity)
        return true
    }

    // --- Rewarded (spec: levels_rewarded_life, levels_rewarded_forgive, and the
    // game_rewarded_extra_time/hint placements — the latter two are configured but have no
    // Android call site yet; see android/ANDROID_PLAN.md Phase 7 for why) ------------------

    /** Loads the next rewarded ad for [placement]. A no-op while one is already cached. */
    fun loadRewarded(context: Context, placement: AdPlacement) {
        require(placement.type == AdPlacement.Type.REWARDED) { "$placement is not a rewarded placement" }
        if (!AdUnitConfiguration.isConfigured(placement)) return
        if (rewardedAds[placement] != null) return

        RewardedAd.load(
            context.applicationContext,
            AdUnitConfiguration.unitId(placement),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAds[placement] = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAds.remove(placement)
                }
            },
        )
    }

    /**
     * Presents [placement] if a cached ad is ready. [onReward] fires once, exactly when the
     * SDK reports an earned reward — the caller applies whatever that placement grants
     * (mirrors iOS's `presentRewardedAd(for:reward:)`). [onDismissed] always fires afterward
     * with whether a reward was actually earned, including the "not ready" path (`false`,
     * fired synchronously) so a caller mid-navigation never hangs waiting on it.
     */
    fun showRewarded(
        activity: Activity?,
        placement: AdPlacement,
        onReward: () -> Unit,
        onDismissed: (rewarded: Boolean) -> Unit = {},
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val ad = rewardedAds[placement]
        if (activity == null || ad == null) {
            activity?.applicationContext?.let { loadRewarded(it, placement) }
            onDismissed(false)
            return
        }

        rewardedAds.remove(placement)
        var earnedReward = false
        ad.setFullScreenContentCallback(object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded(activity.applicationContext, placement)
                onDismissed(earnedReward)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                loadRewarded(activity.applicationContext, placement)
                onDismissed(false)
            }
        })
        ad.show(activity, OnUserEarnedRewardListener {
            earnedReward = true
            frequencyState = AdFrequencyCap.recordRewardedPresented(frequencyState, nowMillis)
            onReward()
        })
    }
}

/** Unwraps a possibly-decorated [Context] (e.g. a Compose `LocalContext`) down to the
 * [Activity] a full-screen ad needs to present against, or null if none wraps one — a
 * background/application `Context` cannot show a full-screen ad. */
internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
