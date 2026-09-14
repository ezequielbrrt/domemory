package com.ezequielbrrt.domemory.services.ads

import com.ezequielbrrt.domemory.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFinishedInterstitialTriggerTest {
    @Test fun `allowInterstitial false never requests, regardless of cadence`() {
        var state = AdFrequencyState()
        repeat(2) {
            state = AdFrequencyCap.afterGameCompletion(state, Difficulty.EASY, 20_000, 100_000, false).state
        }
        // The third qualifying completion would normally REQUEST for EASY (every 3rd win) —
        // the skip gate must still refuse it.
        val decision = GameFinishedInterstitialTrigger.evaluate(
            state = state,
            difficulty = Difficulty.EASY,
            gameDurationMillis = 20_000,
            nowMillis = 100_000,
            allowInterstitial = false,
        )
        assertFalse(decision.shouldRequestPresentation)
        // The skip gate must also leave the cadence state untouched — a paid skip is not a
        // "counted" completion either way, since the underlying AdFrequencyCap call never ran.
        assertEquals(state, decision.nextState)
    }

    @Test fun `allowInterstitial true mirrors AdFrequencyCap's own decision`() {
        var state = AdFrequencyState()
        repeat(2) {
            state = AdFrequencyCap.afterGameCompletion(state, Difficulty.EASY, 20_000, 100_000, false).state
        }
        val decision = GameFinishedInterstitialTrigger.evaluate(
            state = state,
            difficulty = Difficulty.EASY,
            gameDurationMillis = 20_000,
            nowMillis = 100_000,
            allowInterstitial = true,
        )
        assertTrue(decision.shouldRequestPresentation)
    }

    @Test fun `a too-short game never requests even when otherwise due`() {
        val state = AdFrequencyState(qualifyingCompletions = 2)
        val decision = GameFinishedInterstitialTrigger.evaluate(
            state = state,
            difficulty = Difficulty.EASY,
            gameDurationMillis = 19_999,
            nowMillis = 100_000,
            allowInterstitial = true,
        )
        assertFalse(decision.shouldRequestPresentation)
    }
}
