package com.ezequielbrrt.domemory.services.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoomTransactionPlanTest {
    private val room = MultiplayerRoom(
        id = "room", code = "ABC234", status = MultiplayerRoomStatus.WAITING,
        createdAt = 1, updatedAt = 1, hostId = "host", guestId = null,
        players = mapOf("host" to MultiplayerPlayer("host", "Host", lastSeenAt = 1)),
        gameSource = MultiplayerGameSource.FIREBASE, gameId = "", gameName = "",
    )

    @Test
    fun `an empty local cache waits for the server instead of aborting`() {
        var transformCalled = false
        val plan = RoomTransactionPlan.of(null) { transformCalled = true; it }
        assertEquals(RoomTransactionPlan.AwaitServer, plan)
        assertFalse("nothing to transform before the server's data arrives", transformCalled)
    }

    @Test
    fun `unreadable room data aborts`() {
        assertEquals(RoomTransactionPlan.Abort, RoomTransactionPlan.of("not a room") { it })
        assertEquals(RoomTransactionPlan.Abort, RoomTransactionPlan.of(mapOf("id" to "room")) { it })
    }

    @Test
    fun `a transform that rejects the current state aborts`() {
        val plan = RoomTransactionPlan.of(MultiplayerRoomCodec.encode(room)) { null }
        assertEquals(RoomTransactionPlan.Abort, plan)
    }

    @Test
    fun `an accepted transform commits its result`() {
        val plan = RoomTransactionPlan.of(MultiplayerRoomCodec.encode(room)) { it.copy(guestId = "guest") }
        assertEquals(RoomTransactionPlan.Commit(room.copy(guestId = "guest")), plan)
    }
}
