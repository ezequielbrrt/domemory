package com.ezequielbrrt.domemory.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.notifications.NotificationService
import com.ezequielbrrt.domemory.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsUiState(val difficulty: Difficulty = Difficulty.MEDIUM, val theme: ThemePreference = ThemePreference.SYSTEM, val hapticsEnabled: Boolean = true, val remindersEnabled: Boolean = false, val difficultyChanged: Boolean = false)

class SettingsViewModel(private val prefs: UserPreferences, private val notifications: NotificationService) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    init { combine(prefs.playerDifficulty, prefs.themePreference, prefs.hapticsEnabled, prefs.notificationsEnabled) { d, t, h, n -> _state.value.copy(difficulty = d, theme = t, hapticsEnabled = h, remindersEnabled = n) }.onEach { _state.value = it }.launchIn(viewModelScope) }
    fun setDifficulty(value: Difficulty) { if (value != _state.value.difficulty) viewModelScope.launch { prefs.setPlayerDifficulty(value); _state.value = _state.value.copy(difficultyChanged = true) } }
    fun setTheme(value: ThemePreference) = viewModelScope.launch { prefs.setThemePreference(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { prefs.setHapticsEnabled(value) }

    /**
     * The toggle's "on" path never sets `notificationsEnabled` directly — it is called only
     * after [com.ezequielbrrt.domemory.feature.notifications
     * .rememberNotificationPermissionRequester] confirms a grant (or determines none is
     * needed), same as the primer (spec 11.3: "reused by the Settings toggle"). This is the
     * one place a Settings-driven grant is allowed to flip the flag — going through
     * [NotificationService.activateReminders] is what keeps it from repeating spec 11.2's
     * permission-sync bug.
     */
    fun enableReminders() = viewModelScope.launch { notifications.activateReminders() }

    /** Turning reminders off needs no permission dance — just the flag and a cancel. */
    fun disableReminders() = viewModelScope.launch {
        prefs.setNotificationsEnabled(false)
        notifications.cancelAll()
    }
}
