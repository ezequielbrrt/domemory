package com.ezequielbrrt.domemory.services.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiplayerInviteTest {
    @Test fun `share text contains scheme link and Play Store fallback`() {
        assertEquals(
            "Challenge\ndomemory://join/ABC123\nhttps://play.google.com/store/apps/details?id=com.ezequielbrrt.domemory",
            MultiplayerInvite.shareText("Challenge", "ABC123"),
        )
    }
}
