package com.ezequielbrrt.domemory.services.multiplayer

/** What a room transaction's first pass should tell Firebase, decided from the data it was handed. */
sealed interface RoomTransactionPlan {
    /**
     * The local cache holds nothing for this room, so there is nothing to transform yet.
     * Committing the unchanged (null) data makes Firebase compare it against the server, see
     * the mismatch and re-run the handler with the room's real contents. Aborting here
     * instead would fail the very first join of a room this client has never observed.
     */
    data object AwaitServer : RoomTransactionPlan

    /** The data is unreadable, or the transform rejected the room's current state. */
    data object Abort : RoomTransactionPlan

    data class Commit(val room: MultiplayerRoom) : RoomTransactionPlan

    companion object {
        fun of(raw: Any?, transform: (MultiplayerRoom) -> MultiplayerRoom?): RoomTransactionPlan {
            if (raw == null) return AwaitServer
            val current = MultiplayerRoomCodec.decode(raw) ?: return Abort
            return transform(current)?.let(::Commit) ?: Abort
        }
    }
}
