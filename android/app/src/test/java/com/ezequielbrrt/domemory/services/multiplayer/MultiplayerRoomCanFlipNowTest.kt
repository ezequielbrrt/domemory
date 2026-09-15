package com.ezequielbrrt.domemory.services.multiplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins [MultiplayerRoom.canFlipNow] — the local gate `MultiplayerViewModel.choose` reads
 * before firing its optimistic `HapticIntent.CARD_FLIP`, mirroring iOS's
 * `MultiplayerRoomViewModel.isInteractionEnabled`. */
class MultiplayerRoomCanFlipNowTest {
    private val host = "a-host"
    private val guest = "z-guest"
    private val isHost: (String) -> Boolean = { it == host }

    private fun room(
        status: MultiplayerRoomStatus = MultiplayerRoomStatus.PLAYING,
        currentPlayerId: String? = host,
        selectedCardIds: List<Int> = emptyList(),
    ) = MultiplayerRoom(
        id = "room", code = "ABC234", status = status,
        createdAt = 1, updatedAt = 1, hostId = host, guestId = guest,
        players = mapOf(host to MultiplayerPlayer(host, "Host", lastSeenAt = 1), guest to MultiplayerPlayer(guest, "Guest", lastSeenAt = 1)),
        gameSource = MultiplayerGameSource.FIREBASE, gameId = "board", gameName = "Board",
        currentPlayerId = currentPlayerId, selectedCardIds = selectedCardIds,
    )

    @Test fun `can flip on my turn while playing with fewer than two cards selected`() {
        assertTrue(room().canFlipNow(isHost))
        assertTrue(room(selectedCardIds = listOf(0)).canFlipNow(isHost))
    }

    @Test fun `cannot flip on the opponent's turn`() {
        assertFalse(room(currentPlayerId = guest).canFlipNow(isHost))
    }

    @Test fun `cannot flip once a pair is already selected`() {
        assertFalse(room(selectedCardIds = listOf(0, 1)).canFlipNow(isHost))
    }

    @Test fun `cannot flip outside PLAYING`() {
        assertFalse(room(status = MultiplayerRoomStatus.WAITING).canFlipNow(isHost))
        assertFalse(room(status = MultiplayerRoomStatus.RECONNECTING).canFlipNow(isHost))
        assertFalse(room(status = MultiplayerRoomStatus.FINISHED).canFlipNow(isHost))
    }

    @Test fun `cannot flip with no current player set`() {
        assertFalse(room(currentPlayerId = null).canFlipNow(isHost))
    }
}
