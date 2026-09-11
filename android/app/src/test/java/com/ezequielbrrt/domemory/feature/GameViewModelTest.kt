package com.ezequielbrrt.domemory.feature

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.model.GameMode
import com.ezequielbrrt.domemory.core.model.LevelContext
import com.ezequielbrrt.domemory.feature.game.GameOutcome
import com.ezequielbrrt.domemory.feature.game.GameStatsRecorder
import com.ezequielbrrt.domemory.feature.game.GameViewModel
import com.ezequielbrrt.domemory.feature.game.LoseReason
import com.ezequielbrrt.domemory.services.levels.LevelCurve
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    ) = GameViewModel(
        board = board,
        mode = mode,
        playerDifficulty = playerDifficulty,
        stats = stats,
        now = { testScheduler.currentTime },
        scope = this,
    )

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        assertEquals(listOf(board.id), stats.played)
        assertTrue(stats.won.isEmpty())
        vm.stop()
    }

    @Test
    fun `the win guard also stops stats from double-counting`() = runTest {
        val stats = FakeStatsRecorder()
        val vm = viewModel(stats = stats)
        clearBoard(vm)
        advanceUntilIdle()
        // Further ticks and taps must not re-fire the win or its stats write.
        advanceTimeBy(70_000)
        vm.choose(vm.state.value.cards.first().id)
        advanceUntilIdle()

        assertEquals(listOf(board.id), stats.played)
        assertEquals(listOf(board.id), stats.won)
        vm.stop()
    }

    @Test
    fun `with no stats recorder, a finish is a no-op rather than a crash`() = runTest {
        val vm = viewModel(stats = null)
        clearBoard(vm)
        advanceUntilIdle()
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        vm.stop()
    }

    // --- restart (the real nav graph's "try again") ---------------------------

    @Test
    fun `restart resets the clock, the board and the win guard on the same instance`() = runTest {
        val stats = FakeStatsRecorder()
        val vm = viewModel(stats = stats, playerDifficulty = Difficulty.MEDIUM)
        clearBoard(vm)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        advanceUntilIdle()
        assertEquals(listOf(board.id), stats.played)

        vm.restart()
        assertEquals(60.0, vm.state.value.timeRemaining, 0.001)
        assertNull(vm.state.value.outcome)
        assertTrue(vm.state.value.cards.none { it.isMatched })

        // A second win after restart must record again, not be swallowed by the old guard.
        clearBoard(vm)
        assertEquals(GameOutcome.Won, vm.state.value.outcome)
        advanceUntilIdle()
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

    private object FakeStore : com.ezequielbrrt.domemory.services.levels.LevelProgressStore {
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
