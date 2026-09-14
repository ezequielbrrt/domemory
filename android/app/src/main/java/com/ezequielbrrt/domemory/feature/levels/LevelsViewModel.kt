package com.ezequielbrrt.domemory.feature.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.services.levels.LevelProgressService
import com.ezequielbrrt.domemory.services.levels.LevelsIntroGate
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Thin wrapper over [LevelProgressService] / [LevelLivesService] / [StarWalletService]
 * for `LevelsScreen` — mirrors iOS's `LevelsViewModel`. The one rule this exists to make
 * testable without Compose: [attemptStart] is the spec 7.4 gate that must refuse to
 * start a level attempt at 0 daily lives, surfacing the out-of-lives prompt instead.
 */
class LevelsViewModel(
    private val progress: LevelProgressService,
    private val lives: LevelLivesService,
    private val wallet: StarWalletService,
    private val introGate: LevelsIntroGate,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class UiState(
        val livesRemaining: Int = LevelLivesService.MAX_LIVES,
        val starBalance: Int = 0,
        val showOutOfLivesPrompt: Boolean = false,
        val showIntro: Boolean = false,
    )

    private val workScope: CoroutineScope get() = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Live progress revision, so the tile grid recomposes as stars/unlocks arrive. */
    val progressRevision get() = progress.revision

    val highestUnlockedLevel: Int get() = progress.highestUnlockedLevel
    fun stars(level: Int): Int = progress.stars(level)
    fun isUnlocked(level: Int): Boolean = progress.isUnlocked(level)

    init {
        refresh()
        presentIntroIfNeeded()
    }

    /** Call after returning from a game so newly-earned stars/unlocks and the lives
     * count show up (mirrors iOS's `LevelsView.onDisappear { viewModel.refresh() }`). */
    fun refresh() {
        workScope.launch {
            // wallet.refresh(), not wallet.balance.value: a level win credits the wallet
            // directly through UserPreferences (LevelProgressService.recordCompletion),
            // so this cache can be stale until something explicitly re-pulls it.
            val starBalance = wallet.refresh()
            _uiState.update { it.copy(livesRemaining = lives.remaining(), starBalance = starBalance) }
        }
    }

    private fun presentIntroIfNeeded() {
        workScope.launch {
            if (introGate.shouldPresent()) _uiState.update { it.copy(showIntro = true) }
        }
    }

    /** Reopens the one-shot intro from the map header's info button. */
    fun presentIntro() {
        _uiState.update { it.copy(showIntro = true) }
    }

    /** Persisted on dismissal, not on presentation — a kill mid-intro leaves the player
     * eligible to see it again (spec 7.9). */
    fun dismissIntro() {
        workScope.launch {
            introGate.markSeen()
            _uiState.update { it.copy(showIntro = false) }
        }
    }

    /**
     * The spec 7.4 gate: a player with 0 daily lives may not start a level attempt.
     * Returns true when the caller should navigate into the level; on a refusal it
     * surfaces the out-of-lives prompt instead and returns false.
     */
    suspend fun attemptStart(level: Int): Boolean {
        val remaining = lives.remaining()
        _uiState.update { it.copy(livesRemaining = remaining) }
        if (remaining <= 0) {
            _uiState.update { it.copy(showOutOfLivesPrompt = true) }
            return false
        }
        return true
    }

    fun dismissOutOfLivesPrompt() {
        _uiState.update { it.copy(showOutOfLivesPrompt = false) }
    }

    /** Not gated on the Remove-Ads entitlement — the daily budget applies to purchasers
     * too (spec 7.4). */
    fun buyLifeWithStars() {
        workScope.launch {
            if (wallet.spend(LevelPowerUp.LIFE_COST)) {
                lives.refill(1)
                _uiState.update {
                    it.copy(
                        livesRemaining = lives.remaining(),
                        starBalance = wallet.balance.value,
                        showOutOfLivesPrompt = false,
                    )
                }
            }
        }
    }

    /**
     * Ad-earned equivalent of [buyLifeWithStars] — no star spend, the ad already paid for
     * it. The composable layer calls this from a rewarded ad's earned-reward callback
     * (`AdsService.showRewarded`), never directly from a tap.
     */
    fun applyLifeRewardFromAd() {
        workScope.launch {
            lives.refill(1)
            _uiState.update {
                it.copy(livesRemaining = lives.remaining(), showOutOfLivesPrompt = false)
            }
        }
    }
}
