package com.ezequielbrrt.domemory.core

import com.ezequielbrrt.domemory.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {

    @Test
    fun `time limits match the spec, including the two deliberate oddities`() {
        assertEquals(110.0, Difficulty.EASY.timeLimitSeconds, 0.0)
        // medium and hard share a clock: difficulty is board size, not time alone.
        assertEquals(60.0, Difficulty.MEDIUM.timeLimitSeconds, 0.0)
        assertEquals(60.0, Difficulty.HARD.timeLimitSeconds, 0.0)
        // very hard gets *more* time than hard because its boards are larger.
        assertEquals(70.0, Difficulty.VERY_HARD.timeLimitSeconds, 0.0)
    }

    @Test
    fun `interstitial frequency and pie follow difficulty`() {
        assertEquals(3, Difficulty.EASY.interstitialEveryNWins)
        assertEquals(3, Difficulty.MEDIUM.interstitialEveryNWins)
        assertEquals(2, Difficulty.HARD.interstitialEveryNWins)
        assertEquals(2, Difficulty.VERY_HARD.interstitialEveryNWins)

        assertFalse(Difficulty.EASY.showsPie)
        assertFalse(Difficulty.MEDIUM.showsPie)
        assertTrue(Difficulty.HARD.showsPie)
        assertTrue(Difficulty.VERY_HARD.showsPie)
    }

    @Test
    fun `parsing tolerates casing and separators, and rejects junk`() {
        assertEquals(Difficulty.VERY_HARD, Difficulty.parse("veryHard"))
        assertEquals(Difficulty.VERY_HARD, Difficulty.parse("VERYHARD"))
        assertEquals(Difficulty.VERY_HARD, Difficulty.parse("very_hard"))
        assertEquals(Difficulty.VERY_HARD, Difficulty.parse(" very hard "))
        assertNull(Difficulty.parse("nightmare"))
        assertNull(Difficulty.parse(null))
        assertEquals(Difficulty.EASY, Difficulty.parseOrDefault("nope", Difficulty.EASY))
    }
}
