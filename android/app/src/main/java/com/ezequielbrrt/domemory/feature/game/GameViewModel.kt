package com.ezequielbrrt.domemory.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.ChoiceOutcome
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.model.GameMode
import com.ezequielbrrt.domemory.core.model.MemoryGame
import com.ezequielbrrt.domemory.services.levels.LevelCurve
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
 *    cancelled, because cancelling would tear down the flip-back scheduling. Phase 3
 *    hangs Freeze off the same deadline.
 *
 * The win fires exactly once on the transition; re-firing would double-count stats and
 * analytics.
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
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope? = null,
    /** A longer-lived scope for finish persistence; production passes AppContainer's scope. */
    private val statsScope: CoroutineScope? = null,
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

    /** Wall-clock deadline the countdown is held to; Phase 3 Freeze writes here. */
    private var frozenUntilMillis: Long = 0L
    private var winReported = false

    init {
        start()
    }

    private fun initialState(): GameUiState {
        val levelNumber = (mode as? GameMode.Level)?.context?.number
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
            ChoiceOutcome.MATCH -> scheduleHideMatched()
            ChoiceOutcome.MISMATCH -> {
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

    private fun checkMistakeBudget() {
        val max = _state.value.maxFailures ?: return
        if (game.failedTries < max) return
        mistakeLossJob?.cancel()
        mistakeLossJob = workScope.launch {
            // Deferred so the player sees the pair that finished them (spec 7.5).
            delay(MISTAKE_LOSS_MILLIS)
            if (_state.value.isPaused) return@launch
            finish(GameOutcome.Lost(LoseReason.TOO_MANY_MISTAKES))
        }
    }

    private fun checkWin() {
        if (winReported || !game.isWon) return
        winReported = true
        finish(GameOutcome.Won)
    }

    /** Whichever failure landed first wins — never overwrite an existing outcome. */
    private fun finish(outcome: GameOutcome) {
        if (_state.value.outcome != null) return
        tickJob?.cancel()
        flipBackJob?.cancel()
        _state.value = _state.value.copy(outcome = outcome)
        (mode as? GameMode.Level)?.context?.let { context ->
            context.store.recordCompletion(
                level = context.number,
                didWin = outcome is GameOutcome.Won,
                timeRemaining = _state.value.timeRemaining,
                totalTime = _state.value.totalTime,
                failedTries = _state.value.failedTries,
            )
        }
        recordStats(outcome)
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

    fun pause() {
        if (_state.value.isFinished) return
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resume() {
        if (_state.value.isFinished) return
        _state.value = _state.value.copy(isPaused = false)
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
    }

    /**
     * "Try again": a freshly shuffled board and a reset clock, on the *same* view model
     * instance. Phase 1's `Phase1Root` built a brand-new [GameViewModel] per retry, kept
     * alive only by `remember`, with nothing calling [onCleared] on the old one — "try
     * again" left the previous game's countdown ticking forever. Restarting in place
     * instead means the real nav graph's `ViewModelStore` only ever tears this down once,
     * on actually leaving the screen.
     */
    fun restart() {
        stop()
        winReported = false
        game = MemoryGame(board.buildCards())
        _state.value = initialState()
        start()
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
