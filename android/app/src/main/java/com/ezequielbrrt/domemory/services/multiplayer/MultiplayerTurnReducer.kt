package com.ezequielbrrt.domemory.services.multiplayer

/**
 * Pure counterpart to the mutable Firebase transaction (spec 10.4). Keeping every move
 * decision here lets Android and iOS room snapshots be validated without a network or SDK.
 */
object MultiplayerTurnReducer {
    sealed interface Result {
        data class Applied(val room: MultiplayerRoom, val matched: Boolean) : Result
        data object Invalid : Result
    }

    fun choose(room: MultiplayerRoom, playerId: String, cardId: Int, nowSeconds: Long): Result {
        if (!room.isTurnFor(playerId) || room.selectedCardIds.size >= 2) return Result.Invalid
        val index = room.cards.indexOfFirst { it.id == cardId }
        if (index < 0 || room.cards[index].isMatched || room.cards[index].isFaceUp) return Result.Invalid

        var cards = room.cards.toMutableList()
        cards[index] = cards[index].copy(isFaceUp = true)
        val selected = room.selectedCardIds + cardId
        if (selected.size == 1) return Result.Applied(room.copy(cards = cards, selectedCardIds = selected, updatedAt = nowSeconds), false)

        val pair = cards.filter { it.id in selected }
        if (pair.size != 2) return Result.Invalid
        if (pair[0].itemId != pair[1].itemId) {
            return Result.Applied(
                room.copy(cards = cards, selectedCardIds = selected, currentPlayerId = nextPlayer(room, playerId), updatedAt = nowSeconds),
                false,
            )
        }
        cards = cards.map { if (it.id in selected) it.copy(isMatched = true, isFaceUp = true) else it }.toMutableList()
        val players = room.players.toMutableMap()
        players[playerId] = players.getValue(playerId).copy(score = players.getValue(playerId).score + 1)
        val won = cards.all { it.isMatched }
        val updated = room.copy(
            cards = cards, selectedCardIds = emptyList(), players = players,
            status = if (won) MultiplayerRoomStatus.FINISHED else room.status,
            winnerId = if (won) winner(players) else room.winnerId,
            updatedAt = nowSeconds,
        )
        return Result.Applied(updated, true)
    }

    fun clearMismatch(room: MultiplayerRoom, selectedIds: List<Int>, nowSeconds: Long): MultiplayerRoom {
        if (room.status != MultiplayerRoomStatus.PLAYING || room.selectedCardIds.sorted() != selectedIds.sorted()) return room
        return room.copy(
            cards = room.cards.map { if (it.id in selectedIds && !it.isMatched) it.copy(isFaceUp = false) else it },
            selectedCardIds = emptyList(), updatedAt = nowSeconds,
        )
    }

    fun winner(players: Map<String, MultiplayerPlayer>): String? {
        val sorted = players.values.sortedWith(compareByDescending<MultiplayerPlayer> { it.score }.thenBy { it.id })
        val first = sorted.firstOrNull() ?: return null
        return first.id.takeIf { sorted.drop(1).all { it.score < first.score } }
    }

    private fun nextPlayer(room: MultiplayerRoom, current: String) = room.players.keys.firstOrNull { it != current }
}
