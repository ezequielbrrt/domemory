package com.ezequielbrrt.domemory.services.whatsnew

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhatsNewManagerTest {
    @Test fun `new install records version without showing notes`() = runTest {
        val prefs = newPrefs()
        val manager = WhatsNewManager(prefs, "1.0.0")

        assertFalse(manager.shouldShowAfterLaunch(hasOnboarded = false))
        assertFalse(manager.shouldShowAfterLaunch(hasOnboarded = true))
        assertTrue(prefs.whatsNewLastSeenVersion.first() == "1.0.0")
    }

    @Test fun `existing user sees notes once after an update`() = runTest {
        val prefs = newPrefs().also { it.setWhatsNewLastSeenVersion("1.0.0") }
        val manager = WhatsNewManager(prefs, "1.1.0")

        assertTrue(manager.shouldShowAfterLaunch(hasOnboarded = true))
        manager.markSeen()
        assertFalse(manager.shouldShowAfterLaunch(hasOnboarded = true))
    }

    private fun newPrefs(): UserPreferences {
        val file = File.createTempFile("whats_new", ".preferences_pb").also { it.deleteOnExit() }
        return UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
    }
}
