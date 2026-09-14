package com.ezequielbrrt.domemory.feature.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiplayerQrScanTest {
    @Test
    fun `a scanned invite uses the same normalization as a deep link`() {
        assertEquals("AB12CD", MultiplayerQrScan.roomCode("domemory://join/ab-12-cd"))
        assertEquals("AB12CD", MultiplayerQrScan.roomCode("https://domemory.app/join/ab12cd"))
    }

    @Test
    fun `a daily or unrelated QR code cannot join a room`() {
        assertNull(MultiplayerQrScan.roomCode("domemory://daily"))
        assertNull(MultiplayerQrScan.roomCode("https://example.com/join/AB12CD"))
        assertNull(MultiplayerQrScan.roomCode(null))
    }
}
