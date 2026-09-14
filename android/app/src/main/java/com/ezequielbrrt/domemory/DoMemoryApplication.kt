package com.ezequielbrrt.domemory

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ezequielbrrt.domemory.widget.DailyChallengeWidgetScheduler
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import okio.Path.Companion.toOkioPath

class DoMemoryApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(context = this)
        AdsService.initialize(this)
        HapticsService.initialize(this, container.prefs, container.applicationScope)
        // Spec 8.1's midnight refresh. Idempotent across process restarts: KEEP (see the
        // scheduler's doc) leaves an already-armed job's phase alone rather than recomputing
        // it from every app launch.
        DailyChallengeWidgetScheduler.scheduleMidnightRefresh(this)
    }

    /**
     * Season artwork is never bundled — seasons ship from Firebase without an app release
     * (root CLAUDE.md, spec 9.7) — so every card/background image is a network fetch behind
     * this cache. Coil's default in-memory cache (evicted under pressure) covers the memory
     * half of the spec's contract; this configures the ~64 MB LRU disk half explicitly.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("season_artwork").toOkioPath())
                    .maxSizeBytes(SEASON_ARTWORK_DISK_CACHE_BYTES)
                    .build()
            }
            .build()

    private companion object {
        const val SEASON_ARTWORK_DISK_CACHE_BYTES = 64L * 1024 * 1024
    }
}
