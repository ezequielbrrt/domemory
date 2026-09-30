package com.ezequielbrrt.domemory.services.ads

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Pins the two-ad chain behind the ad-free day, ported from iOS's `AdFreeDayServiceTests`: one
 * ad grants nothing, the second completes and resets, progress survives a relaunch but not a
 * new local day, and the apology copy is shown once. Plus the grant itself, which iOS covers
 * in `PurchaseService`: 24 hours from now, stacking on a window that is still running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdFreeDayServiceTest {
    private val dayOne = LocalDate.of(2026, 9, 11)
    private val dayThree = LocalDate.of(2026, 9, 13)
    private var today = dayOne
    private var nowMillis = 1_790_337_600_000L

    private fun TestScope.prefs(): UserPreferences {
        val file = File.createTempFile("ad_free_day", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        return UserPreferences(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }))
    }

    private fun TestScope.service(prefs: UserPreferences = prefs()) =
        AdFreeDayService(prefs, DayProvider { today }, backgroundScope, now = { nowMillis })

    @Test fun `the chain requires two ads`() {
        // The offer copy, the stepper and the spec all promise two ads.
        assertEquals(2, AdFreeDayService.REQUIRED_ADS)
    }

    @Test fun `starts with nothing watched`() = runTest {
        assertEquals(0, service().adsWatched())
    }

    @Test fun `the first ad does not complete the chain`() = runTest {
        val service = service()
        assertFalse("one ad must never grant the day", service.recordAdWatched())
        assertEquals(1, service.adsWatched())
    }

    @Test fun `the second ad completes the chain and resets progress`() = runTest {
        val service = service()
        service.recordAdWatched()
        assertTrue(service.recordAdWatched())
        assertEquals("a completed chain starts the next one from zero", 0, service.adsWatched())
    }

    @Test fun `progress persists across instances within the day`() = runTest {
        val prefs = prefs()
        service(prefs).recordAdWatched()
        assertEquals(1, service(prefs).adsWatched())
    }

    @Test fun `progress resets on a new day`() = runTest {
        val service = service()
        service.recordAdWatched()
        today = dayThree
        assertEquals(0, service.adsWatched())
        assertFalse("yesterday's ad must not count toward today's chain", service.recordAdWatched())
    }

    @Test fun `the intro is shown once`() = runTest {
        val prefs = prefs()
        val service = service(prefs)
        assertFalse(service.hasSeenIntro())
        service.markIntroSeen()
        assertTrue(service.hasSeenIntro())
        assertTrue(service(prefs).hasSeenIntro())
    }

    @Test fun `reset clears progress and the intro`() = runTest {
        val service = service()
        service.recordAdWatched()
        service.markIntroSeen()
        service.reset()
        assertEquals(0, service.adsWatched())
        assertFalse(service.hasSeenIntro())
    }

    @Test fun `a grant lasts 24 hours from now`() = runTest {
        val service = service()
        runCurrent()
        assertNull(service.expiryMillis.value)
        assertFalse(service.isGrantActive())

        val expiry = service.grant()
        runCurrent()

        assertEquals(nowMillis + AdFreeDayService.GRANT_MILLIS, expiry)
        assertEquals(expiry, service.expiryMillis.value)
        assertTrue(service.isGrantActive())
        assertFalse(service.isGrantActive(atMillis = expiry))
    }

    @Test fun `a grant stacks on a window that is still running`() = runTest {
        val service = service()
        val first = service.grant()
        nowMillis += 60 * 60 * 1000 // an hour into the first day
        assertEquals(first + AdFreeDayService.GRANT_MILLIS, service.grant())
    }

    @Test fun `a grant after the window ended starts from now`() = runTest {
        val service = service()
        val first = service.grant()
        nowMillis = first + 5_000
        assertEquals(nowMillis + AdFreeDayService.GRANT_MILLIS, service.grant())
    }

    @Test fun `clearing the grant turns ads back on`() = runTest {
        val service = service()
        service.grant()
        runCurrent()
        assertTrue(service.isGrantActive())
        service.clearGrant()
        runCurrent()
        assertFalse(service.isGrantActive())
    }
}
