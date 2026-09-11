package com.ezequielbrrt.domemory.core

import com.ezequielbrrt.domemory.core.time.DayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayKeyTest {

    @Test
    fun `keys are zero-padded YYYYMMDD`() {
        assertEquals("20260901", DayKey.of(LocalDate.of(2026, 9, 1)))
        assertEquals("20261102", DayKey.of(LocalDate.of(2026, 11, 2)))
        assertEquals("20260101", DayKey.of(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `string ordering on the key format is chronological`() {
        // This is why season windows compare as strings: no timezone offset can shift
        // a boundary day by one.
        val start = DayKey.of(LocalDate.of(2026, 9, 1))
        val middle = DayKey.of(LocalDate.of(2026, 9, 30))
        val end = DayKey.of(LocalDate.of(2026, 11, 2))
        assertTrue(start <= middle && middle <= end)
        assertTrue(DayKey.of(LocalDate.of(2026, 9, 9)) < DayKey.of(LocalDate.of(2026, 9, 10)))
        assertTrue(DayKey.of(LocalDate.of(2025, 12, 31)) < DayKey.of(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `ISO dates parse to keys and junk parses to null`() {
        assertEquals("20260901", DayKey.parse("2026-09-01"))
        assertEquals("20261102", DayKey.parse(" 2026-11-02 "))
        assertNull(DayKey.parse("2026-13-01"))
        assertNull(DayKey.parse("09-01-2026"))
        assertNull(DayKey.parse("soon"))
        assertNull(DayKey.parse(""))
    }
}
