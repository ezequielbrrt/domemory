package com.ezequielbrrt.domemory.services.levels

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LevelProgressServiceTest {
    private fun TestScope.service(): LevelProgressService {
        val file = File.createTempFile("level_progress", ".preferences_pb").also { it.deleteOnExit() }
        return LevelProgressService(UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file })), this)
    }
    @Test fun `winning unlocks the next level and stores its rating`() = runTest {
        val service = service(); advanceUntilIdle()
        assertTrue(service.isUnlocked(1)); assertFalse(service.isUnlocked(2))
        assertEquals(3, service.recordCompletion(1, true, 50.0, 90.0, 0)); advanceUntilIdle()
        assertEquals(2, service.highestUnlockedLevel); assertTrue(service.isUnlocked(2)); service.stars(1); advanceUntilIdle(); assertEquals(3, service.stars(1))
    }
    @Test fun `loss does not change progress`() = runTest {
        val service = service(); advanceUntilIdle()
        assertEquals(0, service.recordCompletion(1, false, 0.0, 90.0, 9)); advanceUntilIdle()
        assertEquals(1, service.highestUnlockedLevel)
    }
}
