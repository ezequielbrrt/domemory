package com.ezequielbrrt.domemory.feature.seasons

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.feature.levels.LivesEffect
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
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
 * Pins the season map's lives gate and header-effect wiring — the twin of
 * `LevelsViewModelHapticsTest`/`LevelsViewModelEffectsTest`, since `SeasonLevelsViewModel`
 * shares the same app-wide [LevelLivesService]/[StarWalletService] singletons and the same
 * `HeaderEffects.kt` rules as the endless map (spec 7.4, 9.1; see `1dc9282`'s
 * `SeasonLevelsView.swift` wiring, which this class mirrors on Android).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonLevelsViewModelTest {

    private fun tempFile(name: String) = File.createTempFile(name, ".preferences_pb").also { it.deleteOnExit() }

    private class Harness(val prefs: UserPreferences, val lives: LevelLivesService, val wallet: StarWalletService)

    private fun TestScope.harness(name: String): Harness {
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { tempFile(name) }))
        val day = LocalDate.of(2026, 9, 15)
        return Harness(prefs, LevelLivesService(prefs, DayProvider { day }), StarWalletService(prefs, this))
    }

    private fun TestScope.viewModel(
        h: Harness,
        fired: MutableList<HapticIntent> = mutableListOf(),
    ): Pair<SeasonLevelsViewModel, MutableList<HapticIntent>> {
        val vm = SeasonLevelsViewModel(
            lives = h.lives,
            wallet = h.wallet,
            scope = this,
            onHaptic = { fired.add(it) },
        )
        advanceUntilIdle()
        return vm to fired
    }

    @Test fun `starting a season level with lives remaining fires SELECT and allows the attempt`() = runTest {
        val h = harness("season_gate_ok")
        val (vm, fired) = viewModel(h)

        assertTrue(vm.attemptStart(1))
        assertEquals(listOf(HapticIntent.SELECT), fired)
        assertFalse(vm.uiState.value.showOutOfLivesPrompt)
    }

    @Test fun `starting a season level at zero lives refuses and shows the out-of-lives prompt`() = runTest {
        val h = harness("season_gate_zero")
        repeat(4) { h.lives.spendOnLoss() }
        val (vm, fired) = viewModel(h)

        assertFalse(vm.attemptStart(1))
        assertEquals(listOf(HapticIntent.WARNING), fired)
        assertTrue(vm.uiState.value.showOutOfLivesPrompt)
    }

    @Test fun `buying a life with stars dismisses the prompt and fires REWARD only on a successful spend`() = runTest {
        val h = harness("season_gate_buy")
        repeat(4) { h.lives.spendOnLoss() }
        h.wallet.credit(LevelPowerUp.LIFE_COST)
        val (vm, fired) = viewModel(h)
        vm.attemptStart(1)

        vm.buyLifeWithStars()
        advanceUntilIdle()

        assertEquals(listOf(HapticIntent.WARNING, HapticIntent.REWARD), fired)
        assertFalse(vm.uiState.value.showOutOfLivesPrompt)
        assertEquals(1, vm.uiState.value.livesRemaining)
    }

    @Test fun `an ad-granted life reward fires REWARD and dismisses the prompt`() = runTest {
        val h = harness("season_gate_ad")
        val (vm, fired) = viewModel(h)

        vm.applyLifeRewardFromAd()
        advanceUntilIdle()

        assertEquals(listOf(HapticIntent.REWARD), fired)
        assertFalse(vm.uiState.value.showOutOfLivesPrompt)
    }

    @Test fun `the first load never animates, even when lives are already down`() = runTest {
        val h = harness("season_effects_first_load")
        repeat(2) { h.lives.spendOnLoss() }
        val (vm, _) = viewModel(h)

        assertEquals(2, vm.uiState.value.livesRemaining)
        assertNull(vm.uiState.value.livesEffect)
        assertFalse(vm.uiState.value.starsCredited)
    }

    @Test fun `buying a life bursts the refilled heart and never sparkles the spend`() = runTest {
        val h = harness("season_effects_buy")
        h.wallet.credit(20)
        h.lives.spendOnLoss()
        val (vm, _) = viewModel(h)

        vm.buyLifeWithStars()
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.livesRemaining)
        assertEquals(LivesEffect.Gained(3), vm.uiState.value.livesEffect)
        assertFalse(vm.uiState.value.starsCredited)

        vm.consumeLivesEffect()
        assertNull(vm.uiState.value.livesEffect)
    }

    @Test fun `a refresh with a new credit sparkles once and clears on consume`() = runTest {
        val h = harness("season_effects_credit")
        val (vm, _) = viewModel(h)
        h.wallet.credit(2)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.starsCredited)

        vm.consumeStarsCredited()
        assertFalse(vm.uiState.value.starsCredited)
    }
}
