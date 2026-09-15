package com.ezequielbrrt.domemory.services.multiplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure guard, no ViewModel/coroutine/DataStore involved — see [MultiplayerWinGuard]'s own
 * doc for why it mirrors iOS's `hasFiredFinishHaptic` reset shape rather than its
 * (never-reset) `hasRecordedMultiplayerWin` latch. */
class MultiplayerWinGuardTest {

    private val isMe: (String) -> Boolean = { it == "me" }

    @Test
    fun `records exactly once on FINISHED with this client as winner`() {
        val guard = MultiplayerWinGuard()
        assertTrue(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe))
        assertFalse(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe))
    }

    @Test
    fun `a duplicate FINISHED update never double-records`() {
        val guard = MultiplayerWinGuard()
        guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe)
        repeat(5) { assertFalse(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe)) }
    }

    @Test
    fun `never records for the opponent's win`() {
        val guard = MultiplayerWinGuard()
        assertFalse(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "opponent", isMe))
        // And staying FINISHED with the same opponent-win snapshot never flips to true later.
        assertFalse(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "opponent", isMe))
    }

    @Test
    fun `never records a draw (null winner)`() {
        val guard = MultiplayerWinGuard()
        assertFalse(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, null, isMe))
    }

    @Test
    fun `a rematch's transition back to PLAYING resets the latch so a later win can record again`() {
        val guard = MultiplayerWinGuard()
        assertTrue(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe))
        assertFalse(guard.shouldRecordWin(MultiplayerRoomStatus.PLAYING, null, isMe))
        assertTrue(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe))
    }

    @Test
    fun `every non-FINISHED status resets the latch, not just PLAYING`() {
        val guard = MultiplayerWinGuard()
        guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe)
        guard.shouldRecordWin(MultiplayerRoomStatus.WAITING, null, isMe)
        assertTrue(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe))
    }

    @Test
    fun `a later winner-bearing FINISHED update after a draw snapshot can still record`() {
        val guard = MultiplayerWinGuard()
        assertFalse(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, null, isMe))
        assertTrue(guard.shouldRecordWin(MultiplayerRoomStatus.FINISHED, "me", isMe))
    }
}
