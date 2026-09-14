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
}
