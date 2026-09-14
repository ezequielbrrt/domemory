package com.ezequielbrrt.domemory.services.multiplayer

import com.ezequielbrrt.domemory.core.model.Board
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiplayerCardsTest {
    @Test fun `double-item cards retain iOS wire order`() {
        val cards = MultiplayerCards.from(Board(id = "animals", name = "Animals", items = listOf("🐶", "🐱")))

        assertEquals(
            listOf(
                MultiplayerCard(0, 0, "🐶"), MultiplayerCard(1, 0, "🐶"),
                MultiplayerCard(2, 1, "🐱"), MultiplayerCard(3, 1, "🐱"),
            ),
            cards,
        )
    }

    @Test fun `adjacency cards retain iOS wire order`() {
        val cards = MultiplayerCards.from(
            Board(id = "pairs", name = "Pairs", items = listOf("one", "uno", "two", "dos"), isDoubleItem = false),
        )

        assertEquals(listOf(0, 0, 2, 2), cards.map(MultiplayerCard::itemId))
    }
}
