package com.ezequielbrrt.domemory.feature.game

import org.junit.Assert.assertEquals
import org.junit.Test

class WinStarSlotTest {

    private fun row(starsEarned: Int, started: Int, completed: Int, reduceMotion: Boolean = false) =
        (0 until 3).map { winStarSlot(it, starsEarned, started, completed, reduceMotion) }

    @Test
    fun `slots beyond the earned count stay dim no matter what`() {
        assertEquals(
            listOf(WinStarSlot.FILLED, WinStarSlot.DIM, WinStarSlot.DIM),
            row(starsEarned = 1, started = 3, completed = 3),
        )
        assertEquals(
            listOf(WinStarSlot.DIM, WinStarSlot.DIM, WinStarSlot.DIM),
            row(starsEarned = 0, started = 3, completed = 3),
        )
    }

    @Test
    fun `an earned slot is dim until it is given the go-ahead`() {
        assertEquals(
            listOf(WinStarSlot.DIM, WinStarSlot.DIM, WinStarSlot.DIM),
            row(starsEarned = 3, started = 0, completed = 0),
        )
    }

    @Test
    fun `a started slot pops while the next one waits its turn`() {
        assertEquals(
            listOf(WinStarSlot.POPPING, WinStarSlot.DIM, WinStarSlot.DIM),
            row(starsEarned = 3, started = 1, completed = 0),
        )
        // The second star has been told to start before the first finished: both are
        // mid-animation at once, which is the staggered overlap iOS deliberately allows.
        assertEquals(
            listOf(WinStarSlot.POPPING, WinStarSlot.POPPING, WinStarSlot.DIM),
            row(starsEarned = 3, started = 2, completed = 0),
        )
    }

    @Test
    fun `a slot whose pop has completed settles to filled`() {
        assertEquals(
            listOf(WinStarSlot.FILLED, WinStarSlot.POPPING, WinStarSlot.DIM),
            row(starsEarned = 3, started = 2, completed = 1),
        )
        assertEquals(
            listOf(WinStarSlot.FILLED, WinStarSlot.FILLED, WinStarSlot.FILLED),
            row(starsEarned = 3, started = 3, completed = 3),
        )
    }

    @Test
    fun `reduce motion jumps every earned slot straight to filled`() {
        assertEquals(
            listOf(WinStarSlot.FILLED, WinStarSlot.FILLED, WinStarSlot.DIM),
            row(starsEarned = 2, started = 0, completed = 0, reduceMotion = true),
        )
    }
}
