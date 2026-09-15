package com.ezequielbrrt.domemory.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.services.stats.Achievement
import com.ezequielbrrt.domemory.services.stats.ProfileStats
import com.ezequielbrrt.domemory.services.stats.ProfileStatsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AchievementsUiState(
    val loading: Boolean = true,
    val stats: ProfileStats = ProfileStats(),
    val achievements: List<Achievement> = emptyList(),
)

/**
 * Loads one [ProfileStats] snapshot per screen visit (spec: [ProfileStatsService]'s own doc
 * on why this is a suspend snapshot, not a live collector — `UserPreferences` has no
 * synchronous read the way iOS's `UserDefaults` does, so [AchievementsView]-on-iOS's
 * synchronous `let stats = ProfileStatsService.shared.stats` becomes an async load here, with
 * [AchievementsUiState.loading] covering the one frame that's visible on Android and never on
 * iOS — a documented, deliberate seam, not a missed requirement.
 */
class AchievementsViewModel(private val service: ProfileStatsService) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stats = service.stats()
            _state.value = AchievementsUiState(
                loading = false,
                stats = stats,
                achievements = ProfileStatsService.achievements(stats),
            )
        }
    }
}
