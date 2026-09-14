package com.ezequielbrrt.domemory.services.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplayerTurnReducerTest {
    private val host = "a-host"
    private val guest = "z-guest"
    private fun room(cards: List<MultiplayerCard> = listOf(
        MultiplayerCard(0, 0, "A"), MultiplayerCard(1, 0, "A"),
        MultiplayerCard(2, 1, "B"), MultiplayerCard(3, 1, "B"),
    )) = MultiplayerRoom(
        id = "room", code = "ABC234", status = MultiplayerRoomStatus.PLAYING,
        createdAt = 1, updatedAt = 1, hostId = host, guestId = guest,
        players = mapOf(host to MultiplayerPlayer(host, "Host", lastSeenAt = 1), guest to MultiplayerPlayer(guest, "Guest", lastSeenAt = 1)),
        gameSource = MultiplayerGameSource.FIREBASE, gameId = "board", gameName = "Board",
        currentPlayerId = host, cards = cards,
    )

    @Test fun `first valid selection turns the card up but keeps the turn`() {
        val result = MultiplayerTurnReducer.choose(room(), host, 0, 2) as MultiplayerTurnReducer.Result.Applied
        assertEquals(listOf(0), result.room.selectedCardIds)
        assertTrue(result.room.cards.first { it.id == 0 }.isFaceUp)
        assertEquals(host, result.room.currentPlayerId)
    }

    @Test fun `a match awards a point and keeps the turn`() {
        val one = (MultiplayerTurnReducer.choose(room(), host, 0, 2) as MultiplayerTurnReducer.Result.Applied).room
        val result = MultiplayerTurnReducer.choose(one, host, 1, 3) as MultiplayerTurnReducer.Result.Applied
        assertTrue(result.matched)
        assertEquals(1, result.room.players.getValue(host).score)
        assertTrue(result.room.cards.filter { it.id in listOf(0, 1) }.all { it.isMatched })
        assertEquals(host, result.room.currentPlayerId)
        assertTrue(result.room.selectedCardIds.isEmpty())
    }

    @Test fun `a mismatch transfers the turn then only the recorded pair can clear`() {
        val one = (MultiplayerTurnReducer.choose(room(), host, 0, 2) as MultiplayerTurnReducer.Result.Applied).room
        val mismatched = (MultiplayerTurnReducer.choose(one, host, 2, 3) as MultiplayerTurnReducer.Result.Applied).room
        assertEquals(guest, mismatched.currentPlayerId)
        assertEquals(listOf(0, 2), mismatched.selectedCardIds)
        assertEquals(mismatched, MultiplayerTurnReducer.clearMismatch(mismatched, listOf(1, 2), 4))
        val cleared = MultiplayerTurnReducer.clearMismatch(mismatched, listOf(2, 0), 4)
        assertTrue(cleared.selectedCardIds.isEmpty())
        assertFalse(cleared.cards.first { it.id == 0 }.isFaceUp)
    }

    @Test fun `last matched pair finishes with the high scorer and equal scores draw`() {
        val cards = listOf(MultiplayerCard(0, 0, "A"), MultiplayerCard(1, 0, "A"))
        val finished = (MultiplayerTurnReducer.choose(
            room(cards).copy(players = room(cards).players.mapValues { (id, p) -> p.copy(score = if (id == host) 2 else 1) }), host, 0, 2,
        ) as MultiplayerTurnReducer.Result.Applied).room
        val result = (MultiplayerTurnReducer.choose(finished, host, 1, 3) as MultiplayerTurnReducer.Result.Applied).room
        assertEquals(MultiplayerRoomStatus.FINISHED, result.status)
        assertEquals(host, result.winnerId)
        assertNull(MultiplayerTurnReducer.winner(result.players.mapValues { it.value.copy(score = 1) }))
    }

    @Test fun `only active player can choose`() {
        assertEquals(MultiplayerTurnReducer.Result.Invalid, MultiplayerTurnReducer.choose(room(), guest, 0, 2))
    }
}
