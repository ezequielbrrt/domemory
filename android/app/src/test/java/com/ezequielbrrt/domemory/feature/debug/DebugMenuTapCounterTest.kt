package com.ezequielbrrt.domemory.feature.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the hidden trigger to iOS's `DebugMenuTapTrigger`: five taps, at most 1.5s apart. */
class DebugMenuTapCounterTest {
    @Test fun `five taps within the window trigger on the fifth`() {
        val counter = DebugMenuTapCounter()
        (0 until 4).forEach { assertFalse(counter.registerTap(it * 1_000L)) }
        assertTrue(counter.registerTap(4_000L))
    }

    @Test fun `a gap longer than the window starts the count over`() {
        val counter = DebugMenuTapCounter()
        (0 until 4).forEach { counter.registerTap(it * 100L) }
        assertFalse(counter.registerTap(300L + 1_501L))
        (1..3).forEach { assertFalse(counter.registerTap(1_801L + it * 100L)) }
        assertTrue(counter.registerTap(1_801L + 400L))
    }

    @Test fun `the count resets after triggering`() {
        val counter = DebugMenuTapCounter()
        (0 until 5).forEach { counter.registerTap(it * 100L) }
        (5 until 9).forEach { assertFalse(counter.registerTap(it * 100L)) }
        assertTrue(counter.registerTap(900L))
    }
}
