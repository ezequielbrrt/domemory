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
    @Test fun `intro advances then completion persists the selected difficulty and onboarding marker`() = runTest {
        val prefs = prefs(); val vm = OnboardingViewModel(prefs, this)
        vm.next(); vm.next(); vm.next(); assertEquals(OnboardingStep.DIFFICULTY, vm.state.value.step)
        vm.selectDifficulty(Difficulty.HARD); vm.finish { }; advanceUntilIdle()
        assertTrue(prefs.hasOnboarded.first()); assertTrue(prefs.onboardingIntroShown.first()); assertEquals(Difficulty.HARD, prefs.playerDifficulty.first())
    }
}
