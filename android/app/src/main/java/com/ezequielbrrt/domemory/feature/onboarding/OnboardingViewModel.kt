package com.ezequielbrrt.domemory.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Paging deliberately lives in `IntroCarousel`, not here. The carousel is swipeable, so
 * a page index owned by the view model could only ever be told "next" and would desync
 * the moment the player swiped. iOS splits it the same way: `IntroCarouselView` owns
 * `@State page` and `FeatureIntroView` owns only persistence and analytics.
 */
data class OnboardingUiState(
    val isSaving: Boolean = false,
)

class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    private val workScope get() = scope ?: viewModelScope
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** The player reached the end of the carousel and tapped "Get Started". */
    fun completeIntro(onComplete: () -> Unit) { finish(onComplete) { AnalyticsEvent.OnboardingIntroCompleted } }

    fun skipIntro(onComplete: () -> Unit) { finish(onComplete) { AnalyticsEvent.OnboardingIntroSkipped } }
    private fun finish(onComplete: () -> Unit, event: () -> AnalyticsEvent) {
        workScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            prefs.completeOnboarding(Difficulty.MEDIUM)
            AnalyticsService.log(event())
            onComplete()
        }
    }
}
