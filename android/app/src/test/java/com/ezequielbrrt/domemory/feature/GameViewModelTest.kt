package com.ezequielbrrt.domemory.feature

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.model.GameMode
import com.ezequielbrrt.domemory.core.model.LevelContext
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.feature.game.GameOutcome
import com.ezequielbrrt.domemory.feature.game.GameStatsRecorder
import com.ezequielbrrt.domemory.feature.game.GameViewModel
import com.ezequielbrrt.domemory.feature.game.LoseReason
import com.ezequielbrrt.domemory.services.levels.LevelCurve
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.services.levels.LevelProgressStore
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val board = Board(
        id = "test",
        name = "test",
        difficulty = Difficulty.MEDIUM.key,
        items = listOf("A", "B", "C"),
    )

    private fun TestScope.viewModel(
        mode: GameMode = GameMode.Free,
        playerDifficulty: Difficulty = Difficulty.MEDIUM,
        stats: GameStatsRecorder? = null,
        levelLives: LevelLivesService? = null,
        starWallet: StarWalletService? = null,
    ) = GameViewModel(
        board = board,
        mode = mode,
        playerDifficulty = playerDifficulty,
        stats = stats,
        now = { testScheduler.currentTime },
        scope = this,
        levelLives = levelLives,
        starWallet = starWallet,
    )

    private fun TestScope.levelLives(day: LocalDate = LocalDate.of(2026, 9, 11)): LevelLivesService {
        val file = File.createTempFile("gvm_lives", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))
        return LevelLivesService(prefs, DayProvider { day })
    }

    private fun TestScope.starWallet(): StarWalletService {
        val file = File.createTempFile("gvm_wallet", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))
        return StarWalletService(prefs, this)
    }

    /**
     * A synchronous in-memory fake — deliberately not a real [androidx.datastore.core.DataStore].
     * A real one's I/O is genuine cross-thread async work that a virtual-time [TestScope]
     * cannot fast-forward through [advanceUntilIdle]; [UserPreferencesGameStatsRecorderTest]
     * covers the real adapter directly instead, by awaiting it in the test's own coroutine.
     */
    private class FakeStatsRecorder : GameStatsRecorder {
        val played = mutableListOf<String>()
        val won = mutableListOf<String>()

        override suspend fun recordFinished(boardId: String, didWin: Boolean) {
            played += boardId
            if (didWin) won += boardId
        }
    }

    /** Records every call instead of actually persisting anything, so tests can assert
     * *when* (or whether) a Level loss gets committed — the crux of the deferred-commit
     * fix (spec 7.5, 7.7). */
    private class RecordingStore : LevelProgressStore {
        data class Completion(
            val level: Int,
            val didWin: Boolean,
            val timeRemaining: Double,
            val totalTime: Double,
            val failedTries: Int,
        )

        val completions = mutableListOf<Completion>()
        val skippedLevels = mutableListOf<Int>()
        override val highestUnlockedLevel = 1
        override fun stars(level: Int) = 0
        override fun isUnlocked(level: Int) = true
        override fun board(level: Int) =
            com.ezequielbrrt.domemory.services.levels.BoardGenerators.endlessLevel(level)

        override fun recordCompletion(
            level: Int,
            didWin: Boolean,
            timeRemaining: Double,
            totalTime: Double,
            failedTries: Int,
        ): Int {
            completions += Completion(level, didWin, timeRemaining, totalTime, failedTries)
            return 0
        }

        override fun skipLevel(level: Int) {
            skippedLevels += level
        }

        override fun nextLevel(after: Int) = after + 1
    }

    @Test
    fun `free play takes the clock from the player setting, not the board`() = runTest {
        // The board declares medium, but an easy player gets 110 seconds and no pie.
        val vm = viewModel(playerDifficulty = Difficulty.EASY)
        assertEquals(110.0, vm.state.value.totalTime, 0.001)
        assertEquals(false, vm.state.value.showsPie)
        // ...while the recorded difficulty still comes from the board.
        assertEquals(Difficulty.MEDIUM, vm.state.value.recordedDifficulty)
        vm.stop()
    }

    @Test
    fun `a level takes its clock and mistake budget from the curve`() = runTest {
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 30, store = FakeStore)))
        assertEquals(LevelCurve.seconds(30).toDouble(), vm.state.value.totalTime, 0.001)
        assertEquals(LevelCurve.maxFailures(30), vm.state.value.maxFailures)
        assertTrue(vm.state.value.showsPie) // level >= 25
        vm.stop()
    }

    @Test
    fun `free play has no mistake budget`() = runTest {
        val vm = viewModel()
        assertNull(vm.state.value.maxFailures)
        vm.stop()
    }

    @Test
    fun `the clock runs down and a timeout loses`() = runTest {
        val vm = viewModel(playerDifficulty = Difficulty.MEDIUM)
        advanceTimeBy(10_000)
        assertEquals(50.0, vm.state.value.timeRemaining, 0.2)

        advanceTimeBy(51_000)
        assertEquals(GameOutcome.Lost(LoseReason.OUT_OF_TIME), vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun `a paused clock does not run down`() = runTest {
        val vm = viewModel()
        vm.pause()
        advanceTimeBy(20_000)
        assertEquals(60.0, vm.state.value.timeRemaining, 0.001)
        vm.resume()
        advanceTimeBy(5_000)
        assertEquals(55.0, vm.state.value.timeRemaining, 0.2)
        vm.stop()
    }

    @Test
    fun `a mismatched pair flips back after two seconds`() = runTest {
        val vm = viewModel()
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        assertEquals(2, vm.state.value.cards.count { it.isFaceUp })

        advanceTimeBy(1_900)
        assertEquals(2, vm.state.value.cards.count { it.isFaceUp })
        advanceTimeBy(200)
        assertEquals(0, vm.state.value.cards.count { it.isFaceUp })
        vm.stop()
    }

    @Test
    fun `tapping during the flip-back window cancels it`() = runTest {
        val vm = viewModel()
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        advanceTimeBy(1_000)

        val third = vm.state.value.cards.first { !it.isFaceUp && !it.isMatched }.id
        vm.choose(third)
        assertEquals(listOf(third), vm.state.value.cards.filter { it.isFaceUp }.map { it.id })

        // The cancelled timer must not fire against the new board and turn `third` down.
        advanceTimeBy(1_500)
        assertEquals(listOf(third), vm.state.value.cards.filter { it.isFaceUp }.map { it.id })
        vm.stop()
    }

    @Test
    fun `matched cards leave the board after the hide delay`() = runTest {
        val vm = viewModel()
        val (a, b) = matchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        assertTrue(vm.state.value.hiddenCardIds.isEmpty())
        advanceTimeBy(1_100)
        assertEquals(setOf(a, b), vm.state.value.hiddenCardIds)
        vm.stop()
    }

    @Test
    fun `clearing the board wins exactly once`() = runTest {
        val vm = viewModel()
        clearBoard(vm)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        assertEquals(3, vm.state.value.matchedPairs)

        // Further ticks and taps must not re-fire or overwrite the win.
        advanceTimeBy(70_000)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun `busting the mistake budget loses after the deferral, not instantly`() = runTest {
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)))
        val max = requireNotNull(vm.state.value.maxFailures) // 4 at level 1

        missPairs(vm, times = max - 1)
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)

        assertEquals(max, vm.state.value.failedTries)
        // The loss is deferred so the player sees the pair that finished them.
        advanceTimeBy(700)
        assertNull(vm.state.value.outcome)
        advanceTimeBy(200)
        assertEquals(
            GameOutcome.Lost(LoseReason.TOO_MANY_MISTAKES),
            vm.state.value.outcome,
        )
        vm.stop()
    }

    @Test
    fun `whichever failure lands first wins`() = runTest {
        // A timeout landing inside the mistake deferral must not be overwritten by the
        // bust — and a timeout must not be offered the mistake rescue.
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)))
        val max = requireNotNull(vm.state.value.maxFailures)

        missPairs(vm, times = max - 1)
        // Run the clock down to half a second, then bust the budget.
        advanceTimeBy(((vm.state.value.timeRemaining - 0.5) * 1000).toLong())
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)

        // Timeout at 500 ms beats the mistake loss deferred to 800 ms.
        advanceTimeBy(1_000)
        assertEquals(GameOutcome.Lost(LoseReason.OUT_OF_TIME), vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun `pausing during the mistake deferral re-arms it on resume instead of dropping it`() = runTest {
        // Spec 7.5: pausing must not let the player play on past the budget "for free" by
        // never letting the deferred loss elapse.
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)))
        val max = requireNotNull(vm.state.value.maxFailures)

        missPairs(vm, times = max - 1)
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        assertEquals(max, vm.state.value.failedTries)

        // Pause well inside the 800 ms deferral window.
        advanceTimeBy(200)
        vm.pause()
        // If pausing merely let the delay keep counting down in the background instead of
        // cancelling it, this would already have fired the loss despite being "paused".
        advanceTimeBy(1_000)
        assertNull(vm.state.value.outcome)

        // Resuming must re-arm a *fresh* 800 ms window, not lose the pending bust entirely.
        vm.resume()
        advanceTimeBy(700)
        assertNull(vm.state.value.outcome)
        advanceTimeBy(200)
        assertEquals(GameOutcome.Lost(LoseReason.TOO_MANY_MISTAKES), vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun `mismatching again after busting the budget cannot push the deferred loss back`() = runTest {
        // Once the deferred loss is armed, further mismatches must not restart its 800 ms
        // window — otherwise a player could mismatch once every 700 ms forever and never
        // actually lose.
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)))
        val max = requireNotNull(vm.state.value.maxFailures)

        missPairs(vm, times = max - 1)
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        assertEquals(max, vm.state.value.failedTries)

        advanceTimeBy(700)
        // One more mismatch, 700 ms into the original 800 ms deferral.
        val (c, d) = mismatchedIds(vm)
        vm.choose(c)
        vm.choose(d)
        advanceTimeBy(200)

        assertEquals(GameOutcome.Lost(LoseReason.TOO_MANY_MISTAKES), vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun `taps after the game is finished are ignored`() = runTest {
        val vm = viewModel()
        clearBoard(vm)
        val before = vm.state.value.failedTries
        vm.choose(vm.state.value.cards.first().id)
        assertEquals(before, vm.state.value.failedTries)
        vm.stop()
    }

    // --- Per-board stats (spec 13.2) ------------------------------------------

    @Test
    fun `a win records both played and won`() = runTest {
        val stats = FakeStatsRecorder()
        val vm = viewModel(stats = stats)
        clearBoard(vm)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        runCurrent()

        assertEquals(listOf(board.id), stats.played)
        assertEquals(listOf(board.id), stats.won)
        vm.stop()
    }

    @Test
    fun `a loss records played but not won`() = runTest {
        val stats = FakeStatsRecorder()
        val vm = viewModel(stats = stats, playerDifficulty = Difficulty.MEDIUM)
        advanceTimeBy(61_000)
        assertEquals(GameOutcome.Lost(LoseReason.OUT_OF_TIME), vm.state.value.outcome)
        runCurrent()

        assertEquals(listOf(board.id), stats.played)
        assertTrue(stats.won.isEmpty())
        vm.stop()
    }

    @Test
    fun `the win guard also stops stats from double-counting`() = runTest {
        val stats = FakeStatsRecorder()
        val vm = viewModel(stats = stats)
        clearBoard(vm)
        runCurrent()
        // Further ticks and taps must not re-fire the win or its stats write.
        advanceTimeBy(70_000)
        vm.choose(vm.state.value.cards.first().id)
        runCurrent()

        assertEquals(listOf(board.id), stats.played)
        assertEquals(listOf(board.id), stats.won)
        vm.stop()
    }

    @Test
    fun `with no stats recorder, a finish is a no-op rather than a crash`() = runTest {
        val vm = viewModel(stats = null)
        clearBoard(vm)
        runCurrent()
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun `stats write survives the game screen scope being cancelled after a finish`() = runTest {
        val screenScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val stats = FakeStatsRecorder()
        val vm = GameViewModel(
            board = board,
            stats = stats,
            now = { testScheduler.currentTime },
            scope = screenScope,
            statsScope = this,
        )

        clearBoard(vm)
        // Mirrors NavController popping the game destination immediately after its end UI.
        screenScope.cancel()
        runCurrent()

        assertEquals(listOf(board.id), stats.played)
        assertEquals(listOf(board.id), stats.won)
    }

    // --- restart (the real nav graph's "try again") ---------------------------

    @Test
    fun `restart resets the clock, the board and the win guard on the same instance`() = runTest {
        val stats = FakeStatsRecorder()
        val vm = viewModel(stats = stats, playerDifficulty = Difficulty.MEDIUM)
        clearBoard(vm)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        runCurrent()
        assertEquals(listOf(board.id), stats.played)

        vm.restart()
        assertEquals(60.0, vm.state.value.timeRemaining, 0.001)
        assertNull(vm.state.value.outcome)
        assertTrue(vm.state.value.cards.none { it.isMatched })

        // A second win after restart must record again, not be swallowed by the old guard.
        clearBoard(vm)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        runCurrent()
        assertEquals(listOf(board.id, board.id), stats.played)
        assertEquals(listOf(board.id, board.id), stats.won)
        vm.stop()
    }

    @Test
    fun `restart cancels the previous game's countdown instead of leaving it ticking`() = runTest {
        val vm = viewModel(playerDifficulty = Difficulty.MEDIUM)
        advanceTimeBy(10_000)
        assertEquals(50.0, vm.state.value.timeRemaining, 0.2)

        vm.restart()
        // If the old tick job survived, this would also apply the pre-restart countdown.
        advanceTimeBy(5_000)
        assertEquals(55.0, vm.state.value.timeRemaining, 0.2)
        vm.stop()
    }

    @Test
    fun `restart cancels a deferred mistake loss from the previous game`() = runTest {
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)))
        val max = requireNotNull(vm.state.value.maxFailures)
        missPairs(vm, times = max - 1)
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        assertEquals(max, vm.state.value.failedTries)

        vm.restart()
        advanceTimeBy(GameViewModel.MISTAKE_LOSS_MILLIS + 100)

        assertNull(vm.state.value.outcome)
        vm.stop()
    }

    // --- Loss recovery: deferred commit (spec 7.5, 7.7) -----------------------

    @Test
    fun `a Level loss is not committed until the player walks away from it`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)
        assertEquals(GameOutcome.Lost(LoseReason.OUT_OF_TIME), vm.state.value.outcome)
        runCurrent()

        // The lose screen is up, but nothing has been recorded and no life spent yet.
        assertTrue(store.completions.isEmpty())
        assertEquals(4, lives.remaining())
        vm.stop()
    }

    @Test
    fun `retry commits the loss, spends a life, and restarts`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)
        assertTrue(vm.state.value.outcome is GameOutcome.Lost)

        val restarted = vm.retry()
        runCurrent()

        assertTrue(restarted)
        assertEquals(1, store.completions.size)
        assertFalse(store.completions.single().didWin)
        assertEquals(3, lives.remaining())
        // A genuine restart: fresh clock, outcome cleared.
        assertNull(vm.state.value.outcome)
        assertEquals(vm.state.value.totalTime, vm.state.value.timeRemaining, 0.001)
        vm.stop()
    }

    @Test
    fun `retry commits the loss but refuses to restart at zero lives`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        repeat(4) { lives.spendOnLoss() } // already at 0 lives before this attempt
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)

        val restarted = vm.retry()
        runCurrent()

        assertFalse(restarted)
        // The loss still committed — retry() decides whether to *restart*, not whether the
        // walk-away itself happened.
        assertEquals(1, store.completions.size)
        // The clock was not reset — the caller is expected to leave the screen instead.
        assertEquals(GameOutcome.Lost(LoseReason.OUT_OF_TIME), vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun `retry commits at most once even if called twice`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)
        vm.retry()
        runCurrent()
        // A second loss on the restarted attempt is a *new* standing loss and should
        // commit again — this is not the same thing as double-committing one loss.
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)
        vm.retry()
        runCurrent()

        assertEquals(2, store.completions.size)
        assertEquals(2, lives.remaining())
        vm.stop()
    }

    @Test
    fun `acknowledgeLossAndQuit commits without restarting`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)

        vm.acknowledgeLossAndQuit()
        runCurrent()

        assertEquals(1, store.completions.size)
        assertEquals(3, lives.remaining())
        // Unlike retry(), the outcome is left standing — the caller is navigating away.
        assertTrue(vm.state.value.outcome is GameOutcome.Lost)
        vm.stop()
    }

    @Test
    fun `quitting mid-game with no standing loss commits nothing`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
        )
        vm.acknowledgeLossAndQuit() // no outcome yet — a plain mid-game quit
        runCurrent()

        assertTrue(store.completions.isEmpty())
        assertEquals(4, lives.remaining())
        vm.stop()
    }

    @Test
    fun `a win still commits immediately, not deferred like a loss`() = runTest {
        val store = RecordingStore()
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = store)))
        clearBoard(vm)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)

        assertEquals(1, store.completions.size)
        assertTrue(store.completions.single().didWin)
        vm.stop()
    }

    // --- Lose-screen star purchases (spec 7.7) ----------------------------------

    @Test
    fun `forgiving mistakes resumes the same board without committing the loss`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.FORGIVE_COST)
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
            starWallet = wallet,
        )
        val max = requireNotNull(vm.state.value.maxFailures)
        missPairs(vm, times = max - 1)
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        advanceTimeBy(GameViewModel.MISTAKE_LOSS_MILLIS + 100)
        assertEquals(GameOutcome.Lost(LoseReason.TOO_MANY_MISTAKES), vm.state.value.outcome)
        val matchedBefore = vm.state.value.matchedPairs

        val forgave = vm.forgiveMistakesWithStars()
        runCurrent()

        assertTrue(forgave)
        assertNull(vm.state.value.outcome)
        assertEquals(max - LevelPowerUp.FORGIVE_AMOUNT, vm.state.value.failedTries)
        // Matched pairs survive the rescue — it is the *same* board, not a fresh one.
        assertEquals(matchedBefore, vm.state.value.matchedPairs)
        // No life spent, nothing recorded — the rescue is not a game finish.
        assertTrue(store.completions.isEmpty())
        assertEquals(4, lives.remaining())
        assertEquals(0, wallet.balance.value)
        vm.stop()
    }

    @Test
    fun `forgiving mistakes floors the clock so the resumed board is playable`() = runTest {
        val store = RecordingStore()
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.FORGIVE_COST)
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            starWallet = wallet,
        )
        val max = requireNotNull(vm.state.value.maxFailures)
        missPairs(vm, times = max - 1)
        // Run the clock down to almost nothing before the final, budget-busting mismatch.
        advanceTimeBy(((vm.state.value.timeRemaining - 1.0) * 1000).toLong())
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        advanceTimeBy(GameViewModel.MISTAKE_LOSS_MILLIS + 100)
        assertEquals(GameOutcome.Lost(LoseReason.TOO_MANY_MISTAKES), vm.state.value.outcome)

        vm.forgiveMistakesWithStars()
        runCurrent()

        assertEquals(LevelPowerUp.FORGIVE_MINIMUM_SECONDS, vm.state.value.timeRemaining, 0.001)
        vm.stop()
    }

    @Test
    fun `forgiving mistakes is refused for a timeout loss`() = runTest {
        val store = RecordingStore()
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.FORGIVE_COST)
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            starWallet = wallet,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)
        assertEquals(GameOutcome.Lost(LoseReason.OUT_OF_TIME), vm.state.value.outcome)

        assertFalse(vm.forgiveMistakesWithStars())
        assertEquals(LevelPowerUp.FORGIVE_COST, wallet.balance.value) // untouched
        vm.stop()
    }

    @Test
    fun `forgiving mistakes without enough stars is refused`() = runTest {
        val store = RecordingStore()
        val wallet = starWallet() // empty
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            starWallet = wallet,
        )
        val max = requireNotNull(vm.state.value.maxFailures)
        missPairs(vm, times = max - 1)
        val (a, b) = mismatchedIds(vm)
        vm.choose(a)
        vm.choose(b)
        advanceTimeBy(GameViewModel.MISTAKE_LOSS_MILLIS + 100)

        assertFalse(vm.forgiveMistakesWithStars())
        assertTrue(vm.state.value.outcome is GameOutcome.Lost)
        vm.stop()
    }

    @Test
    fun `buying a life with stars refills a life and restarts without committing`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.LIFE_COST)
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
            starWallet = wallet,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)
        assertTrue(vm.state.value.outcome is GameOutcome.Lost)

        val bought = vm.buyLifeWithStars()
        runCurrent()

        assertTrue(bought)
        assertNull(vm.state.value.outcome)
        // Mirrors iOS: this path never logs a finish, so nothing is recorded here either.
        assertTrue(store.completions.isEmpty())
        assertEquals(4, lives.remaining())
        assertEquals(0, wallet.balance.value)
        vm.stop()
    }

    @Test
    fun `skipping a level commits the loss and unlocks the next level without stars`() = runTest {
        val store = RecordingStore()
        val lives = levelLives()
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.SKIP_LEVEL_COST)
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = store)),
            levelLives = lives,
            starWallet = wallet,
        )
        advanceTimeBy((vm.state.value.timeRemaining * 1000).toLong() + 200)

        val skipped = vm.skipLevelWithStars()
        runCurrent()

        assertTrue(skipped)
        assertEquals(listOf(1), store.skippedLevels)
        // The attempt still counts as a loss — skipping buys the unlock, not a clean record.
        assertEquals(1, store.completions.size)
        assertFalse(store.completions.single().didWin)
        assertEquals(3, lives.remaining())
        assertEquals(0, wallet.balance.value)
        vm.stop()
    }

    // --- Power-ups (spec 7.6) ----------------------------------------------------

    @Test
    fun `extra time adds fifteen seconds and spends its cost`() = runTest {
        val store = RecordingStore()
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.EXTRA_TIME.cost)
        runCurrent()
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = store)), starWallet = wallet)
        val before = vm.state.value.timeRemaining

        assertTrue(vm.buyExtraTime())
        runCurrent()

        assertEquals(before + LevelPowerUp.EXTRA_TIME_SECONDS, vm.state.value.timeRemaining, 0.001)
        assertEquals(0, wallet.balance.value)
        vm.stop()
    }

    @Test
    fun `a power-up is refused without enough stars and nothing is applied`() = runTest {
        val wallet = starWallet() // empty
        val vm = viewModel(
            mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)),
            starWallet = wallet,
        )
        val before = vm.state.value.timeRemaining

        assertFalse(vm.buyExtraTime())
        assertEquals(before, vm.state.value.timeRemaining, 0.001)
        vm.stop()
    }

    @Test
    fun `power-ups are Levels-only, refused in free play`() = runTest {
        val wallet = starWallet()
        wallet.credit(20)
        runCurrent()
        val vm = viewModel(mode = GameMode.Free, starWallet = wallet)

        assertFalse(vm.buyExtraTime())
        assertEquals(20, wallet.balance.value) // untouched
        vm.stop()
    }

    @Test
    fun `peek reveals every unmatched card then flips back on its own`() = runTest {
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.PEEK.cost)
        runCurrent()
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)), starWallet = wallet)

        assertTrue(vm.buyPeek())
        assertTrue(vm.state.value.cards.all { it.isFaceUp || it.isMatched })

        advanceTimeBy((LevelPowerUp.PEEK_DURATION_SECONDS * 1000).toLong() + 100)
        assertTrue(vm.state.value.cards.none { it.isFaceUp && !it.isMatched })
        vm.stop()
    }

    @Test
    fun `pausing during a peek ends it immediately instead of leaving the board free`() = runTest {
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.PEEK.cost)
        runCurrent()
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)), starWallet = wallet)

        vm.buyPeek()
        assertTrue(vm.state.value.cards.any { it.isFaceUp && !it.isMatched })

        vm.pause()
        assertTrue(vm.state.value.cards.none { it.isFaceUp && !it.isMatched })

        // The peek's own scheduled flip-back must not fire again against a resumed game.
        vm.resume()
        advanceTimeBy((LevelPowerUp.PEEK_DURATION_SECONDS * 1000).toLong() + 100)
        assertTrue(vm.state.value.cards.none { it.isFaceUp && !it.isMatched })
        vm.stop()
    }

    @Test
    fun `freeze holds the countdown and reports isFrozen`() = runTest {
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.FREEZE.cost)
        runCurrent()
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)), starWallet = wallet)
        val before = vm.state.value.timeRemaining

        assertTrue(vm.buyFreeze())
        advanceTimeBy((LevelPowerUp.FREEZE_DURATION_SECONDS * 1000).toLong() - 200)
        assertTrue(vm.state.value.isFrozen)
        assertEquals(before, vm.state.value.timeRemaining, 0.001)

        // Past the freeze window, the clock resumes counting down and isFrozen clears.
        advanceTimeBy(1_000)
        assertFalse(vm.state.value.isFrozen)
        assertTrue(vm.state.value.timeRemaining < before)
        vm.stop()
    }

    @Test
    fun `reveal pair turns up one unmatched pair and re-arms the normal flip-back`() = runTest {
        val wallet = starWallet()
        wallet.credit(LevelPowerUp.REVEAL_PAIR.cost)
        runCurrent()
        val vm = viewModel(mode = GameMode.Level(LevelContext(number = 1, store = FakeStore)), starWallet = wallet)

        assertTrue(vm.buyRevealPair())
        val faceUp = vm.state.value.cards.filter { it.isFaceUp && !it.isMatched }
        assertEquals(2, faceUp.size)
        assertEquals(faceUp[0].itemId, faceUp[1].itemId)

        advanceTimeBy(GameViewModel.FLIP_BACK_MILLIS + 100)
        assertTrue(vm.state.value.cards.none { it.isFaceUp && !it.isMatched })
        vm.stop()
    }

    // --- helpers -------------------------------------------------------------

    /** Two face-down cards that do not match. */
    private fun mismatchedIds(vm: GameViewModel): Pair<Int, Int> {
        val open = vm.state.value.cards.filter { !it.isMatched && !it.isFaceUp }
        val first = open.first()
        val second = open.first { it.itemId != first.itemId }
        return first.id to second.id
    }

    /** Misses [times] pairs, letting the flip-back clear the board between each. */
    private fun kotlinx.coroutines.test.TestScope.missPairs(vm: GameViewModel, times: Int) {
        repeat(times) {
            val (a, b) = mismatchedIds(vm)
            vm.choose(a)
            vm.choose(b)
            advanceTimeBy(2_100)
        }
    }

    private fun matchedIds(vm: GameViewModel): Pair<Int, Int> {
        val open = vm.state.value.cards.filter { !it.isMatched }
        val first = open.first()
        val second = open.first { it.itemId == first.itemId && it.id != first.id }
        return first.id to second.id
    }

    private fun clearBoard(vm: GameViewModel) {
        repeat(3) {
            val (a, b) = matchedIds(vm)
            vm.choose(a)
            vm.choose(b)
        }
    }

    private object FakeStore : LevelProgressStore {
        override val highestUnlockedLevel = 1
        override fun stars(level: Int) = 0
        override fun isUnlocked(level: Int) = true
        override fun board(level: Int) =
            com.ezequielbrrt.domemory.services.levels.BoardGenerators.endlessLevel(level)
        override fun recordCompletion(
            level: Int,
            didWin: Boolean,
            timeRemaining: Double,
            totalTime: Double,
            failedTries: Int,
        ) = 0
        override fun skipLevel(level: Int) = Unit
        override fun nextLevel(after: Int) = after + 1
    }
}
