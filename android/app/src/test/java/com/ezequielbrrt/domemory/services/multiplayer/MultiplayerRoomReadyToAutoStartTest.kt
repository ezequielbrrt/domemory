package com.ezequielbrrt.domemory.services.multiplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [MultiplayerRoom.hasSelectedGame] and [MultiplayerRoom.readyToAutoStart] — spec 10.2's
 * `gameId == ""` sentinel and spec 10.4 step 5's pure precondition for the reactive,
 * host-only match start (`MultiplayerService.start`'s transaction guard and
 * `MultiplayerViewModel`'s auto-start trigger both read the same property, rather than each
 * re-deriving the condition).
 */
class MultiplayerRoomReadyToAutoStartTest {
    private val host = "a-host"
    private val guest = "z-guest"

    private fun room(
        status: MultiplayerRoomStatus = MultiplayerRoomStatus.READY,
        gameId: String = "board",
        players: Map<String, MultiplayerPlayer> = mapOf(
            host to MultiplayerPlayer(host, "Host", lastSeenAt = 1, isReady = true),
            guest to MultiplayerPlayer(guest, "Guest", lastSeenAt = 1, isReady = true),
        ),
    ) = MultiplayerRoom(
        id = "room", code = "ABC234", status = status,
        createdAt = 1, updatedAt = 1, hostId = host, guestId = guest,
        players = players,
        gameSource = MultiplayerGameSource.FIREBASE, gameId = gameId, gameName = if (gameId.isEmpty()) "" else "Board",
    )

    @Test fun `hasSelectedGame is false only for the empty-string wire sentinel`() {
        assertFalse(room(gameId = "").hasSelectedGame)
        assertTrue(room(gameId = "board").hasSelectedGame)
    }

    @Test fun `ready to auto-start once READY, a game is picked, two players, both ready`() {
        assertTrue(room().readyToAutoStart)
    }

    @Test fun `not ready outside READY`() {
        assertFalse(room(status = MultiplayerRoomStatus.WAITING).readyToAutoStart)
        assertFalse(room(status = MultiplayerRoomStatus.PLAYING).readyToAutoStart)
        assertFalse(room(status = MultiplayerRoomStatus.FINISHED).readyToAutoStart)
    }

    @Test fun `not ready with no game picked`() {
        assertFalse(room(gameId = "").readyToAutoStart)
    }

    @Test fun `not ready with only one player present`() {
        assertFalse(room(players = mapOf(host to MultiplayerPlayer(host, "Host", lastSeenAt = 1, isReady = true))).readyToAutoStart)
    }

    @Test fun `not ready while either player hasn't marked ready`() {
        assertFalse(
            room(
                players = mapOf(
                    host to MultiplayerPlayer(host, "Host", lastSeenAt = 1, isReady = true),
                    guest to MultiplayerPlayer(guest, "Guest", lastSeenAt = 1, isReady = false),
                ),
            ).readyToAutoStart,
        )
        assertFalse(
            room(
                players = mapOf(
                    host to MultiplayerPlayer(host, "Host", lastSeenAt = 1, isReady = false),
                    guest to MultiplayerPlayer(guest, "Guest", lastSeenAt = 1, isReady = false),
                ),
            ).readyToAutoStart,
        )
    }
}
