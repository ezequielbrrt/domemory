package com.ezequielbrrt.domemory.feature.adfree

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.feature.adfree.AdFreeDayOffer.Phase
import com.ezequielbrrt.domemory.services.ads.AdFreeDayService
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The ad-free day sheet's state machine, driven with a fake ad instead of AdMob. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdFreeDayOfferTest {
    private val nowMillis = 1_790_337_600_000L

    private class FakeAds(var readyOnLoad: Boolean = true) : AdFreeDayAds {
        var loads = 0
        private var pending: Pair<() -> Unit, (Boolean) -> Unit>? = null

        override fun load(onReady: (Boolean) -> Unit) {
            loads++
            onReady(readyOnLoad)
        }

        override fun show(onReward: () -> Unit, onDismissed: (rewarded: Boolean) -> Unit) {
            pending = onReward to onDismissed
        }

        /** The player closes the ad, after watching it through or not. */
        fun close(rewarded: Boolean) {
            val (onReward, onDismissed) = checkNotNull(pending) { "no ad is showing" }
            pending = null
            if (rewarded) onReward()
            onDismissed(rewarded)
        }
    }

    private fun TestScope.service(): AdFreeDayService {
        val file = File.createTempFile("ad_free_offer", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }))
        return AdFreeDayService(prefs, DayProvider { LocalDate.of(2026, 9, 11) }, backgroundScope, now = { nowMillis })
    }

    private fun TestScope.offer(
        service: AdFreeDayService,
        ads: FakeAds,
        events: MutableList<AnalyticsEvent> = mutableListOf(),
        haptics: MutableList<HapticIntent> = mutableListOf(),
    ) = AdFreeDayOffer("menu_levels", service, ads, backgroundScope, onAnalytics = { events += it }, onHaptic = { haptics += it })

    @Test fun `the first open is the apology, and later opens are neutral`() = runTest {
        val service = service()
        val events = mutableListOf<AnalyticsEvent>()

        val first = offer(service, FakeAds(), events)
        first.start()
        assertTrue(first.state.value.isIntro)
        assertEquals(Phase.READY, first.state.value.phase)
        assertEquals(AnalyticsEvent.AdFreeDayOfferShown("menu_levels", isIntro = true, adsWatched = 0), events.first())

        val second = offer(service, FakeAds())
        second.start()
        assertFalse(second.state.value.isIntro)
    }

    @Test fun `with no ad available the button retries and keeps progress`() = runTest {
        val ads = FakeAds(readyOnLoad = false)
        val offer = offer(service(), ads)
        offer.start()
        assertEquals(Phase.NO_FILL, offer.state.value.phase)

        ads.readyOnLoad = true
        offer.primaryAction()
        assertEquals(Phase.READY, offer.state.value.phase)
        assertEquals(2, ads.loads)
    }

    @Test fun `two watched ads grant the day, logging each step of the funnel`() = runTest {
        val service = service()
        val ads = FakeAds()
        val events = mutableListOf<AnalyticsEvent>()
        val haptics = mutableListOf<HapticIntent>()
        val offer = offer(service, ads, events, haptics)
        offer.start()

        offer.primaryAction()
        assertEquals(Phase.PRESENTING, offer.state.value.phase)
        ads.close(rewarded = true)
        runCurrent()
        assertEquals(1, offer.state.value.adsWatched)
        assertEquals("one ad grants nothing", Phase.READY, offer.state.value.phase)
        assertFalse(service.isGrantActive())

        offer.primaryAction()
        ads.close(rewarded = true)
        runCurrent()
        assertEquals(Phase.ACTIVE, offer.state.value.phase)
        assertEquals(nowMillis + AdFreeDayService.GRANT_MILLIS, offer.state.value.expiryMillis)
        assertTrue(service.isGrantActive())
        assertEquals(listOf(HapticIntent.REWARD), haptics)

        assertEquals(
            listOf(
                AnalyticsEvent.AdFreeDayOfferShown("menu_levels", isIntro = true, adsWatched = 0),
                AnalyticsEvent.AdFreeDayWatchTapped("menu_levels", adNumber = 1),
                AnalyticsEvent.AdFreeDayAdWatched("menu_levels", adNumber = 1),
                AnalyticsEvent.AdFreeDayWatchTapped("menu_levels", adNumber = 2),
                AnalyticsEvent.AdFreeDayAdWatched("menu_levels", adNumber = 2),
                AnalyticsEvent.AdFreeDayGranted("menu_levels"),
            ),
            events,
        )
    }

    @Test fun `closing an ad early grants nothing and loads another`() = runTest {
        val service = service()
        val ads = FakeAds()
        val offer = offer(service, ads)
        offer.start()

        offer.primaryAction()
        ads.close(rewarded = false)
        runCurrent()

        assertEquals(0, offer.state.value.adsWatched)
        assertEquals(Phase.READY, offer.state.value.phase)
        assertEquals(2, ads.loads)
        assertEquals(0, service.adsWatched())
    }

    @Test fun `opening while ad-free shows the running grant without loading an ad`() = runTest {
        val service = service()
        service.grant()
        runCurrent()
        val ads = FakeAds()
        val offer = offer(service, ads)

        offer.start()

        assertEquals(Phase.ACTIVE, offer.state.value.phase)
        assertEquals(0, ads.loads)
    }

    @Test fun `the button is ignored while an ad is loading or showing`() = runTest {
        val ads = FakeAds()
        val offer = offer(service(), ads)
        offer.start()
        offer.primaryAction()
        assertEquals(Phase.PRESENTING, offer.state.value.phase)

        offer.primaryAction() // a second tap while the ad is up
        assertEquals(Phase.PRESENTING, offer.state.value.phase)
        assertEquals(1, ads.loads)
    }

    @Test fun `entry state names what the pill showed`() {
        assertEquals("idle", adFreeDayEntryState(isActive = false, adsWatched = 0))
        assertEquals("in_progress", adFreeDayEntryState(isActive = false, adsWatched = 1))
        assertEquals("active", adFreeDayEntryState(isActive = true, adsWatched = 0))
    }
}
