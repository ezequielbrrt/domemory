package com.ezequielbrrt.domemory.services.seasons

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 9.6: a finite, per-season-id twin of `LevelProgressServiceTest`. The extra cases
 * here are the ones that only make sense for a *bounded* store — completion at
 * `levelCount + 1`, `nextLevel` returning null past the end, and namespacing that keeps
 * two seasons (and endless Levels) from ever touching each other's keys.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonProgressServiceTest {
    private fun TestScope.prefs(): UserPreferences {
        val file = File.createTempFile("season_progress", ".preferences_pb").also { it.deleteOnExit() }
        return UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))
    }

    @Test fun `a fresh season starts at level 1 with no stars`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        assertEquals(1, service.highestUnlockedLevel)
        assertTrue(service.isUnlocked(1))
        assertFalse(service.isUnlocked(2))
        assertEquals(0, service.stars(1))
    }

    @Test fun `winning unlocks the next level and stores its rating`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        assertEquals(3, service.recordCompletion(1, true, 50.0, 90.0, 0)); advanceUntilIdle()
        assertEquals(2, service.highestUnlockedLevel)
        assertTrue(service.isUnlocked(2))
        assertEquals(3, service.stars(1))
    }

    @Test fun `loss does not change progress`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        assertEquals(0, service.recordCompletion(1, false, 0.0, 90.0, 9)); advanceUntilIdle()
        assertEquals(1, service.highestUnlockedLevel)
    }

    @Test fun `only the improvement over the high-water mark is credited to the wallet`() = runTest {
        val p = prefs()
        val service = SeasonProgressService("spooky", p, this); advanceUntilIdle()
        service.recordCompletion(1, true, 50.0, 90.0, 0); advanceUntilIdle() // 3 stars, +3
        assertEquals(3, p.levelsWalletBalance.first())
        service.recordCompletion(1, true, 10.0, 90.0, 9); advanceUntilIdle() // worse replay, 1 star, no credit
        assertEquals(3, p.levelsWalletBalance.first())
        assertEquals(3, service.stars(1))
    }

    @Test fun `season stars credit the shared wallet but never the endless lifetime-stars counter`() = runTest {
        val p = prefs()
        val service = SeasonProgressService("spooky", p, this); advanceUntilIdle()
        service.recordCompletion(1, true, 50.0, 90.0, 0); advanceUntilIdle()
        assertEquals(3, p.levelsWalletBalance.first())
        assertEquals(0, p.levelsLifetimeStars.first())
    }

    @Test fun `skipping unlocks the next level without storing stars`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        service.skipLevel(1); advanceUntilIdle()
        assertEquals(2, service.highestUnlockedLevel)
        assertEquals(0, service.stars(1))
    }

    @Test fun `skipping a locked level is a no-op`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        service.skipLevel(5); advanceUntilIdle()
        assertEquals(1, service.highestUnlockedLevel)
    }

    @Test fun `completion is stored as levelCount plus one so extending a season needs no migration`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        service.recordCompletion(1, true, 50.0, 90.0, 0); advanceUntilIdle()
        assertEquals(2, service.highestUnlockedLevel)
        assertTrue(service.isComplete(levelCount = 1))
        // The season is later extended to 3 levels: the player's progress reads "1 of 3"
        // rather than needing any stored value to change.
        assertFalse(service.isComplete(levelCount = 3))
        assertEquals(1, service.clearedLevelCount(levelCount = 3))
    }

    @Test fun `nextLevel is null past the season's last level`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        assertEquals(2, service.nextLevel(after = 1, levelCount = 3))
        assertNull(service.nextLevel(after = 3, levelCount = 3))
    }

    @Test fun `total stars are summed on demand and bounded by levelCount`() = runTest {
        val service = SeasonProgressService("spooky", prefs(), this); advanceUntilIdle()
        service.recordCompletion(1, true, 50.0, 90.0, 0); advanceUntilIdle() // 3 stars
        service.recordCompletion(2, true, 30.0, 90.0, 0); advanceUntilIdle() // 2 stars (fraction 0.33, in [0.25, 0.5))
        service.recordCompletion(3, true, 50.0, 90.0, 0); advanceUntilIdle() // would be 3, but excluded by levelCount below
        assertEquals(5, service.totalStars(levelCount = 2))
    }

    @Test fun `two seasons namespace independently`() = runTest {
        val p = prefs()
        val spooky = SeasonProgressService("spooky", p, this); advanceUntilIdle()
        val winter = SeasonProgressService("winter", p, this); advanceUntilIdle()
        spooky.recordCompletion(1, true, 50.0, 90.0, 0); advanceUntilIdle()
        assertEquals(2, spooky.highestUnlockedLevel)
        assertEquals(1, winter.highestUnlockedLevel)
        assertEquals(0, winter.stars(1))
    }

    @Test fun `season progress never touches endless-Levels highestUnlocked`() = runTest {
        val p = prefs()
        val season = SeasonProgressService("spooky", p, this); advanceUntilIdle()
        season.recordCompletion(1, true, 50.0, 90.0, 0); advanceUntilIdle()
        assertEquals(0, p.levelsHighestUnlocked.first())
    }
}
