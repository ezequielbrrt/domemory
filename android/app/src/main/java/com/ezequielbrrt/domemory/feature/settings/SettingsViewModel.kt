package com.ezequielbrrt.domemory.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsUiState(val difficulty: Difficulty = Difficulty.MEDIUM, val theme: ThemePreference = ThemePreference.SYSTEM, val hapticsEnabled: Boolean = true, val remindersEnabled: Boolean = false, val difficultyChanged: Boolean = false)

class SettingsViewModel(private val prefs: UserPreferences) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    init { combine(prefs.playerDifficulty, prefs.themePreference, prefs.hapticsEnabled, prefs.notificationsEnabled) { d, t, h, n -> _state.value.copy(difficulty = d, theme = t, hapticsEnabled = h, remindersEnabled = n) }.onEach { _state.value = it }.launchIn(viewModelScope) }
    fun setDifficulty(value: Difficulty) { if (value != _state.value.difficulty) viewModelScope.launch { prefs.setPlayerDifficulty(value); _state.value = _state.value.copy(difficultyChanged = true) } }
    fun setTheme(value: ThemePreference) = viewModelScope.launch { prefs.setThemePreference(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { prefs.setHapticsEnabled(value) }
    fun setReminders(value: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(value) }
}
