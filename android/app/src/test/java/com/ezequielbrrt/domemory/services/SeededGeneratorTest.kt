package com.ezequielbrrt.domemory.services

import com.ezequielbrrt.domemory.core.rng.EmojiPool
import com.ezequielbrrt.domemory.core.rng.SeededGenerator
import com.ezequielbrrt.domemory.services.levels.BoardGenerators
import com.ezequielbrrt.domemory.services.levels.LevelCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeededGeneratorTest {

    @Test
    fun `FNV-1a matches the known 64-bit vectors`() {
        // Reference values for FNV-1a 64.
        assertEquals(0xcbf29ce484222325UL, SeededGenerator.fnv1a64(""))
        assertEquals(0xaf63dc4c8601ec8cUL, SeededGenerator.fnv1a64("a"))
        assertEquals(0x85944171f73967e8UL, SeededGenerator.fnv1a64("foobar"))
    }

    @Test
    fun `the same seed always produces the same stream`() {
        val a = SeededGenerator("20260910")
        val b = SeededGenerator("20260910")
        repeat(64) { assertEquals(a.nextULong(), b.nextULong()) }
    }

    @Test
    fun `different seeds diverge`() {
        val a = SeededGenerator("20260910")
        val b = SeededGenerator("20260911")
        assertNotEquals(a.nextULong(), b.nextULong())
    }

    @Test
    fun `a seeded shuffle is a permutation and is reproducible`() {
        val first = SeededGenerator("level-7").shuffled(EmojiPool.all)
        val second = SeededGenerator("level-7").shuffled(EmojiPool.all)
        assertEquals(first, second)
        assertEquals(EmojiPool.all.sorted(), first.sorted())
    }

    @Test
    fun `the emoji pool is 48 distinct entries in a fixed order`() {
        assertEquals(48, EmojiPool.size)
        assertEquals(48, EmojiPool.all.distinct().size)
        // Order is part of the contract — seeded selection depends on it.
        assertEquals("😀", EmojiPool.all.first())
        assertEquals("⚡️", EmojiPool.all.last())
    }

    @Test
    fun `the daily board is six pairs and stable for a given day`() {
        val a = BoardGenerators.daily("20260910")
        val b = BoardGenerators.daily("20260910")
        val other = BoardGenerators.daily("20260911")

        assertEquals(6, a.pairCount)
        assertEquals(a.items, b.items)
        assertNotEquals(a.items, other.items)
        assertEquals("medium", a.difficulty)
        assertTrue(a.isDoubleItem)
        assertEquals(6, a.items.distinct().size)
    }

    @Test
    fun `generated levels follow the curve and never repeat an emoji on a board`() {
        listOf(1, 5, 10, 25, 50, 120).forEach { level ->
            val board = BoardGenerators.endlessLevel(level)
            assertEquals(LevelCurve.pairs(level), board.pairCount)
            assertEquals(board.items.size, board.items.distinct().size)
        }
    }

    @Test
    fun `season levels are namespaced by season id`() {
        val pool = EmojiPool.all.take(12)
        val spooky = BoardGenerators.seasonLevel("spooky-2026", 3, pool)
        val winter = BoardGenerators.seasonLevel("winter-2026", 3, pool)
        assertNotEquals(spooky.items, winter.items)
        assertEquals(spooky.items, BoardGenerators.seasonLevel("spooky-2026", 3, pool).items)
    }

    @Test
    fun `a season pool at the curve cap can still fill its deepest board`() {
        val pool = EmojiPool.all.take(LevelCurve.maxPairs)
        val board = BoardGenerators.seasonLevel("s", 500, pool)
        assertEquals(LevelCurve.maxPairs, board.pairCount)
    }
}
