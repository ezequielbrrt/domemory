package com.ezequielbrrt.domemory.data

import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.remote.BoardDecoder
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinned against `src/test/resources/data.json`, a verbatim copy of the live
 * `/data` node. If the catalog's shape ever changes, this fails before a player sees
 * an empty grid.
 */
class BoardDecoderTest {

    private val liveCatalog: List<Map<String, Any?>> by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("data.json"))
            .bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            entry.keys().asSequence().associateWith { key ->
                when (val value = entry.get(key)) {
                    is JSONArray -> (0 until value.length()).map { value.get(it) }
                    else -> value
                }
            }
        }
    }

    @Test
    fun `the live catalog decodes completely`() {
        val boards = BoardDecoder.decodeCatalog(liveCatalog)
        assertEquals(134, boards.size)
        assertEquals(134, boards.distinctBy { it.id }.size)
    }

    @Test
    fun `every live board carries a parseable difficulty`() {
        val boards = BoardDecoder.decodeCatalog(liveCatalog)
        boards.forEach { assertNotNull("board ${it.id}", Difficulty.parse(it.difficulty)) }

        val byDifficulty = boards.groupingBy { Difficulty.parse(it.difficulty) }.eachCount()
        assertEquals(35, byDifficulty[Difficulty.EASY])
        assertEquals(35, byDifficulty[Difficulty.MEDIUM])
        assertEquals(33, byDifficulty[Difficulty.HARD])
        assertEquals(31, byDifficulty[Difficulty.VERY_HARD])
    }

    @Test
    fun `every live board builds a playable set of cards`() {
        BoardDecoder.decodeCatalog(liveCatalog).forEach { board ->
            assertTrue("board ${board.id} is not double-item", board.isDoubleItem)
            val cards = board.buildCards()
            assertEquals(board.items.size * 2, cards.size)
            assertEquals(board.items.size, board.pairCount)
            // Two cards per pair key, or the board is unwinnable.
            cards.groupBy { it.itemId }.forEach { (_, pair) -> assertEquals(2, pair.size) }
        }
    }

    @Test
    fun `live board sizes stay inside what the layout can show without scrolling`() {
        val sizes = BoardDecoder.decodeCatalog(liveCatalog).map { it.items.size }.toSortedSet()
        assertEquals(listOf(4, 6, 10, 12), sizes.toList())
    }

    @Test
    fun `a map keyed by index decodes the same as an array`() {
        // Realtime Database hands back a Map when the array is sparse.
        val asMap = liveCatalog.withIndex().associate { (i, v) -> i.toString() to v }
        assertEquals(
            BoardDecoder.decodeCatalog(liveCatalog).map { it.id }.sorted(),
            BoardDecoder.decodeCatalog(asMap).map { it.id }.sorted(),
        )
    }

    @Test
    fun `nulls and unusable entries are dropped without failing the catalog`() {
        val raw = listOf(
            null,
            mapOf("id" to "1", "items" to listOf("A", "B")),
            mapOf("items" to listOf("A", "B")),          // no id
            mapOf("id" to "3", "items" to listOf("A")),  // cannot form a pair
            mapOf("id" to "4"),                          // no items
            "not a board",
        )
        val boards = BoardDecoder.decodeCatalog(raw)
        assertEquals(listOf("1"), boards.map { it.id })
    }

    @Test
    fun `an unexpected payload yields an empty catalog rather than throwing`() {
        assertTrue(BoardDecoder.decodeCatalog(null).isEmpty())
        assertTrue(BoardDecoder.decodeCatalog("nonsense").isEmpty())
        assertTrue(BoardDecoder.decodeCatalog(42).isEmpty())
        assertTrue(BoardDecoder.decodeCatalog(emptyList<Any>()).isEmpty())
    }

    @Test
    fun `a numeric id is accepted and defaults are applied`() {
        val board = BoardDecoder.decodeBoard(
            mapOf("id" to 17L, "items" to listOf("A", "B")),
        )
        assertNotNull(board)
        assertEquals("17", board!!.id)
        assertEquals("String", board.itemType)
        assertTrue(board.isDoubleItem) // absent means the catalog's universal value
        assertNull(board.difficulty)
    }
}
