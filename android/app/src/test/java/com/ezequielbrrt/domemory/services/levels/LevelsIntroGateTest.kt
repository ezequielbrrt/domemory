package com.ezequielbrrt.domemory.services.levels

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 7.9: the intro is a one-shot, marked seen on *dismissal* rather than on
 * presentation — a kill mid-intro must leave the player eligible to see it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LevelsIntroGateTest {
    private fun TestScope.gate(): LevelsIntroGate {
        val file = File.createTempFile("levels_intro", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))
        return LevelsIntroGate(prefs)
    }

    @Test fun `presents once per install until dismissed`() = runTest {
        val gate = gate()
        assertTrue(gate.shouldPresent())
        gate.markSeen()
        assertFalse(gate.shouldPresent())
    }

    @Test fun `staying open without dismissing leaves it eligible again`() = runTest {
        // Simulates a kill mid-intro: shouldPresent is asked (and presumably shown)
        // several times, but markSeen is never reached.
        val gate = gate()
        assertTrue(gate.shouldPresent())
        assertTrue(gate.shouldPresent())
        assertTrue(gate.shouldPresent())
    }
}
