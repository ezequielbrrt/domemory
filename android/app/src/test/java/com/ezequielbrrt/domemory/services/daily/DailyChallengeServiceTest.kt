package com.ezequielbrrt.domemory.services.daily

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Spec 8: one board per day, one attempt per day (idempotent), win-streaks-continue /
 * loss-resets-to-zero, and the milestone thresholds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyChallengeServiceTest {
    private fun service(day: LocalDate): DailyChallengeService {
        val file = File.createTempFile("daily", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        return DailyChallengeService(prefs, DayProvider { day })
    }

    @Test fun `the board for a given day is deterministic and 6 pairs`() = runTest {
        val day = LocalDate.of(2026, 6, 16)
        val a = service(day).boardForToday()
        val b = service(day).boardForToday()
        assertEquals(a, b)
        assertEquals(6, a.pairCount)
    }

    @Test fun `a fresh install has not completed today`() = runTest {
        assertFalse(service(LocalDate.of(2026, 6, 16)).isCompletedToday())
    }

    @Test fun `a win starts a streak of 1 and locks the day`() = runTest {
        val s = service(LocalDate.of(2026, 6, 16))
        val result = s.recordCompletion(didWin = true)
        assertEquals(1, result.streak)
        assertFalse(result.alreadyCompleted)
        assertTrue(s.isCompletedToday())
    }

    @Test fun `a loss resets the streak to zero but still locks the day`() = runTest {
        val s = service(LocalDate.of(2026, 6, 16))
        val result = s.recordCompletion(didWin = false)
        assertEquals(0, result.streak)
        assertTrue(s.isCompletedToday())
    }

    @Test fun `a second attempt the same day is idempotent and echoes the existing streak`() = runTest {
        val s = service(LocalDate.of(2026, 6, 16))
        s.recordCompletion(didWin = true)
        val second = s.recordCompletion(didWin = false) // a loss recorded after a win should not overwrite
        assertEquals(1, second.streak)
        assertTrue(second.alreadyCompleted)
        assertFalse(second.isNewMilestone)
    }

    @Test fun `consecutive winning days extend the streak`() = runTest {
        val file = File.createTempFile("daily", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        val day1 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 16) })
        val day2 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 17) })
        val day3 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 18) })

        assertEquals(1, day1.recordCompletion(didWin = true).streak)
        assertEquals(2, day2.recordCompletion(didWin = true).streak)
        assertEquals(3, day3.recordCompletion(didWin = true).streak)
        assertEquals(3, day3.longestStreak())
    }

    @Test fun `a gap day breaks the streak back to 1`() = runTest {
        val file = File.createTempFile("daily", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        val day1 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 16) })
        val day3 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 18) }) // skipped the 17th

        day1.recordCompletion(didWin = true)
        val result = day3.recordCompletion(didWin = true)
        assertEquals(1, result.streak)
    }

    @Test fun `a loss between two wins breaks the streak even though it still consumes a day`() = runTest {
        val file = File.createTempFile("daily", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        val day1 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 16) })
        val day2 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 17) })
        val day3 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 18) })

        day1.recordCompletion(didWin = true)
        day2.recordCompletion(didWin = false)
        val result = day3.recordCompletion(didWin = true)
        assertEquals(1, result.streak)
    }

    @Test fun `milestones are only reported on the day the streak first reaches them`() = runTest {
        val file = File.createTempFile("daily", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        var lastMilestone = false
        var day = LocalDate.of(2026, 6, 1)
        repeat(3) {
            val result = DailyChallengeService(prefs, DayProvider { day }).recordCompletion(didWin = true)
            lastMilestone = result.isNewMilestone
            day = day.plusDays(1)
        }
        // Three wins in a row lands the streak at 3, the first milestone.
        assertTrue(lastMilestone)
    }

    @Test fun `longest streak never decreases when the current streak resets`() = runTest {
        val file = File.createTempFile("daily", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        val day1 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 16) })
        val day2 = DailyChallengeService(prefs, DayProvider { LocalDate.of(2026, 6, 17) })

        day1.recordCompletion(didWin = true)
        day2.recordCompletion(didWin = false)
        assertEquals(0, day2.currentStreak())
        assertEquals(1, day2.longestStreak())
    }
}
