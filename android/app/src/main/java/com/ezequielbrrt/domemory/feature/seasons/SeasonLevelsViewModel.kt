package com.ezequielbrrt.domemory.feature.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.feature.levels.LivesEffect
import com.ezequielbrrt.domemory.feature.levels.livesEffect
import com.ezequielbrrt.domemory.feature.levels.starsCredited
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The season-map counterpart of [com.ezequielbrrt.domemory.feature.levels.LevelsViewModel]
 * (spec 7.4, mirrored for Seasons at spec 9.1's "one daily budget and one wallet across
 * endless Levels and every season"). iOS's `SeasonLevelsView.swift` gates a tile tap on
 * `hasLivesRemaining` exactly the way `LevelsView.swift` does and shows the identical
 * `OutOfLivesModal` on a refusal — this class exists so Android's season map can do the
 * same instead of starting every tile tap unconditionally (the gap `ANDROID_PLAN.md`'s
 * 2026-09-15 haptics entry names as "a separate, still-open item, §8 item 4").
 *
 * Also carries the same header Lottie-effect state
 * [com.ezequielbrrt.domemory.feature.levels.LevelsViewModel] does (`1dc9282`'s heart-break/
 * refill and star-sparkle work) — iOS's `SeasonLevelsView.swift` wires the identical
 * `livesEffect`/`starsCredited` pair on its own lives/star pills, so the season map would
 * otherwise be the one place in the app where a life refill or star credit lands silently.
 *
 * [lives] and [wallet] are the same app-wide singletons
 * [com.ezequielbrrt.domemory.feature.levels.LevelsViewModel] uses — there is no
 * season-specific lives or star concept, only a season-specific *progress* one (tracked
 * separately by [SeasonLevelProgressStore]/[SeasonProgressService]).
 */
class SeasonLevelsViewModel(
    private val lives: LevelLivesService,
    private val wallet: StarWalletService,
    private val scope: CoroutineScope? = null,
    private val onHaptic: ((HapticIntent) -> Unit)? = null,
    /** Same bare-callback shape as [onHaptic], for the same reason — see
     * [com.ezequielbrrt.domemory.feature.game.GameViewModel]'s `onAnalytics` doc. */
    private val onAnalytics: ((AnalyticsEvent) -> Unit)? = null,
) : ViewModel() {

    data class UiState(
        val livesRemaining: Int = LevelLivesService.MAX_LIVES,
        val starBalance: Int = 0,
        val showOutOfLivesPrompt: Boolean = false,
        /** A one-shot heart animation the header owes the player; see `HeaderEffects.kt`. */
        val livesEffect: LivesEffect? = null,
        /** Sparkle over the star chip for a credit that just landed; never for a spend. */
        val starsCredited: Boolean = false,
    )

    /** Whether [refresh] has populated the counters at least once — see
     * [com.ezequielbrrt.domemory.feature.levels.LevelsViewModel]'s identical guard. */
    private var hasLoadedCounters = false

    /** Clears a played heart effect so it does not replay on the next recomposition. */
    fun consumeLivesEffect() {
        _uiState.update { it.copy(livesEffect = null) }
    }

    fun consumeStarsCredited() {
        _uiState.update { it.copy(starsCredited = false) }
    }

    /** Applies fresh counters, attaching whatever animation the change deserves. */
    private fun UiState.withCounters(livesRemaining: Int, starBalance: Int = this.starBalance): UiState =
        if (!hasLoadedCounters) {
            copy(livesRemaining = livesRemaining, starBalance = starBalance)
        } else {
            copy(
                livesRemaining = livesRemaining,
                starBalance = starBalance,
                livesEffect = livesEffect(this.livesRemaining, livesRemaining) ?: livesEffect,
                starsCredited = starsCredited || starsCredited(this.starBalance, starBalance),
            )
        }

    private val workScope: CoroutineScope get() = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Call after returning from a game so newly-spent lives/stars show up the moment the
     * player is back on the season map (mirrors iOS's `SeasonLevelsView.onDisappear`). */
    fun refresh() {
        workScope.launch {
            val starBalance = wallet.refresh()
            val livesRemaining = lives.remaining()
            _uiState.update { it.withCounters(livesRemaining = livesRemaining, starBalance = starBalance) }
            hasLoadedCounters = true
        }
    }

    /**
     * The spec 7.4 gate, applied to season play too: a player with 0 daily lives may not
     * start a season-level attempt. Returns true when the caller should navigate into the
     * level; on a refusal it surfaces the out-of-lives prompt instead and returns false —
     * mirrors iOS's `SeasonLevelsView.onSelect` exactly.
     */
    suspend fun attemptStart(level: Int): Boolean {
        val remaining = lives.remaining()
        _uiState.update { it.copy(livesRemaining = remaining) }
        if (remaining <= 0) {
            onHaptic?.invoke(HapticIntent.WARNING)
            onAnalytics?.invoke(AnalyticsEvent.LevelOutOfLivesShown(source = "season_tile"))
            _uiState.update { it.copy(showOutOfLivesPrompt = true) }
            return false
        }
        onHaptic?.invoke(HapticIntent.SELECT)
        return true
    }

    fun dismissOutOfLivesPrompt() {
        _uiState.update { it.copy(showOutOfLivesPrompt = false) }
    }

    /** Not gated on the Remove-Ads entitlement — the daily budget applies to purchasers
     * too (spec 7.4), season play included. */
    fun buyLifeWithStars() {
        workScope.launch {
            if (wallet.spend(LevelPowerUp.LIFE_COST)) {
                // Mirrors iOS's own comment on the equivalent button: "emits .reward on the
                // spend; a .tap here would double-buzz."
                onHaptic?.invoke(HapticIntent.REWARD)
                lives.refill(1)
                val livesRemaining = lives.remaining()
                val starBalance = wallet.balance.value
                onAnalytics?.invoke(
                    AnalyticsEvent.LevelLifePurchasedWithStars(cost = LevelPowerUp.LIFE_COST, balanceAfter = starBalance),
                )
                _uiState.update {
                    it.withCounters(livesRemaining = livesRemaining, starBalance = starBalance)
                        .copy(showOutOfLivesPrompt = false)
                }
            }
        }
    }

    /** Ad-earned equivalent of [buyLifeWithStars] — the composable layer calls this from a
     * rewarded ad's earned-reward callback, never directly from a tap. */
    fun applyLifeRewardFromAd() {
        workScope.launch {
            onHaptic?.invoke(HapticIntent.REWARD)
            lives.refill(1)
            val livesRemaining = lives.remaining()
            onAnalytics?.invoke(AnalyticsEvent.LevelLifeGrantedFromAd(livesRemaining = livesRemaining))
            _uiState.update {
                it.withCounters(livesRemaining = livesRemaining).copy(showOutOfLivesPrompt = false)
            }
        }
    }
}
