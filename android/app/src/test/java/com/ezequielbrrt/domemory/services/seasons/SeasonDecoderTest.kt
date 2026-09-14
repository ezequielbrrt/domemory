package com.ezequielbrrt.domemory.services.seasons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonDecoderTest {
    private val pool = (1..12).joinToString(",") { "\"e$it\"" }
    @Test fun `invalid structure is skipped while a valid sibling survives`() {
        val seasons = SeasonDecoder.decode("""{"bad":{"enabled":true,"levelCount":1,"emojiPool":["x"]},"good":{"enabled":true,"startDate":"2026-09-01","endDate":"2026-09-30","levelCount":2,"emojiPool":[$pool]}}""")
        assertEquals(listOf("good"), seasons.map { it.id })
    }
    @Test fun `active selection is inclusive priority ordered and deterministic`() {
        val raw = """{"z":{"enabled":true,"startDate":"2026-09-01","endDate":"2026-09-10","priority":1,"levelCount":1,"emojiPool":[$pool]},"a":{"enabled":true,"startDate":"2026-09-01","endDate":"2026-09-10","priority":1,"levelCount":1,"emojiPool":[$pool]}}"""
        val seasons = SeasonDecoder.decode(raw)
        assertEquals("a", SeasonDecoder.active(seasons, "20260901")?.id)
        assertNull(SeasonDecoder.active(seasons, "20260911"))
    }
}
