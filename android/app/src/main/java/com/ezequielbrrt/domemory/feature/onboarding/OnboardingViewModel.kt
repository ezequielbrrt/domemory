package com.ezequielbrrt.domemory.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val page: Int = 0,
    val isSaving: Boolean = false,
)

class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    private val workScope get() = scope ?: viewModelScope
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next(onComplete: () -> Unit) {
        if (_state.value.page == 2) finish(onComplete) else _state.value = _state.value.copy(page = _state.value.page + 1)
    }
    fun skipIntro(onComplete: () -> Unit) { finish(onComplete) }
    private fun finish(onComplete: () -> Unit) { workScope.launch { _state.value = _state.value.copy(isSaving = true); prefs.completeOnboarding(Difficulty.MEDIUM); onComplete() } }
}
