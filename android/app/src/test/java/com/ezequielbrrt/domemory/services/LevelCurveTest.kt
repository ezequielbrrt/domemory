package com.ezequielbrrt.domemory.services

import com.ezequielbrrt.domemory.services.levels.LevelCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCurveTest {

    @Test
    fun `pairs hit every anchor exactly`() {
        assertEquals(3, LevelCurve.pairs(1))
        assertEquals(4, LevelCurve.pairs(5))
        assertEquals(6, LevelCurve.pairs(10))
        assertEquals(9, LevelCurve.pairs(25))
        assertEquals(12, LevelCurve.pairs(50))
    }

    @Test
    fun `seconds hit every anchor exactly`() {
        assertEquals(90, LevelCurve.seconds(1))
        assertEquals(85, LevelCurve.seconds(5))
        assertEquals(75, LevelCurve.seconds(10))
        assertEquals(60, LevelCurve.seconds(25))
        assertEquals(45, LevelCurve.seconds(50))
        assertEquals(35, LevelCurve.seconds(80))
    }

    @Test
    fun `maxFailures hit every anchor exactly`() {
        assertEquals(4, LevelCurve.maxFailures(1))
        assertEquals(6, LevelCurve.maxFailures(5))
        assertEquals(8, LevelCurve.maxFailures(10))
        assertEquals(11, LevelCurve.maxFailures(25))
        assertEquals(14, LevelCurve.maxFailures(50))
    }

    @Test
    fun `values interpolate linearly between anchors`() {
        // Midway between (1,90) and (5,85).
        assertEquals(88, LevelCurve.seconds(3))
        // Midway between (10,6) and (25,9): 6 + 0.5*3 = 7.5 -> 8 (round-half-up).
        assertEquals(8, LevelCurve.pairs(18))
        assertEquals(7, LevelCurve.maxFailures(7))
    }

    @Test
    fun `the last anchor is held forever after`() {
        assertEquals(12, LevelCurve.pairs(50))
        assertEquals(12, LevelCurve.pairs(500))
        assertEquals(12, LevelCurve.pairs(100_000))
        assertEquals(35, LevelCurve.seconds(1_000))
        assertEquals(14, LevelCurve.maxFailures(1_000))
    }

    @Test
    fun `levels below one clamp to the first anchor`() {
        assertEquals(3, LevelCurve.pairs(0))
        assertEquals(3, LevelCurve.pairs(-40))
        assertEquals(90, LevelCurve.seconds(0))
    }

    @Test
    fun `the mistake budget always exceeds the pair count`() {
        // Even perfect recall costs mismatches; a budget at or below the pair count
        // would make levels unwinnable.
        (1..200).forEach { level ->
            assertTrue(
                "level $level: ${LevelCurve.maxFailures(level)} vs ${LevelCurve.pairs(level)}",
                LevelCurve.maxFailures(level) > LevelCurve.pairs(level),
            )
        }
    }

    @Test
    fun `curves are monotonic in the direction they should be`() {
        (1..200).zipWithNext().forEach { (a, b) ->
            assertTrue(LevelCurve.pairs(b) >= LevelCurve.pairs(a))
            assertTrue(LevelCurve.seconds(b) <= LevelCurve.seconds(a))
            assertTrue(LevelCurve.maxFailures(b) >= LevelCurve.maxFailures(a))
        }
    }

    @Test
    fun `the pair cap is the floor on a season emoji pool`() {
        assertEquals(12, LevelCurve.maxPairs)
    }

    @Test
    fun `the pie appears from level 25`() {
        assertFalse(LevelCurve.showsPie(24))
        assertTrue(LevelCurve.showsPie(25))
        assertTrue(LevelCurve.showsPie(400))
    }
}
