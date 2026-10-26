package com.ezequielbrrt.domemory.services.ads

import com.google.android.ump.ConsentDebugSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 11.4: no ad request of any kind while `canRequestAds` is false. Pins the shared gate
 * every load and every `is…Configured` check reads, so the ad-free-day offer and the
 * rewarded buttons stay hidden until consent allows ads.
 */
class ConsentGateTest {
    @After
    fun tearDown() {
        ConsentService.update(canRequestAds = false, privacyOptionsRequired = false)
    }

    @Test
    fun noPlacementCanLoadBeforeConsent() {
        ConsentService.update(canRequestAds = false, privacyOptionsRequired = false)
        AdPlacement.entries.forEach { assertFalse("$it loaded without consent", AdsService.canLoad(it)) }
        assertFalse(AdsService.isRewardedConfigured(AdPlacement.AD_FREE_DAY_REWARDED))
        assertFalse(AdsService.isRewardedConfigured(AdPlacement.LEVELS_REWARDED_LIFE))
        assertFalse(AdsService.isNativeConfigured(AdPlacement.MULTIPLAYER_FINISHED_NATIVE))
    }

    @Test
    fun everyConfiguredPlacementCanLoadOnceConsentAllows() {
        ConsentService.update(canRequestAds = true, privacyOptionsRequired = false)
        AdPlacement.entries.forEach { assertTrue("$it blocked with consent", AdsService.canLoad(it)) }
        assertTrue(AdsService.isRewardedConfigured(AdPlacement.AD_FREE_DAY_REWARDED))
        assertTrue(AdsService.isNativeConfigured(AdPlacement.MULTIPLAYER_FINISHED_NATIVE))
    }

    @Test
    fun typeChecksStillApplyWithConsent() {
        ConsentService.update(canRequestAds = true, privacyOptionsRequired = false)
        assertFalse(AdsService.isRewardedConfigured(AdPlacement.HOME_BANNER))
        assertFalse(AdsService.isNativeConfigured(AdPlacement.LEVELS_REWARDED_LIFE))
    }

    @Test
    fun privacyOptionsFlagIsIndependentOfCanRequestAds() {
        ConsentService.update(canRequestAds = true, privacyOptionsRequired = true)
        assertTrue(ConsentService.isPrivacyOptionsRequired)
        ConsentService.update(canRequestAds = true, privacyOptionsRequired = false)
        assertFalse(ConsentService.isPrivacyOptionsRequired)
    }

    @Test
    fun debugGeographyParsesOnlyKnownValues() {
        assertEquals(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA, ConsentService.debugGeography("EEA"))
        assertEquals(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA, ConsentService.debugGeography("eea"))
        assertEquals(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER, ConsentService.debugGeography("Other"))
        assertNull(ConsentService.debugGeography(null))
        assertNull(ConsentService.debugGeography("US"))
    }
}
