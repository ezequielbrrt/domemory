package com.ezequielbrrt.domemory.feature.levels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors iOS's `LivesRowEffectTests`: which heart the map header animates on a change. */
class HeaderEffectsTest {

    @Test fun `a drop breaks the first now-empty heart`() {
        assertEquals(LivesEffect.Lost(2), livesEffect(previous = 3, current = 2))
        assertEquals(LivesEffect.Lost(0), livesEffect(previous = 1, current = 0))
    }

    @Test fun `a rise bursts the last now-filled heart`() {
        assertEquals(LivesEffect.Gained(2), livesEffect(previous = 2, current = 3))
        assertEquals(LivesEffect.Gained(0), livesEffect(previous = 0, current = 1))
    }

    @Test fun `a day reset animates one heart, not four`() {
        assertEquals(LivesEffect.Gained(3), livesEffect(previous = 0, current = 4))
    }

    @Test fun `no change is no effect`() {
        assertNull(livesEffect(previous = 2, current = 2))
    }

    @Test fun `only a credit sparkles, never a spend`() {
        assertTrue(starsCredited(previous = 5, current = 7))
        assertFalse(starsCredited(previous = 7, current = 5))
        assertFalse(starsCredited(previous = 7, current = 7))
    }
}
