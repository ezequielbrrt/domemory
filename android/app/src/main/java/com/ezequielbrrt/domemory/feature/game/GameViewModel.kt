package com.ezequielbrrt.domemory.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.ChoiceOutcome
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.model.GameMode
import com.ezequielbrrt.domemory.core.model.MemoryGame
import com.ezequielbrrt.domemory.services.daily.DailyChallengeService
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.levels.LevelCurve
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import com.ezequielbrrt.domemory.services.stats.ProfileStatsRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The whole game loop (spec 3.3). Serves every mode.
 *
 * Timers, and why they are shaped this way:
 *  - **Flip-back, 2.0 s** — cancelled on the next tap, so a mismatched pair stays
 *    tappable and a third tap resolves the board immediately.
 *  - **Matched hide, 1.0 s** — matched cards leave the board, then the win check runs.
 *  - **Countdown** — ticks against a `frozenUntil` deadline rather than being
 *    cancelled, because cancelling would tear down the flip-back scheduling. The
 *    Freeze power-up (spec 7.6) hangs off the same deadline.
 *
 * The win fires exactly once on the transition; re-firing would double-count stats and
 * analytics.
 *
 * **Loss commit is deferred for Levels (spec 7.5, 7.7).** Reaching a `Lost` outcome only
 * shows the lose screen — it does not, by itself, spend the day's life or record the
 * attempt. iOS mirrors this: `logGameFinishedIfNeeded` for a loss is called from the lose
 * modal's own "Try Again" / "Go to menu" handlers, not from wherever `loseReason` first
 * gets set, precisely so a rescue (forgive mistakes, buy a life, skip) taken instead can
 * cost nothing beyond its own star price. [retry] and [acknowledgeLossAndQuit] are the
 * "walk away" paths that actually commit it; [forgiveMistakesWithStars] and
 * [buyLifeWithStars] undo the loss without ever committing it. This deferral applies to
 * Levels only — Free play and the Daily Challenge keep the original immediate-commit
 * behavior, since neither has a lose-screen rescue to protect.
 */
