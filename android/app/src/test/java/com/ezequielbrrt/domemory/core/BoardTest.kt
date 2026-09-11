package com.ezequielbrrt.domemory.core

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardTest {

    @Test
    fun `double-item board yields two cards per item sharing an itemId`() {
        val board = Board(id = "1", name = "b", items = listOf("A", "B", "C"))
        val cards = board.buildCards(Random(1))

        assertEquals(6, cards.size)
        assertEquals(3, board.pairCount)
        assertEquals(3, cards.distinctBy { it.itemId }.size)
        assertEquals(6, cards.distinctBy { it.id }.size)
        cards.groupBy { it.itemId }.forEach { (_, pair) ->
            assertEquals(2, pair.size)
            assertEquals(pair[0].content, pair[1].content)
        }
    }

    @Test
    fun `single-item board pairs items by adjacency`() {
        val board = Board(
            id = "2",
            name = "b",
            items = listOf("sun", "SUN", "moon", "MOON"),
            isDoubleItem = false,
        )
        val cards = board.buildCards(Random(1))

        assertEquals(4, cards.size)
        assertEquals(2, board.pairCount)
        // 0&1 match, 2&3 match — and the two faces of a pair differ.
        assertEquals(cards.first { it.content == "sun" }.itemId, cards.first { it.content == "SUN" }.itemId)
        assertEquals(cards.first { it.content == "moon" }.itemId, cards.first { it.content == "MOON" }.itemId)
        assertNotEquals(
            cards.first { it.content == "sun" }.itemId,
            cards.first { it.content == "moon" }.itemId,
        )
    }

    @Test
    fun `layout is shuffled per play even for identical content`() {
        val board = Board(id = "3", name = "b", items = ('a'..'l').map { it.toString() })
        val first = board.buildCards(Random(1)).map { it.id }
        val second = board.buildCards(Random(2)).map { it.id }
        assertNotEquals(first, second)
        assertEquals(first.sorted(), second.sorted())
    }

    @Test
    fun `board difficulty falls back to the player setting when absent or unparseable`() {
        val none = Board(id = "4", name = "b", difficulty = null, items = listOf("A"))
        val junk = Board(id = "5", name = "b", difficulty = "impossible", items = listOf("A"))
        val own = Board(id = "6", name = "b", difficulty = "veryHard", items = listOf("A"))

        assertEquals(Difficulty.EASY, none.resolvedDifficulty(Difficulty.EASY))
        assertEquals(Difficulty.EASY, junk.resolvedDifficulty(Difficulty.EASY))
        assertEquals(Difficulty.VERY_HARD, own.resolvedDifficulty(Difficulty.EASY))
    }

    @Test
    fun `custom boards are identified by their id prefix`() {
        assertTrue(Board(id = "custom_17", name = "b", items = listOf("A")).isCustom)
        assertTrue(!Board(id = "17", name = "b", items = listOf("A")).isCustom)
    }
}
