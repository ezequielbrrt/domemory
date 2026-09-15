package com.ezequielbrrt.domemory.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the pure selection logic behind `GameScreen.kt`'s HUD chip accessibility
 * descriptions (spec 14.5) — separate from the `@Composable` that resolves the actual
 * `stringResource` format, so it is testable without Compose/Robolectric. */
class GameHudAccessibilityTest {

    @Test
    fun `timer accessibility is null while not frozen`() {
        assertNull(timerAccessibilitySeconds(timeRemainingSeconds = 12, isFrozen = false))
    }

    @Test
    fun `timer accessibility carries the remaining seconds while frozen`() {
        assertEquals(12, timerAccessibilitySeconds(timeRemainingSeconds = 12, isFrozen = true))
    }

    @Test
    fun `timer accessibility is null even at zero seconds when not frozen`() {
        // A ticking-out timer must not suddenly gain a spoken description just because it
        // hit zero — only `isFrozen` gates this, per spec 14.5.
        assertNull(timerAccessibilitySeconds(timeRemainingSeconds = 0, isFrozen = false))
    }

    @Test
    fun `fails chip accessibility is null with no mistake budget`() {
        // Free play and the Daily Challenge have no `maxFailures` — nothing to announce.
        assertNull(failsChipAccessibilityValues(failedTries = 2, maxFailures = null))
    }

    @Test
    fun `fails chip accessibility carries used and max when a budget exists`() {
        assertEquals(2 to 5, failsChipAccessibilityValues(failedTries = 2, maxFailures = 5))
    }

    @Test
    fun `fails chip accessibility reports zero used against a real budget`() {
        assertEquals(0 to 5, failsChipAccessibilityValues(failedTries = 0, maxFailures = 5))
    }
}
