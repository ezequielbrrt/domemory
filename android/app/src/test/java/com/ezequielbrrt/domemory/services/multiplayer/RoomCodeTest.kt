package com.ezequielbrrt.domemory.services.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class RoomCodeTest {
    @Test fun `codes have six characters from the readable shared alphabet`() {
        val code = RoomCode.generate(Random(7))
        assertEquals(RoomCode.LENGTH, code.length)
        assertNotNull(RoomCode.normalize(code))
    }

    @Test fun `input is trimmed uppercased and rejects ambiguous alphabet characters`() {
        assertEquals("ABC234", RoomCode.normalize(" abc234 "))
        assertNull(RoomCode.normalize("ABIO01"))
        assertNull(RoomCode.normalize("SHORT"))
    }
}
