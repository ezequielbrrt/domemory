package com.ezequielbrrt.domemory.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.ui.components.IntroCarousel
import com.ezequielbrrt.domemory.ui.components.IntroIllustration
import com.ezequielbrrt.domemory.ui.components.IntroSlide

/**
 * The first-launch feature intro, ported from iOS's `FeatureIntroView.swift`: three
 * illustrated slides introducing multiplayer, custom memoramas and the Daily Challenge.
 *
 * These replace an earlier text-only screen whose copy (match the pairs / play at your
 * pace / make it yours) described the rules rather than the features and matched no iOS
 * slide. The `intro_*` strings used here were already translated into all ten locales
 * for the Levels intro, so no new translation was needed.
 *
 * [onNext] fires only from the last page — [IntroCarousel] handles paging itself.
 */
@Composable
fun OnboardingScreen(state: OnboardingUiState, onNext: () -> Unit, onSkip: () -> Unit) {
    LaunchedEffect(Unit) {
        AnalyticsService.log(
            AnalyticsEvent.ScreenView(screenName = "onboarding_intro", screenClass = "OnboardingScreen"),
        )
    }

    val slides = listOf(
        IntroSlide(
            icon = Icons.Filled.People,
            color = { it.primary },
            title = stringResource(R.string.intro_multiplayer_title),
            subtitle = stringResource(R.string.intro_multiplayer_subtitle),
            illustration = IntroIllustration(
                light = R.drawable.onboarding_play_with_friends,
                dark = R.drawable.onboarding_play_with_friends_dark,
            ),
        ),
        IntroSlide(
            icon = Icons.Filled.GridView,
            color = { it.easyGreen },
            title = stringResource(R.string.intro_custom_title),
            subtitle = stringResource(R.string.intro_custom_subtitle),
            illustration = IntroIllustration(
                light = R.drawable.onboarding_make_it_yours,
                dark = R.drawable.onboarding_make_it_yours_dark,
            ),
        ),
        IntroSlide(
            icon = Icons.Filled.LocalFireDepartment,
            color = { it.hardAmber },
            title = stringResource(R.string.intro_daily_title),
            subtitle = stringResource(R.string.intro_daily_subtitle),
            illustration = IntroIllustration(
                light = R.drawable.onboarding_come_back_daily,
                dark = R.drawable.onboarding_come_back_daily_dark,
            ),
        ),
    )

    IntroCarousel(
        slides = slides,
        finishTitle = stringResource(R.string.intro_get_started),
        // `isSaving` guards a second tap while the completion write is still in flight,
        // which the old two-button layout got from its `enabled` flags.
        onSkip = { if (!state.isSaving) onSkip() },
        onFinish = { if (!state.isSaving) onNext() },
    )
}
