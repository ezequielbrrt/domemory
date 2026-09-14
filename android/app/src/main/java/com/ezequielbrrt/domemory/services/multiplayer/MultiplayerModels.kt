package com.ezequielbrrt.domemory.services.multiplayer

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Card

/** Wire-compatible room values from spec 10.2 and iOS's MultiplayerModels.swift. */
enum class MultiplayerRoomStatus { WAITING, READY, PLAYING, RECONNECTING, FINISHED, ABANDONED }
enum class MultiplayerGameSource { FIREBASE, CUSTOM }

data class MultiplayerPlayer(
    val id: String,
    val name: String,
    val connected: Boolean = true,
    val lastSeenAt: Long,
    val score: Int = 0,
)

data class MultiplayerCustomGamePayload(
    val title: String,
    val category: String,
    val items: List<String>,
    val itemType: String,
    val isDoubleItem: Boolean,
) {
    fun asBoard(id: String, difficulty: String?) = Board(
        id = id, name = title, category = category, difficulty = difficulty,
        items = items, itemType = itemType, isDoubleItem = isDoubleItem,
    )

    companion object {
        fun from(board: Board) = MultiplayerCustomGamePayload(
            title = board.name, category = board.category, items = board.items,
            itemType = board.itemType, isDoubleItem = board.isDoubleItem,
        )
    }
}

/** A room card intentionally only has the persistent fields of [Card]. */
data class MultiplayerCard(
    val id: Int,
    val itemId: Int,
    val content: String,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false,
) {
    fun asCard() = Card(id, itemId, content, isFaceUp, isMatched)
    companion object { fun from(card: Card) = MultiplayerCard(card.id, card.itemId, card.content, card.isFaceUp, card.isMatched) }
}

/** Stable card serialization shared by both native clients; do not shuffle this list. */
object MultiplayerCards {
    fun from(board: Board): List<MultiplayerCard> = if (board.isDoubleItem) {
        board.items.flatMapIndexed { itemId, content ->
            listOf(
                MultiplayerCard(id = itemId * 2, itemId = itemId, content = content),
                MultiplayerCard(id = itemId * 2 + 1, itemId = itemId, content = content),
            )
        }
    } else {
        board.items.mapIndexed { index, content ->
            MultiplayerCard(id = index, itemId = if (index % 2 == 0) index else index - 1, content = content)
        }
    }
}

data class MultiplayerRoom(
    val id: String,
    val code: String,
    val status: MultiplayerRoomStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val hostId: String,
    val guestId: String? = null,
    val players: Map<String, MultiplayerPlayer>,
    val gameSource: MultiplayerGameSource,
    val gameId: String,
    val gameName: String,
    val difficulty: String? = null,
    val customGamePayload: MultiplayerCustomGamePayload? = null,
    val currentPlayerId: String? = null,
    val cards: List<MultiplayerCard> = emptyList(),
    val selectedCardIds: List<Int> = emptyList(),
    val winnerId: String? = null,
    val disconnectStartedAt: Long? = null,
    val disconnectPlayerId: String? = null,
) {
    val allPairsMatched get() = cards.isNotEmpty() && cards.all { it.isMatched }
    fun isTurnFor(playerId: String) = status == MultiplayerRoomStatus.PLAYING && currentPlayerId == playerId
}
