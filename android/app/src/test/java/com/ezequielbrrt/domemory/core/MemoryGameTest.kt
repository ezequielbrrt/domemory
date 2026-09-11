package com.ezequielbrrt.domemory.core

import com.ezequielbrrt.domemory.core.model.Card
import com.ezequielbrrt.domemory.core.model.ChoiceOutcome
import com.ezequielbrrt.domemory.core.model.MemoryGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGameTest {

    /** Deterministic 3-pair board: ids 0..5, itemIds 0,0,1,1,2,2. */
    private fun game() = MemoryGame(
        (0 until 6).map { Card(id = it, itemId = it / 2, content = "e${it / 2}") },
    )

    @Test
    fun `first tap turns a card face up and counts no failure`() {
        val g = game()
        assertEquals(ChoiceOutcome.FLIPPED_UP, g.choose(0, NOW))
        assertTrue(g.cards.first { it.id == 0 }.isFaceUp)
        assertEquals(0, g.failedTries)
    }

    @Test
    fun `matching pair marks both matched and leaves them face up`() {
        val g = game()
        g.choose(0, NOW)
        assertEquals(ChoiceOutcome.MATCH, g.choose(1, NOW))
        val matched = g.cards.filter { it.isMatched }
        assertEquals(2, matched.size)
        assertTrue(matched.all { it.isFaceUp })
        assertEquals(0, g.failedTries)
    }

    @Test
    fun `mismatch increments failedTries and leaves both face up`() {
        val g = game()
        g.choose(0, NOW)
        assertEquals(ChoiceOutcome.MISMATCH, g.choose(2, NOW))
        assertEquals(1, g.failedTries)
        assertEquals(2, g.cards.count { it.isFaceUp && !it.isMatched })
    }

    @Test
    fun `a third tap resolves a mismatch immediately instead of waiting`() {
        val g = game()
        g.choose(0, NOW)
        g.choose(2, NOW)
        assertEquals(ChoiceOutcome.FLIPPED_UP, g.choose(4, NOW))
        // Only the newly chosen card is up; the mismatched pair went back down.
        assertEquals(listOf(4), g.cards.filter { it.isFaceUp }.map { it.id })
    }

    @Test
    fun `tapping a face-up or matched card is ignored and changes nothing`() {
        val g = game()
        g.choose(0, NOW)
        assertEquals(ChoiceOutcome.IGNORED, g.choose(0, NOW))

        g.choose(1, NOW)
        assertEquals(ChoiceOutcome.IGNORED, g.choose(1, NOW))
        assertEquals(0, g.failedTries)
    }

    @Test
    fun `a mismatched pair is not re-tappable, so one mistake cannot count twice`() {
        val g = game()
        g.choose(0, NOW)
        g.choose(2, NOW)
        assertEquals(1, g.failedTries)

        // Both are still face up, so tapping either is a dead tap.
        assertEquals(ChoiceOutcome.IGNORED, g.choose(0, NOW))
        assertEquals(ChoiceOutcome.IGNORED, g.choose(2, NOW))
        assertEquals(1, g.failedTries)

        // Once they flip back, missing the same pair again is a second mistake.
        g.flipDownUnmatched(NOW)
        g.choose(0, NOW)
        assertEquals(ChoiceOutcome.MISMATCH, g.choose(2, NOW))
        assertEquals(2, g.failedTries)
    }

    @Test
    fun `win requires every card matched`() {
        val g = game()
        assertFalse(g.isWon)
        listOf(0 to 1, 2 to 3, 4 to 5).forEach { (a, b) ->
            g.choose(a, NOW)
            g.choose(b, NOW)
        }
        assertTrue(g.isWon)
        assertEquals(3, g.matchedPairCount)
    }

    @Test
    fun `flipDownUnmatched leaves matched cards alone`() {
        val g = game()
        g.choose(0, NOW)
        g.choose(1, NOW) // match
        g.choose(2, NOW)
        g.choose(4, NOW) // mismatch
        g.flipDownUnmatched(NOW)
        assertEquals(2, g.cards.count { it.isFaceUp })
        assertTrue(g.cards.filter { it.isFaceUp }.all { it.isMatched })
    }

    @Test
    fun `findUnmatchedPair returns two cards sharing an itemId`() {
        val g = game()
        val pair = requireNotNull(g.findUnmatchedPair())
        val first = g.cards.first { it.id == pair.first }
        val second = g.cards.first { it.id == pair.second }
        assertEquals(first.itemId, second.itemId)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
