package com.ezequielbrrt.domemory.services.seasons

import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

fun interface SeasonCatalogSource { suspend fun load(): String? }

/**
 * Cache-first catalog: an unavailable or malformed network response never erases good
 * offline data (spec 9.7).
 *
 * iOS reads its UserDefaults cache **synchronously** at init so the season card renders
 * on cold launch with no network round trip. DataStore has no synchronous read, so the
 * Android equivalent is [loadCached] launched from `AppContainer`'s application-scoped
 * coroutine as early as possible — see the class doc on `AppContainer` for why manual DI
 * already accepts this asynchronous-at-startup shape for `LevelProgressService`.
 */
class SeasonCatalogService(private val prefs: UserPreferences, private val remote: SeasonCatalogSource) {
    private val _seasons = MutableStateFlow<List<Season>>(emptyList())
    val seasons: StateFlow<List<Season>> = _seasons.asStateFlow()

    /**
     * The season to show today, or null when there is none — including every failure mode:
     * no cache and no network, a kill switch flipped off, or a payload that decoded to
     * nothing. Call [refreshActive] to re-evaluate (e.g. on app foreground, spec 9.4) — the
     * activation window is a day boundary, so a value computed at cold launch can go stale
     * in a long-lived process.
     */
    private val _activeSeason = MutableStateFlow<Season?>(null)
    val activeSeason: StateFlow<Season?> = _activeSeason.asStateFlow()

    suspend fun loadCached(today: String = todayKey()): List<Season> =
        SeasonDecoder.decode(prefs.seasonCatalogJson.first().orEmpty()).also {
            _seasons.value = it
            refreshActive(today)
        }

    suspend fun refresh(today: String = todayKey()): List<Season> {
        val raw = remote.load()
        if (raw != null) {
            val decoded = SeasonDecoder.decode(raw)
            // A non-empty validated catalog is safe to cache; malformed responses decode
            // empty and deliberately leave the last known-good cache in place.
            if (decoded.isNotEmpty()) { prefs.setSeasonCatalogJson(raw); _seasons.value = decoded }
        }
        refreshActive(today)
        return _seasons.value
    }

    /** Re-picks the active season from what is already known, with no network round trip. */
    fun refreshActive(today: String = todayKey()) {
        _activeSeason.value = SeasonDecoder.active(_seasons.value, today)
    }

    private fun todayKey(): String = DayKey.of(LocalDate.now())
}
