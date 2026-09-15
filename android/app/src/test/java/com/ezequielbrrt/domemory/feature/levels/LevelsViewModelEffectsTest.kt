package com.ezequielbrrt.domemory.feature.levels

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelProgressService
import com.ezequielbrrt.domemory.services.levels.LevelsIntroGate
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Pins when the map header owes the player a heart or star animation. Built the same way
 * `LevelsViewModelHapticsTest` is: real services over a temp-file `UserPreferences`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LevelsViewModelEffectsTest {

    private fun tempFile(name: String) = File.createTempFile(name, ".preferences_pb").also { it.deleteOnExit() }

    private class Harness(val prefs: UserPreferences, val lives: LevelLivesService, val wallet: StarWalletService)

    private fun TestScope.harness(name: String): Harness {
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { tempFile(name) }))
        val day = LocalDate.of(2026, 9, 15)
        return Harness(prefs, LevelLivesService(prefs, DayProvider { day }), StarWalletService(prefs, this))
    }

    private fun TestScope.viewModel(h: Harness): LevelsViewModel {
        val vm = LevelsViewModel(
            progress = LevelProgressService(h.prefs, this),
            lives = h.lives,
            wallet = h.wallet,
            introGate = LevelsIntroGate(h.prefs),
            scope = this,
        )
        advanceUntilIdle()
        return vm
    }

    @Test fun `the first load never animates, even when lives are already down`() = runTest {
        val h = harness("effects_first_load")
        repeat(2) { h.lives.spendOnLoss() }
        val vm = viewModel(h)

        assertEquals(2, vm.uiState.value.livesRemaining)
        assertNull(vm.uiState.value.livesEffect)
        assertFalse(vm.uiState.value.starsCredited)
    }

    @Test fun `a life lost during play breaks its heart on the next refresh`() = runTest {
        val h = harness("effects_loss")
        val vm = viewModel(h)
        h.lives.spendOnLoss()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(LivesEffect.Lost(3), vm.uiState.value.livesEffect)
        vm.consumeLivesEffect()
        assertNull(vm.uiState.value.livesEffect)
    }

    @Test fun `a credit sparkles and a refresh with nothing new does not`() = runTest {
        val h = harness("effects_credit")
        val vm = viewModel(h)
        h.wallet.credit(2)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.starsCredited)
        vm.consumeStarsCredited()

        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.starsCredited)
        assertNull(vm.uiState.value.livesEffect)
    }

    @Test fun `buying a life bursts the refilled heart and never sparkles the spend`() = runTest {
        val h = harness("effects_buy")
        h.wallet.credit(20)
        h.lives.spendOnLoss()
        val vm = viewModel(h)

        vm.buyLifeWithStars()
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.livesRemaining)
        assertEquals(LivesEffect.Gained(3), vm.uiState.value.livesEffect)
        assertFalse(vm.uiState.value.starsCredited)
    }
}
