package com.ezequielbrrt.domemory.services.ads

import com.ezequielbrrt.domemory.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFrequencyCapTest {
    @Test fun `easy and medium request every third qualifying completion`() {
        var state = AdFrequencyState()
        repeat(2) { state = AdFrequencyCap.afterGameCompletion(state, Difficulty.EASY, 20_000, 100_000, false).state }
        assertEquals(InterstitialDecision.REQUEST, AdFrequencyCap.afterGameCompletion(state, Difficulty.EASY, 20_000, 100_000, false).decision)
    }

    @Test fun `hard requests every second qualifying completion`() {
        val first = AdFrequencyCap.afterGameCompletion(AdFrequencyState(), Difficulty.HARD, 20_000, 100_000, false)
        assertEquals(InterstitialDecision.DEFERRED, first.decision)
        assertEquals(InterstitialDecision.REQUEST, AdFrequencyCap.afterGameCompletion(first.state, Difficulty.HARD, 20_000, 100_000, false).decision)
    }

    @Test fun `short games rewarded views and global gap do not request`() {
        assertEquals(InterstitialDecision.TOO_SHORT, AdFrequencyCap.afterGameCompletion(AdFrequencyState(), Difficulty.EASY, 19_999, 100_000, false).decision)
        assertEquals(InterstitialDecision.RECENT_REWARDED, AdFrequencyCap.afterGameCompletion(AdFrequencyState(lastRewardedAtMillis = 50_001), Difficulty.EASY, 20_000, 100_000, false).decision)
        assertEquals(InterstitialDecision.GLOBAL_GAP, AdFrequencyCap.afterGameCompletion(AdFrequencyState(2, lastFullScreenAtMillis = 20_001), Difficulty.EASY, 20_000, 100_000, false).decision)
    }

    @Test fun `app open cache expires after four hours`() {
        assertTrue(AdFrequencyCap.isAppOpenFresh(1, AdFrequencyCap.APP_OPEN_FRESHNESS_MILLIS))
        assertFalse(AdFrequencyCap.isAppOpenFresh(0, AdFrequencyCap.APP_OPEN_FRESHNESS_MILLIS))
    }
}
