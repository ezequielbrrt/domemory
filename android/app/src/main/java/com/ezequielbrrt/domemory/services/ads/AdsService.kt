package com.ezequielbrrt.domemory.services.ads

import android.content.Context
import com.ezequielbrrt.domemory.BuildConfig
import com.google.android.gms.ads.MobileAds
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

    private fun select(debug: Boolean, test: String, release: String) = if (debug) test else release

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
}

object AdsService {
    private val initialized = AtomicBoolean(false)

    /** SDK initialization is idempotent and deliberately happens before any placement load. */
    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            Thread { MobileAds.initialize(context.applicationContext) {} }.start()
        }
    }
}
