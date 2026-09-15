package com.ezequielbrrt.domemory.feature.levels

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.services.levels.LevelProgressService
import com.ezequielbrrt.domemory.services.levels.LevelsIntroGate
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Pins the haptic moments `LevelsViewModel` newly wires — the Android counterpart of iOS's
 * `LevelsView.onSelect` (`.select`/`.warning`) and `LevelsViewModel.buyLifeWithStars`/
 * `watchAdForLife` (`.reward`). Built the same way `LevelProgressServiceTest`/
 * `LevelLivesServiceTest` are: real services over a temp-file `UserPreferences`, no mocking
 * framework, with the view model's own `scope` override standing in for `viewModelScope` so
 * `runTest`'s `TestScope` drives every coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LevelsViewModelHapticsTest {

    private fun tempFile(name: String) = File.createTempFile(name, ".preferences_pb").also { it.deleteOnExit() }

    private fun TestScope.viewModel(
        prefs: UserPreferences = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { tempFile("levels_haptics") })),
        day: LocalDate = LocalDate.of(2026, 9, 15),
        fired: MutableList<HapticIntent> = mutableListOf(),
    ): Pair<LevelsViewModel, MutableList<HapticIntent>> {
        val vm = LevelsViewModel(
            progress = LevelProgressService(prefs, this),
            lives = LevelLivesService(prefs, DayProvider { day }),
            wallet = StarWalletService(prefs, this),
            introGate = LevelsIntroGate(prefs),
            scope = this,
            onHaptic = { fired.add(it) },
        )
        advanceUntilIdle()
        return vm to fired
    }

    @Test fun `starting a level with lives remaining fires SELECT`() = runTest {
        val (vm, fired) = viewModel()
        assertTrue(vm.attemptStart(1))
        assertEquals(listOf(HapticIntent.SELECT), fired)
    }

    @Test fun `starting a level at zero lives fires WARNING instead, and refuses`() = runTest {
        val (vm, fired) = viewModel()
        // LevelLivesService.MAX_LIVES == 4 — spend every daily life first via a fresh
        // LevelLivesService over the same prefs (the view model doesn't expose spendOnLoss).
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { tempFile("levels_haptics_zero") }))
        val day = LocalDate.of(2026, 9, 15)
        val lives = LevelLivesService(prefs, DayProvider { day })
        repeat(4) { lives.spendOnLoss() }
        val (drained, drainedFired) = viewModel(prefs = prefs, day = day)

        assertFalse(drained.attemptStart(1))
        assertEquals(listOf(HapticIntent.WARNING), drainedFired)
    }

    @Test fun `buying a life with stars fires REWARD only on a successful spend`() = runTest {
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { tempFile("levels_haptics_buy") }))
        val (vm, fired) = viewModel(prefs = prefs)

        // Not enough stars yet — no REWARD (and no double-buzz TAP either, matching iOS's
        // own comment on this exact button).
        vm.buyLifeWithStars()
        advanceUntilIdle()
        assertTrue(fired.isEmpty())

        val wallet = StarWalletService(prefs, this)
        wallet.credit(LevelPowerUp.LIFE_COST)
        advanceUntilIdle()

        vm.buyLifeWithStars()
        advanceUntilIdle()
        assertEquals(listOf(HapticIntent.REWARD), fired)
    }

    @Test fun `an ad-granted life reward fires REWARD`() = runTest {
        val (vm, fired) = viewModel()
        vm.applyLifeRewardFromAd()
        advanceUntilIdle()
        assertEquals(listOf(HapticIntent.REWARD), fired)
    }
}
