package com.ezequielbrrt.domemory.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 11.1: the accepted link forms and the 6-character room-code extraction rule. */
class DeepLinkTest {
    @Test fun `the custom scheme daily link is recognized`() {
        assertEquals(DeepLink.Daily, DeepLink.parse("domemory://daily"))
    }

    @Test fun `the universal link daily form is recognized`() {
        assertEquals(DeepLink.Daily, DeepLink.parse("https://domemory.app/daily"))
    }

    @Test fun `a custom scheme join link extracts and uppercases a 6-char code`() {
        assertEquals(DeepLink.Join("AB12CD"), DeepLink.parse("domemory://join/ab12cd"))
    }

    @Test fun `a universal join link extracts the same code`() {
        assertEquals(DeepLink.Join("AB12CD"), DeepLink.parse("https://domemory.app/join/ab12cd"))
    }

    @Test fun `a query-parameter code is the fallback when there is no join path`() {
        assertEquals(DeepLink.Join("AB12CD"), DeepLink.parse("domemory://open?code=ab12cd"))
    }

    @Test fun `non-alphanumeric characters in the code are stripped before the length check`() {
        assertEquals(DeepLink.Join("AB12CD"), DeepLink.parse("domemory://join/ab-12-cd"))
    }

    @Test fun `a code that is not exactly six characters is rejected`() {
        assertNull(DeepLink.parse("domemory://join/ab12c"))
        assertNull(DeepLink.parse("domemory://join/ab12cde"))
    }

    @Test fun `an unrelated link is not a deep link`() {
        assertNull(DeepLink.parse("domemory://something-else"))
        assertNull(DeepLink.parse("https://example.com/daily-ish"))
        assertNull(DeepLink.parse("https://example.com/join/AB12CD"))
    }

    @Test fun `blank, null and unparseable input all return null`() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse(""))
        assertNull(DeepLink.parse("   "))
        assertNull(DeepLink.parse("not a uri at all ::: %%"))
    }

    @Test fun `the https domain itself is never mistaken for a join code`() {
        // "domemory.app" as a bare host on an unrelated path must not somehow parse as a code.
        assertNull(DeepLink.parse("https://domemory.app/"))
    }

    @Test fun `a custom scheme season link carries the season id`() {
        assertEquals(DeepLink.OpenSeason("spooky-2026", 0), DeepLink.parse("domemory://season/spooky-2026", 0))
    }

    @Test fun `the universal season link carries the same id`() {
        assertEquals(DeepLink.OpenSeason("spooky-2026", 0), DeepLink.parse("https://domemory.app/season/spooky-2026", 0))
    }

    @Test fun `a season link without an id asks for the active season`() {
        assertEquals(DeepLink.OpenSeason(null, 0), DeepLink.parse("domemory://season", 0))
    }

    @Test fun `an invite to room SEASON is still an invite`() {
        // Room codes are six letters or digits, so SEASON is a valid one.
        assertEquals(DeepLink.Join("SEASON"), DeepLink.parse("domemory://join/season", 0))
    }

    @Test fun `the linked season opens when it is the active one`() {
        val link = DeepLink.OpenSeason("spooky-2026", 1_000)
        assertEquals(DeepLink.OpenSeason.Resolution.OPEN, link.resolve("spooky-2026", 1_000))
        assertEquals(DeepLink.OpenSeason.Resolution.OPEN, DeepLink.OpenSeason(null, 1_000).resolve("spooky-2026", 1_000))
    }

    @Test fun `a link for another season leaves the player on the menu`() {
        assertEquals(DeepLink.OpenSeason.Resolution.DROP, DeepLink.OpenSeason("winter-2026", 1_000).resolve("spooky-2026", 1_000))
    }

    @Test fun `a season link waits for the catalog, but not for ever`() {
        val link = DeepLink.OpenSeason("spooky-2026", 1_000)
        assertEquals(DeepLink.OpenSeason.Resolution.WAIT, link.resolve(null, 6_000))
        val late = 1_000 + DeepLink.OpenSeason.MAX_WAIT_MILLIS + 1
        assertEquals(DeepLink.OpenSeason.Resolution.DROP, link.resolve(null, late))
        assertEquals(DeepLink.OpenSeason.Resolution.DROP, link.resolve("spooky-2026", late))
    }
}
