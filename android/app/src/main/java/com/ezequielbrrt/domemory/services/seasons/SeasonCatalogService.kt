package com.ezequielbrrt.domemory.services.seasons

import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

fun interface SeasonCatalogSource { suspend fun load(): String? }

/** Cache-first catalog: an unavailable or malformed network response never erases good offline data. */
class SeasonCatalogService(private val prefs: UserPreferences, private val remote: SeasonCatalogSource) {
    private val _seasons = MutableStateFlow<List<Season>>(emptyList())
    val seasons: StateFlow<List<Season>> = _seasons.asStateFlow()

    suspend fun loadCached(): List<Season> = SeasonDecoder.decode(prefs.seasonCatalogJson.first().orEmpty()).also { _seasons.value = it }

    suspend fun refresh(): List<Season> {
        val raw = remote.load() ?: return _seasons.value
        val decoded = SeasonDecoder.decode(raw)
        // A non-empty validated catalog is safe to cache; malformed responses decode empty
        // and deliberately leave the last known-good cache in place.
        if (decoded.isNotEmpty()) { prefs.setSeasonCatalogJson(raw); _seasons.value = decoded }
        return _seasons.value
    }
}
