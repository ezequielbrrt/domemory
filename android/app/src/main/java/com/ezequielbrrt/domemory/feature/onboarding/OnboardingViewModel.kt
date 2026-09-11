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

enum class OnboardingStep { INTRO, DIFFICULTY }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    val page: Int = 0,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val isSaving: Boolean = false,
)

class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    private val workScope get() = scope ?: viewModelScope
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next() { _state.value = if (_state.value.page == 2) _state.value.copy(step = OnboardingStep.DIFFICULTY) else _state.value.copy(page = _state.value.page + 1) }
    fun skipIntro() { _state.value = _state.value.copy(step = OnboardingStep.DIFFICULTY) }
    fun selectDifficulty(value: Difficulty) { _state.value = _state.value.copy(difficulty = value) }
    fun finish(onComplete: () -> Unit) { workScope.launch { _state.value = _state.value.copy(isSaving = true); prefs.completeOnboarding(_state.value.difficulty); onComplete() } }
}
