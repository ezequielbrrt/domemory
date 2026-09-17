package com.ezequielbrrt.domemory.services.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.ezequielbrrt.domemory.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for Firebase Analytics — the counterpart of
 * `ios/DoMemory/DoMemory/Configuration/AppConfiguration.swift`'s `enum AnalyticsService`.
 * [log] is the *only* sanctioned way to reach `FirebaseAnalytics` — nothing else in this
 * codebase should call `FirebaseAnalytics.getInstance(...).logEvent(...)` directly, the same
 * "one gated entry point" rule [com.ezequielbrrt.domemory.services.haptics.HapticsService]
 * documents for `Vibrator`.
 *
 * An `object`, not a constructed instance held in [com.ezequielbrrt.domemory.AppContainer] —
 * mirrors [com.ezequielbrrt.domemory.services.ads.AdsService] and
 * [com.ezequielbrrt.domemory.services.haptics.HapticsService], the other two singletons that
 * must be reachable both from a framework-free `ViewModel` (via a bare
 * `(AnalyticsEvent) -> Unit)?` callback, e.g. `GameViewModel.onAnalytics` — the same shape as
 * `GameViewModel.onHaptic`, for the identical reason: the moment a level starts, a pair is
 * matched, or a power-up is bought is *decided* inside the view model, not the view) *and*
 * directly from a composable's own click handler or `LaunchedEffect` for purely navigational
 * or screen-lifecycle events (screen views, the What's New sheet, the notification primer,
 * onboarding, a share-sheet tap) — mirroring [HapticsService.fire]'s own direct call sites in
 * `NavGraph.kt` for "purely navigational taps [that] have no state change to hang this off."
 * [initialize] is called once from `DoMemoryApplication.onCreate`, the same shape as
 * `AdsService.initialize`.
 *
 * **Why a `Context`-taking `FirebaseAnalytics.getInstance(context)` and not the `Firebase.analytics`
 * Kotlin-extension property:** every other Firebase call site in this codebase
 * (`MultiplayerService`'s `FirebaseAuth.getInstance()`/`FirebaseDatabase.getInstance()`,
 * `FirebaseBoardCatalogSource`) uses the plain static-factory style, not the `com.google.firebase.Firebase`
 * extension receiver — this follows that existing precedent rather than introducing a second style.
 *
 * A call before [initialize] (e.g. a stray test that skips it) is a silent no-op rather than a
 * crash — [firebaseAnalytics] stays null and [log] just returns, the same defensive shape
 * [HapticsService.fire] uses when its own `vibrator` is unset.
 */
object AnalyticsService {
    /** Matches iOS's `AnalyticsService.cardTapSampleRate` — every tap would be far too high a
     * volume to log unsampled. */
    const val CARD_TAP_SAMPLE_RATE = 0.2

    private val initialized = AtomicBoolean(false)
    private var firebaseAnalytics: FirebaseAnalytics? = null

    /** Called once from `DoMemoryApplication.onCreate`. */
    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    /** The single gate. Every call site — view model callback or composable/service call —
     * goes through here.
     *
     * The debug log is wrapped in [runCatching]: `android.util.Log` is not mocked in a plain
     * JVM unit test (no `testOptions.unitTests.isReturnDefaultValues`/Robolectric configured
     * in this module), so a test that wires a real [onAnalytics]-style callback straight to
     * [log] — plausible, since every other bare callback in this codebase (`onHaptic`,
     * `onCompletionInterstitial`) defaults to null and tests only exercise it when a test
     * explicitly opts in — would otherwise crash on `BuildConfig.DEBUG == true` (unit tests
     * compile against the debug variant) with "Method d in android.util.Log not mocked".
     * `firebaseAnalytics?.logEvent(...)` is already unconditionally safe: [firebaseAnalytics]
     * stays null until [initialize] runs, which no unit test calls.
     */
    fun log(event: AnalyticsEvent) {
        firebaseAnalytics?.logEvent(event.name, event.parameters.toBundle())
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(TAG, "${event.name}: ${event.parameters}") }
        }
    }

    /** Matches iOS's `AnalyticsService.shouldSample(_:)` — `rate` in `0..1`. */
    fun shouldSample(rate: Double): Boolean = Math.random() < rate

    private fun Map<String, Any>.toBundle(): Bundle {
        val bundle = Bundle()
        for ((key, value) in this) {
            when (value) {
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putFloat(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }

    private const val TAG = "Analytics"
}
