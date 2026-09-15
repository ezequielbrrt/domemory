package com.ezequielbrrt.domemory.feature.share

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins `resultGridString` against iOS's own `resultGridString` (`ShareResultCard.swift`):
 * one green square per pair (capped at 12), an amber row only when there was a mistake
 * (also capped at 12). Pure, no Compose/Android dependency. */
class ResultGridStringTest {

    @Test
    fun `greens only, no mistakes`() {
        assertEquals("🟩🟩🟩🟩", resultGridString(pairs = 4, failedTries = 0))
    }

    @Test
    fun `greens plus one amber row on a mistake`() {
        assertEquals("🟩🟩🟩\n🟧🟧", resultGridString(pairs = 3, failedTries = 2))
    }

    @Test
    fun `zero pairs produces an empty green segment`() {
        assertEquals("", resultGridString(pairs = 0, failedTries = 0))
    }

    @Test
    fun `pairs cap at 12 greens`() {
        assertEquals("🟩".repeat(12), resultGridString(pairs = 20, failedTries = 0))
    }

    @Test
    fun `failedTries cap at 12 ambers`() {
        assertEquals("🟩" + "\n" + "🟧".repeat(12), resultGridString(pairs = 1, failedTries = 30))
    }

    @Test
    fun `negative pairs never produce a negative repeat count`() {
        assertEquals("", resultGridString(pairs = -3, failedTries = 0))
    }

    @Test
    fun `zero failedTries never appends an amber row`() {
        assertEquals("🟩🟩", resultGridString(pairs = 2, failedTries = 0))
    }
}
