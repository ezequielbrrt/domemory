package com.ezequielbrrt.domemory.services.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdUnitConfigurationTest {
    @Test fun `nine remaining placements use supplied release unit ids`() {
        assertEquals(9, AdPlacement.entries.size)
        assertEquals("ca-app-pub-4297174845441653/5149706669", AdUnitConfiguration.unitId(AdPlacement.HOME_BANNER, debug = false))
        assertEquals("ca-app-pub-4297174845441653/3454937887", AdUnitConfiguration.unitId(AdPlacement.GAME_BANNER, debug = false))
        assertEquals("ca-app-pub-4297174845441653/5739899454", AdUnitConfiguration.unitId(AdPlacement.GAME_FINISHED_INTERSTITIAL, debug = false))
        assertEquals("ca-app-pub-4297174845441653/4426817783", AdUnitConfiguration.unitId(AdPlacement.APP_OPEN, debug = false))
        assertEquals("ca-app-pub-4297174845441653/7887550646", AdUnitConfiguration.unitId(AdPlacement.MULTIPLAYER_FINISHED_NATIVE, debug = false))
        AdPlacement.entries.filter { it.type == AdPlacement.Type.REWARDED }.forEach {
            assertEquals("ca-app-pub-4297174845441653/7306552982", AdUnitConfiguration.unitId(it, debug = false))
        }
    }

    @Test fun `debug builds use Google demo units`() {
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdUnitConfiguration.unitId(AdPlacement.HOME_BANNER, debug = true))
        assertEquals("ca-app-pub-3940256099942544/5224354917", AdUnitConfiguration.unitId(AdPlacement.GAME_REWARDED_HINT, debug = true))
    }

    @Test fun `every placement is configured in both build types today`() {
        // Unconfigured-placement hiding (spec: iOS's `configuredUnitID` returning nil for a
        // blank release id) has nothing to hide against yet — every Android unit id is
        // non-blank. This pins that fact so a future blank id is a deliberate, visible change.
        AdPlacement.entries.forEach {
            assertTrue(AdUnitConfiguration.isConfigured(it, debug = true))
            assertTrue(AdUnitConfiguration.isConfigured(it, debug = false))
        }
    }
}