class GameViewModel(
    private val board: Board,
    private val mode: GameMode = GameMode.Free,
    private val playerDifficulty: Difficulty = Difficulty.MEDIUM,
    /**
     * Records per-board `stats.<boardId>.played` / `.won` on every finish (spec 13.2),
     * mirroring iOS's `GameStatsService.recordGameFinished`, which fires unconditionally
     * whenever a `memorama` id exists — free play, Daily Challenge and Levels alike, all
     * of which always carry a [board] here. Null (the default) is a no-op, so every
     * existing plain-Kotlin test that does not care about persistence is unaffected.
     */
    private val stats: GameStatsRecorder? = null,
    /**
     * Records lifetime `profile.*` aggregates (spec 13.2) alongside the per-board [stats]
     * above, from the exact same single-fire commit points ([commit], [commitLossIfNeeded])
     * — mirrors iOS's `ProfileStatsService.recordGameFinished`, itself called from the same
     * `logGameFinishedIfNeeded` guard [stats] is. Null (the default) is a no-op, matching
     * [stats]'s own shape, so existing tests that don't care about this are unaffected.
     */
    private val profileStats: ProfileStatsRecorder? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope? = null,
    /** A longer-lived scope for finish persistence; production passes AppContainer's scope. */
    private val statsScope: CoroutineScope? = null,
    private val levelLives: LevelLivesService? = null,
    /** Levels/Seasons only (spec 7.6, 7.7) — null in every other mode. */
    private val starWallet: StarWalletService? = null,
    /** Records the Daily Challenge finish (spec 8) when [mode] is [GameMode.DailyChallenge]. */
    private val dailyChallenge: DailyChallengeService? = null,
    /**
     * Fired after [dailyChallenge] records a finish (spec 8: "Finishing refreshes the
     * streak-at-risk reminder ... and the home-screen widget"). Deliberately a bare
     * `() -> Unit` rather than a `NotificationService`/widget reference — this class has no
     * Android-framework dependency anywhere else, and `AppContainer.onDailyChallengeFinished`
     * is what wires the two real side effects together in production.
     */
    private val onDailyChallengeFinished: (() -> Unit)? = null,
    /**
     * Fired once, from every finish in every mode (spec 11.2: "Both [inactivity tiers] are
     * rescheduled (cancel + re-add) after every game finish"). Production wires this to
     * `AppContainer.onGameFinished`, which re-arms the day-2/day-7 inactivity reminders from
     * "now" — playing at all is itself a sign of activity, so the countdown to "you haven't
     * played in a while" restarts every time a game ends, win or lose.
     */
    private val onGameFinished: (() -> Unit)? = null,
    /**
     * Fired after every finish that actually commits, with the board's [recordedDifficulty]
     * and the elapsed game duration in milliseconds — the two inputs
     * [com.ezequielbrrt.domemory.services.ads.AdFrequencyCap.afterGameCompletion] needs
     * (mirrors iOS's `presentInterstitialEvery` call from `logGameFinishedIfNeeded`). Never
     * fired for [skipLevelWithStars] (see that function's doc). Production wires this to
     * `AdsService.notifyGameFinished` from the composable layer, which owns the `Activity` a
     * full-screen ad needs to present against — this class stays Android-framework-free like
     * every other callback here.
     */
    private val onCompletionInterstitial: ((Difficulty, Long) -> Unit)? = null,
    /**
     * Fired for every named moment this class decides on its own (card flip, match,
     * mismatch, win, loss, a power-up/rescue purchase succeeding or being refused) — the
     * Android counterpart of iOS's `HapticsService.shared.fire(_:)` call sites inside
     * `MemorizeViewModel`, which fires almost entirely from the view model rather than the
     * view for exactly this reason: the moment a match or a loss is *decided* lives here,
     * not in `GameScreen`. A bare `(HapticIntent) -> Unit)?` rather than a
     * `HapticsService` reference for the same reason as [onCompletionInterstitial] —
     * `HapticIntent` is a plain, Android-framework-free enum (see its own doc), so taking it
     * keeps this class free of any Android import, while the composable layer
     * (`NavGraph.kt`) wires the real `HapticsService.fire` call. A handful of purely
     * navigational taps (quitting free play, the two watch-ad buttons) have no state change
     * to hang this off and fire directly from `NavGraph.kt` instead — see that file.
     */
    private val onHaptic: ((HapticIntent) -> Unit)? = null,
    /**
     * Fired once, only for a genuine win reaching [commit] — never for a loss, and never for
     * [skipLevelWithStars] (which commits a loss, not a win). The Android counterpart of
     * iOS's `AppReviews.recordSuccessfulGameWin()`, which iOS calls from the win modal's
     * "Continue" tap (`WinModalListener.tapOnContinue`). Android's win screen has no
     * separate Continue/Next-Level split yet (a documented gap in `ANDROID_PLAN.md` — the
     * win overlay only offers "Try Again" / "Go to menu"), so this fires from the commit
     * itself rather than a distinct button tap. The object it would call
     * (`services.review.AppReviews`) needs an `Activity`, which this framework-free class
     * deliberately doesn't hold — same bare-callback shape as [onCompletionInterstitial].
     */
    private val onGameWon: (() -> Unit)? = null,
) : ViewModel() {

    private val workScope: CoroutineScope get() = scope ?: viewModelScope
    private val statsWorkScope: CoroutineScope get() = statsScope ?: workScope

    private var game = MemoryGame(board.buildCards())

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var flipBackJob: Job? = null
    private var hideJob: Job? = null
    private var mistakeLossJob: Job? = null
    private var peekJob: Job? = null

    /** Wall-clock deadline the countdown is held to; written by the Freeze power-up. */
    private var frozenUntilMillis: Long = 0L
    private var winReported = false

    /** Guards [commitLossIfNeeded] — a rescue that undoes the loss never sets this. */
    private var lossCommitted = false

    /** Wall-clock start of the current attempt (spec: iOS's `gameStartedAt`) — the elapsed
     * time [onCompletionInterstitial] reports is measured from here, and it is reset on
     * every [restart] so a retried attempt is timed from its own start, not the original. */
    private var gameStartedAtMillis: Long = now()

    init {
        start()
    }

    private fun initialState(): GameUiState {
        val levelContext = (mode as? GameMode.Level)?.context
        val levelNumber = levelContext?.number
        val totalTime = when {
            levelNumber != null -> LevelCurve.seconds(levelNumber).toDouble()
            // Free play and Daily Challenge both take the *player's* setting, not the
            // board's — a daily board declares medium but an easy player gets 110 s.
            else -> playerDifficulty.timeLimitSeconds
        }
        val showsPie = when {
            levelNumber != null -> LevelCurve.showsPie(levelNumber)
            else -> playerDifficulty.showsPie
        }
        val columns = ceil(sqrt(game.cards.size.toDouble())).toInt().coerceAtLeast(1)

        return GameUiState(
            boardName = board.name,
            cards = game.cards,
            columns = columns,
            totalTime = totalTime,
            timeRemaining = totalTime,
            maxFailures = levelNumber?.let { LevelCurve.maxFailures(it) },
            totalPairs = board.pairCount,
            showsPie = showsPie,
            recordedDifficulty = board.resolvedDifficulty(playerDifficulty),
            levelNumber = levelNumber,
            hasNextLevel = levelContext?.let { it.store.nextLevel(it.number) != null } ?: false,
        )
    }

    fun start() {
        tickJob?.cancel()
        tickJob = workScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                val current = _state.value
                if (current.isPaused || current.isFinished) continue
                if (now() < frozenUntilMillis) {
                    if (!current.isFrozen) _state.value = current.copy(isFrozen = true)
                    continue
                }
                val remaining = (current.timeRemaining - TICK_SECONDS).coerceAtLeast(0.0)
                _state.value = current.copy(timeRemaining = remaining, isFrozen = false)
                if (remaining <= 0.0) finish(GameOutcome.Lost(LoseReason.OUT_OF_TIME))
            }
        }
    }

    fun choose(cardId: Int): ChoiceOutcome {
        val current = _state.value
        if (current.isPaused || current.isFinished) return ChoiceOutcome.IGNORED

        // Cancel-on-next-tap: a pending flip-back must not fire against the new board.
        flipBackJob?.cancel()
        flipBackJob = null

        val outcome = game.choose(cardId, now())
        if (outcome == ChoiceOutcome.IGNORED) return outcome

        publishCards()

        when (outcome) {
            ChoiceOutcome.FLIPPED_UP -> onHaptic?.invoke(HapticIntent.CARD_FLIP)
            ChoiceOutcome.MATCH -> {
                // The final pair is followed within the same call stack by checkWin()'s
                // SUCCESS haptic (below) — a MATCH thud in front of it reads as a stutter,
                // so the win is left to speak for itself (mirrors iOS's
                // `fireChooseHaptic`: `if matchedNow < model.cards.count`).
                if (!game.isWon) onHaptic?.invoke(HapticIntent.MATCH)
                scheduleHideMatched()
            }
            ChoiceOutcome.MISMATCH -> {
                onHaptic?.invoke(HapticIntent.MISMATCH)
                scheduleFlipBack()
                checkMistakeBudget()
            }
            else -> Unit
        }
        checkWin()
        return outcome
    }

    private fun scheduleFlipBack() {
        flipBackJob = workScope.launch {
            delay(FLIP_BACK_MILLIS)
            game.flipDownUnmatched(now())
            publishCards()
        }
    }

    private fun scheduleHideMatched() {
        val idsToHide = game.cards.filter { it.isMatched }.map { it.id }.toSet()
        hideJob = workScope.launch {
            delay(MATCHED_HIDE_MILLIS)
            _state.value = _state.value.copy(
                hiddenCardIds = _state.value.hiddenCardIds + idsToHide,
            )
            checkWin()
        }
    }

    /**
     * Arms the deferred mistake-budget loss (spec 7.5). Guarded by [mistakeLossJob] so a
     * player who keeps mismatching after busting the budget can't keep pushing the 0.8 s
     * window back forever — once one is scheduled, further mismatches are no-ops until it
     * either fires or [pause] cancels it. [resume] re-arms it from a fresh 0.8 s window,
     * which is the "re-armed when the timer restarts" rule, not a resumed countdown.
     */
    private fun checkMistakeBudget() {
        val max = _state.value.maxFailures ?: return
        if (_state.value.isFinished || mistakeLossJob != null) return
        if (game.failedTries < max) return
        mistakeLossJob = workScope.launch {
            // Deferred so the player sees the pair that finished them (spec 7.5).
            delay(MISTAKE_LOSS_MILLIS)
            mistakeLossJob = null
            finish(GameOutcome.Lost(LoseReason.TOO_MANY_MISTAKES))
        }
    }

    private fun checkWin() {
        if (winReported || !game.isWon) return
        winReported = true
        finish(GameOutcome.Won)
    }

    /**
     * Whichever failure landed first wins — never overwrite an existing outcome. A win
     * commits immediately (via [commit]); a Level loss only shows the lose screen and is
     * committed later, by [commitLossIfNeeded] (see the class doc). A loss in any other
     * mode (free play, the Daily Challenge — neither has a lose-screen rescue to protect)
     * also commits immediately, through the same [commit].
     */
    private fun finish(outcome: GameOutcome) {
        if (_state.value.outcome != null) return
        tickJob?.cancel()
        flipBackJob?.cancel()
        _state.value = _state.value.copy(outcome = outcome)
        // Fires as soon as the outcome is decided — independent of whether a Level loss's
        // commit is deferred below, matching iOS's own timing (the lose/win screen appears
        // at this same instant).
        onHaptic?.invoke(if (outcome is GameOutcome.Won) HapticIntent.SUCCESS else HapticIntent.FAILURE)
        onGameFinished?.invoke()
        val isDeferredLevelLoss = mode is GameMode.Level && outcome is GameOutcome.Lost
        if (!isDeferredLevelLoss) {
            commit(outcome)
        }
    }

    /**
     * The immediate-commit path: every win, and a loss in a mode with no lose-screen
     * rescue to protect. Never reached for a Level loss — that always goes through
     * [commitLossIfNeeded] instead, which is why there is no life-spend here: only Levels
     * spend lives (spec 7.4), and Level losses never take this path.
     */
    private fun commit(outcome: GameOutcome) {
        (mode as? GameMode.Level)?.context?.let { context ->
            val starsEarned = context.store.recordCompletion(
                level = context.number,
                didWin = outcome is GameOutcome.Won,
                timeRemaining = _state.value.timeRemaining,
                totalTime = _state.value.totalTime,
                failedTries = _state.value.failedTries,
            )
            if (outcome is GameOutcome.Won) {
                _state.value = _state.value.copy(starsEarned = starsEarned)
            }
        }
        // Any finish — win or loss — consumes the day (spec 8); recordCompletion is itself
        // idempotent, but winReported already guards this call to at most once per instance.
        // The Daily Challenge has no lose-screen rescue to protect (see the class doc), so
        // it always takes this immediate-commit path, never commitLossIfNeeded.
        if (mode is GameMode.DailyChallenge) {
            dailyChallenge?.let { daily ->
                statsWorkScope.launch {
                    daily.recordCompletion(outcome is GameOutcome.Won)
                    onDailyChallengeFinished?.invoke()
                }
            }
        }
        if (outcome is GameOutcome.Won) onGameWon?.invoke()
        recordStats(outcome)
        recordProfileStats(outcome)
        notifyCompletionInterstitial()
    }

    /**
     * Commits a standing Level loss exactly once. A no-op when there is nothing standing
     * to commit (free play, a mid-game quit with no outcome yet, or a loss already
     * committed) — safe to call from every "walk away" path without its own guard.
     *
     * `suspend`, and awaits the life spend directly rather than firing it via
     * `statsWorkScope.launch` the way [commit] does for the immediate-commit paths: [retry]
     * calls this and then, in the same coroutine, immediately checks whether the player is
     * now out of lives. Firing the spend fire-and-forget would let that check race its own
     * write and read the count from *before* this loss's life was spent.
     */
    private suspend fun commitLossIfNeeded(allowInterstitial: Boolean = true) {
        if (lossCommitted || mode !is GameMode.Level) return
        val outcome = _state.value.outcome as? GameOutcome.Lost ?: return
        lossCommitted = true
        (mode as? GameMode.Level)?.context?.let { context ->
            context.store.recordCompletion(
                level = context.number,
                didWin = false,
                timeRemaining = _state.value.timeRemaining,
                totalTime = _state.value.totalTime,
                failedTries = _state.value.failedTries,
            )
        }
        levelLives?.spendOnLoss()
        recordStats(outcome)
        recordProfileStats(outcome)
        notifyCompletionInterstitial(allowInterstitial)
    }

    /** [commit] and [commitLossIfNeeded]'s shared last step — see [onCompletionInterstitial]. */
    private fun notifyCompletionInterstitial(allowInterstitial: Boolean = true) {
        if (!allowInterstitial) return
        val callback = onCompletionInterstitial ?: return
        callback(_state.value.recordedDifficulty, now() - gameStartedAtMillis)
    }

    /**
     * Per-board played/won counters (spec 13.2, 13.3). Fires exactly once per finish,
     * guarded by the same single-fire path as [finish] itself. A loss still records
     * "played" without "won" — only [recordBoardWon] is conditioned on the outcome.
     */
    private fun recordStats(outcome: GameOutcome) {
        val recorder = stats ?: return
        statsWorkScope.launch { recorder.recordFinished(board.id, didWin = outcome is GameOutcome.Won) }
    }

    /**
     * Lifetime `profile.*` aggregates (spec 13.2), same single-fire guard as [recordStats].
     * `isPerfect` is a win with zero mistakes — [GameUiState.failedTries] is read here
     * rather than [MemoryGame.failedTries] directly since it's already the value
     * [publishCards] kept in sync with the model on every choice, and is what the win
     * screen itself displays (spec: "the pair that finished them" applies to losses only —
     * a win's failedTries is simply whatever the player accumulated along the way).
     */
    private fun recordProfileStats(outcome: GameOutcome) {
        val recorder = profileStats ?: return
        val didWin = outcome is GameOutcome.Won
        val isPerfect = didWin && _state.value.failedTries == 0
        val difficulty = _state.value.recordedDifficulty
        val timeRemaining = _state.value.timeRemaining.toInt()
        statsWorkScope.launch {
            recorder.recordGameFinished(didWin, isPerfect, difficulty, timeRemaining)
        }
    }

    fun pause() {
        if (_state.value.isFinished) return
        onHaptic?.invoke(HapticIntent.TAP)
        // Pausing during the mistake-loss deferral must not let the player play on past
        // the budget "for free" by never letting the delay elapse — cancelling it here
        // and re-arming a fresh one from `resume()` is the actual spec 7.5 rule.
        mistakeLossJob?.cancel()
        mistakeLossJob = null
        // A peek left running through a pause would leave the board revealed for free
        // once resumed (spec 7.6).
        endPeekIfActive()
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resume() {
        if (_state.value.isFinished) return
        onHaptic?.invoke(HapticIntent.TAP)
        _state.value = _state.value.copy(isPaused = false)
        checkMistakeBudget()
    }

    private fun endPeekIfActive() {
        val job = peekJob ?: return
        peekJob = null
        job.cancel()
        game.flipDownUnmatched(now())
        publishCards()
    }

    private fun publishCards() {
        _state.value = _state.value.copy(
            cards = game.cards,
            failedTries = game.failedTries,
            matchedPairs = game.matchedPairCount,
        )
    }

    /** Cancels every timer. Called on teardown, and directly by tests. */
    fun stop() {
        tickJob?.cancel()
        flipBackJob?.cancel()
        hideJob?.cancel()
        mistakeLossJob?.cancel()
        peekJob?.cancel()
    }

    /**
     * "Try again": a freshly shuffled board and a reset clock, on the *same* view model
     * instance. Phase 1's `Phase1Root` built a brand-new [GameViewModel] per retry, kept
     * alive only by `remember`, with nothing calling [onCleared] on the old one — "try
     * again" left the previous game's countdown ticking forever. Restarting in place
     * instead means the real nav graph's `ViewModelStore` only ever tears this down once,
     * on actually leaving the screen.
     *
     * Does not itself commit a standing loss — see [retry], the lose-screen entry point
     * that does, then calls this.
     */
    fun restart() {
        stop()
        winReported = false
        lossCommitted = false
        frozenUntilMillis = 0L
        gameStartedAtMillis = now()
        game = MemoryGame(board.buildCards())
        _state.value = initialState()
        start()
    }

    /**
     * Lose-screen "Try Again" (spec 7.7): commits a standing Level loss — this is the
     * moment the day's life is actually spent, not the loss itself — then refuses to
     * restart when that leaves the player at 0 lives, the same gate that guards starting
     * a fresh level (spec 7.4). Returns false in that case; the caller is expected to show
     * the out-of-lives prompt instead of navigating into a new attempt. Free play, the
     * Daily Challenge, and a restart with nothing standing to commit (the pause modal's
     * reload) always restart.
     */
    suspend fun retry(): Boolean {
        onHaptic?.invoke(HapticIntent.TAP)
        val hadLevelLoss = mode is GameMode.Level && _state.value.outcome is GameOutcome.Lost
        commitLossIfNeeded()
        if (hadLevelLoss && levelLives?.hasLivesRemaining() == false) return false
        restart()
        return true
    }

    /**
     * Lose-screen "Go to menu" (spec 7.7): commits a standing loss without restarting.
     * Fire-and-forget is fine here — unlike [retry], nothing downstream needs to observe
     * the post-spend life count, and the caller (a synchronous Compose click handler)
     * navigates away immediately regardless.
     */
    fun acknowledgeLossAndQuit() {
        onHaptic?.invoke(HapticIntent.TAP)
        statsWorkScope.launch { commitLossIfNeeded() }
    }

    // --- Power-ups (spec 7.6) — Levels and Seasons only, bought mid-game with no
    // confirmation step. The star spend is awaited *before* the effect is applied (see
    // [spendOnPowerUp]) so a purchase can never grant its effect for free. ------------

    suspend fun buyExtraTime(): Boolean = spendOnPowerUp(LevelPowerUp.EXTRA_TIME) {
        _state.value = _state.value.copy(
            timeRemaining = _state.value.timeRemaining + LevelPowerUp.EXTRA_TIME_SECONDS,
        )
        true
    }

    suspend fun buyPeek(): Boolean = spendOnPowerUp(LevelPowerUp.PEEK) {
        peekJob?.cancel()
        flipBackJob?.cancel()
        flipBackJob = null
        game.faceUpAllUnmatched(now())
        publishCards()
        peekJob = workScope.launch {
            delay((LevelPowerUp.PEEK_DURATION_SECONDS * 1000).toLong())
            peekJob = null
            game.flipDownUnmatched(now())
            publishCards()
        }
        true
    }

    suspend fun buyFreeze(): Boolean = spendOnPowerUp(LevelPowerUp.FREEZE) {
        frozenUntilMillis = now() + (LevelPowerUp.FREEZE_DURATION_SECONDS * 1000).toLong()
        _state.value = _state.value.copy(isFrozen = true)
        true
    }

    suspend fun buyRevealPair(): Boolean = spendOnPowerUp(LevelPowerUp.REVEAL_PAIR) {
        revealHintPair()
    }

    /** Rewarded-ad equivalent of [buyRevealPair], available from the pause sheet in every mode. */
    fun applyHintReward(): Boolean {
        if (_state.value.isFinished) return false
        val revealed = revealHintPair()
        if (revealed) onHaptic?.invoke(HapticIntent.REWARD)
        return revealed
    }

    private fun revealHintPair(): Boolean {
        val pair = game.findUnmatchedPair() ?: return false
        flipBackJob?.cancel()
        game.flipDownUnmatched(now())
        game.faceUp(setOf(pair.first, pair.second), now())
        publishCards()
        // Reveal pair re-arms the normal 2 s flip-back (spec 7.6).
        scheduleFlipBack()
        return true
    }

    /**
     * Shared gate for every power-up: Levels/Seasons only, not paused, not finished —
     * then spends the stars *first*, through [StarWalletService.spend]'s atomic
     * DataStore transaction, and only calls [apply] once that spend actually succeeds.
     *
     * This ordering matters: an earlier version applied the effect optimistically off a
     * cached `canAfford` check and fired the spend afterward without checking its
     * result. Two power-ups (or the same one double-tapped) bought back-to-back, before
     * either's background spend had completed, could both pass the same stale
     * `canAfford` check — the second `spend()` would then correctly fail against the
     * real balance, but its effect had already been granted for free, since nothing
     * looked at that failure. Spending first closes that window: the real, serialized
     * balance decides before any effect exists to roll back. If [apply] itself then
     * reports failure (e.g. reveal pair with no unmatched pair left), the spend is
     * refunded via [StarWalletService.credit] so a failed power-up never costs stars.
     */
    private suspend fun spendOnPowerUp(powerUp: LevelPowerUp, apply: () -> Boolean): Boolean {
        if (mode !is GameMode.Level) return false
        if (_state.value.isPaused || _state.value.isFinished) return false
        val wallet = starWallet ?: return false
        if (!wallet.spend(powerUp.cost)) {
            onHaptic?.invoke(HapticIntent.WARNING)
            return false
        }
        // Re-check after the suspending spend: the game could have finished or been
        // paused while that transaction was in flight.
        if (_state.value.isPaused || _state.value.isFinished || !apply()) {
            wallet.credit(powerUp.cost)
            onHaptic?.invoke(HapticIntent.WARNING)
            return false
        }
        onHaptic?.invoke(HapticIntent.REWARD)
        return true
    }

    // --- Lose-screen star purchases (spec 7.7) ----------------------------------------

    /**
     * 8★: forgives 3 mistakes and resumes the *same* board — matched pairs stay matched.
     * Not a game finish: never commits the loss, so no life is spent and nothing is
     * recorded, matching spec 7.5's rescue exactly.
     */
    suspend fun forgiveMistakesWithStars(): Boolean {
        if (mode !is GameMode.Level) return false
        val outcome = _state.value.outcome as? GameOutcome.Lost ?: return false
        if (outcome.reason != LoseReason.TOO_MANY_MISTAKES) return false
        val wallet = starWallet ?: return false
        if (!wallet.spend(LevelPowerUp.FORGIVE_COST)) {
            onHaptic?.invoke(HapticIntent.WARNING)
            return false
        }
        game.forgiveFailures(LevelPowerUp.FORGIVE_AMOUNT)
        // A floored clock, or the resumed board could restart already expired (spec 7.5).
        val flooredTime = maxOf(_state.value.timeRemaining, LevelPowerUp.FORGIVE_MINIMUM_SECONDS)
        _state.value = _state.value.copy(
            outcome = null,
            timeRemaining = flooredTime,
            failedTries = game.failedTries,
        )
        start()
        onHaptic?.invoke(HapticIntent.REWARD)
        return true
    }

    /**
     * 10★: +1 life (capped at 4) and restarts the level. Never commits the loss itself —
     * mirrors iOS, which does not log a finish for this path either.
     */
    suspend fun buyLifeWithStars(): Boolean {
        if (mode !is GameMode.Level) return false
        if (_state.value.outcome !is GameOutcome.Lost) return false
        val wallet = starWallet ?: return false
        val lives = levelLives ?: return false
        if (!wallet.spend(LevelPowerUp.LIFE_COST)) {
            onHaptic?.invoke(HapticIntent.WARNING)
            return false
        }
        lives.refill(1)
        restart()
        onHaptic?.invoke(HapticIntent.REWARD)
        return true
    }

    /**
     * 15★, confirmation required by the caller. Commits the standing loss (skipping buys
     * the unlock, not a clean record) and unlocks the next level without crediting stars.
     * Returns to the map is the caller's job — this only mutates progress.
     *
     * Passes `allowInterstitial = false` to [commitLossIfNeeded] — charging stars to skip a
     * level and then serving an ad on the way out would be the worst moment in the app to
     * show one (mirrors iOS's `logGameFinishedIfNeeded(result:allowInterstitial: false)`).
     */
    suspend fun skipLevelWithStars(): Boolean {
        val context = (mode as? GameMode.Level)?.context ?: return false
        if (_state.value.outcome !is GameOutcome.Lost) return false
        val wallet = starWallet ?: return false
        if (!wallet.spend(LevelPowerUp.SKIP_LEVEL_COST)) {
            onHaptic?.invoke(HapticIntent.WARNING)
            return false
        }
        // No REWARD here — skipping spends stars to bypass the level, it doesn't grant
        // anything the way the power-ups and rescues above do.
        commitLossIfNeeded(allowInterstitial = false)
        context.store.skipLevel(context.number)
        return true
    }

    // --- Ad-earned lose-screen rescues (spec 7.7 alternates) --------------------------
    // Mirror [forgiveMistakesWithStars] / [buyLifeWithStars] exactly, minus the star spend —
    // the ad view already paid for the rescue by playing to completion. The composable layer
    // calls these from a rewarded ad's earned-reward callback (`AdsService.showRewarded`),
    // never directly from a tap, since presenting the ad itself needs an `Activity` this
    // class deliberately does not have.

    /** Ad-earned equivalent of [forgiveMistakesWithStars]. */
    suspend fun applyForgiveMistakesReward(): Boolean {
        if (mode !is GameMode.Level) return false
        val outcome = _state.value.outcome as? GameOutcome.Lost ?: return false
        if (outcome.reason != LoseReason.TOO_MANY_MISTAKES) return false
        game.forgiveFailures(LevelPowerUp.FORGIVE_AMOUNT)
        val flooredTime = maxOf(_state.value.timeRemaining, LevelPowerUp.FORGIVE_MINIMUM_SECONDS)
        _state.value = _state.value.copy(
            outcome = null,
            timeRemaining = flooredTime,
            failedTries = game.failedTries,
        )
        start()
        onHaptic?.invoke(HapticIntent.REWARD)
        return true
    }

    /** Ad-earned equivalent of [buyLifeWithStars]. */
    suspend fun applyLifeReward(): Boolean {
        if (mode !is GameMode.Level) return false
        if (_state.value.outcome !is GameOutcome.Lost) return false
        val lives = levelLives ?: return false
        lives.refill(1)
        restart()
        onHaptic?.invoke(HapticIntent.REWARD)
        return true
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    companion object {
        const val TICK_MILLIS = 100L
        const val TICK_SECONDS = TICK_MILLIS / 1000.0
        const val FLIP_BACK_MILLIS = 2_000L
        const val MATCHED_HIDE_MILLIS = 1_000L
        const val MISTAKE_LOSS_MILLIS = 800L
    }
}
