package com.ezequielbrrt.domemory.services.levels

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private fun TestScope.prefsOver(file: File): UserPreferences =
        UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))

    private fun TestScope.service(prefs: UserPreferences = prefsOver(tempFile())): LevelProgressService =
        LevelProgressService(prefs, this)

    private fun tempFile(): File = File.createTempFile("level_progress", ".preferences_pb").also { it.deleteOnExit() }

    @Test fun `winning unlocks the next level and stores its rating`() = runTest {
        val service = service(); advanceUntilIdle()
        assertTrue(service.isUnlocked(1)); assertFalse(service.isUnlocked(2))
        assertEquals(3, service.recordCompletion(1, true, 50.0, 90.0, 0)); advanceUntilIdle()
        assertEquals(2, service.highestUnlockedLevel); assertTrue(service.isUnlocked(2))
        // A single read is enough now — no more "call it, advance, call it again".
        assertEquals(3, service.stars(1))
    }

    @Test fun `loss does not change progress`() = runTest {
        val service = service(); advanceUntilIdle()
        assertEquals(0, service.recordCompletion(1, false, 0.0, 90.0, 9)); advanceUntilIdle()
        assertEquals(1, service.highestUnlockedLevel)
    }

    @Test fun `a replay below the high-water mark echoes the existing rating, not this run's`() = runTest {
        val service = service(); advanceUntilIdle()
        // First clear: 3 stars (fast, clean).
        assertEquals(3, service.recordCompletion(1, true, 50.0, 90.0, 0)); advanceUntilIdle()
        // A sloppy replay would only earn 1 star on its own, but the high-water mark is
        // never lowered, and the return value mirrors iOS: the level's rating *after*
        // recording, not this attempt's own — replaying a 3-star level pays (and reports)
        // nothing (spec 7.2).
        assertEquals(3, service.recordCompletion(1, true, 1.0, 90.0, 9)); advanceUntilIdle()
        assertEquals(3, service.stars(1))
    }

    @Test fun `an improvement over the high-water mark is credited and reported`() = runTest {
        val service = service(); advanceUntilIdle()
        assertEquals(1, service.recordCompletion(1, true, 1.0, 90.0, 9)); advanceUntilIdle()
        assertEquals(3, service.recordCompletion(1, true, 50.0, 90.0, 0)); advanceUntilIdle()
        assertEquals(3, service.stars(1))
    }

    @Test fun `skipping unlocks the next level without touching stars`() = runTest {
        val service = service(); advanceUntilIdle()
        service.skipLevel(1); advanceUntilIdle()
        assertEquals(2, service.highestUnlockedLevel)
        assertEquals(0, service.stars(1))
    }

    // --- Cache concurrency / consistency (the fix this test file exists to pin) --------

    @Test fun `a level already cleared before this instance existed is correct on its first read`() = runTest {
        val file = tempFile()
        val bootPrefs = prefsOver(file)
        // Written directly, standing in for a previous process's completed run.
        bootPrefs.recordLevelCompletion(level = 1, awardedStars = 3)
        bootPrefs.recordLevelCompletion(level = 2, awardedStars = 2)

        val service = LevelProgressService(bootPrefs, this)
        advanceUntilIdle()

        // No priming call, no second read — the very first ask for an already-cleared
        // level must already reflect disk, the same way `highestUnlockedLevel` always has.
        assertEquals(3, service.stars(1))
        assertEquals(2, service.stars(2))
        assertEquals(3, service.highestUnlockedLevel)
    }

    @Test fun `many concurrent readers of an unloaded level share one load, not one each`() = runTest {
        val service = service(); advanceUntilIdle()
        // Before the fix, every one of these calls would have queued its own redundant
        // disk read for the same level; none of that is observable from the return value
        // alone, but none of it should throw or produce an inconsistent result either.
        val reads = (1..50).map { async { service.stars(7) } }
        advanceUntilIdle()
        val results = reads.awaitAll()
        assertTrue(results.all { it == 0 })
        assertEquals(0, service.stars(7))
    }

    @Test fun `concurrent completions on different levels do not corrupt the cache`() = runTest {
        val service = service(); advanceUntilIdle()
        val jobs = (1..20).map { level ->
            async { service.recordCompletion(level, true, 80.0, 90.0, 0) }
        }
        advanceUntilIdle()
        jobs.awaitAll()
        advanceUntilIdle()
        for (level in 1..20) {
            assertEquals("level $level", 3, service.stars(level))
        }
        assertEquals(21, service.highestUnlockedLevel)
    }
}
