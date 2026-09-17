package com.ezequielbrrt.domemory.feature.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private fun TestScope.prefs(): UserPreferences { val file = File.createTempFile("onboarding", ".preferences_pb"); file.deleteOnExit(); return UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file })) }
    // Paging is no longer the view model's business — `IntroCarousel` owns it, because the
    // carousel is swipeable and a view-model page index could only ever be told "next".
    @Test fun `completing the intro defaults to medium difficulty`() = runTest {
        val prefs = prefs(); val vm = OnboardingViewModel(prefs, this)
        var completed = false
        vm.completeIntro { completed = true }; advanceUntilIdle()
        assertTrue(completed); assertTrue(prefs.hasOnboarded.first()); assertTrue(prefs.onboardingIntroShown.first()); assertEquals(Difficulty.MEDIUM, prefs.playerDifficulty.first())
    }
    @Test fun `skipping the intro also defaults to medium difficulty`() = runTest {
        val prefs = prefs(); val vm = OnboardingViewModel(prefs, this)
        var completed = false
        vm.skipIntro { completed = true }; advanceUntilIdle()
        assertTrue(completed); assertTrue(prefs.hasOnboarded.first()); assertEquals(Difficulty.MEDIUM, prefs.playerDifficulty.first())
    }
}
