package com.ezequielbrrt.domemory.services.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderPlannerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    // 2026-09-14T10:00:00Z — a Monday morning, well before either 19:00 or 20:00 local.
    private val morning = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun `inactivity fires at day 2 and day 7, both 19-00 local`() {
        val fires = ReminderPlanner.inactivityFireTimes(morning, zone)
        assertEquals(2, fires.size)

        val day2 = fires.first { it.tierDays == ReminderPlanner.INACTIVITY_TIER_1_DAYS }
        assertEquals(Instant.parse("2026-09-16T19:00:00Z"), day2.fireAt)

        val day7 = fires.first { it.tierDays == ReminderPlanner.INACTIVITY_TIER_2_DAYS }
        assertEquals(Instant.parse("2026-09-21T19:00:00Z"), day7.fireAt)
    }

    @Test
    fun `inactivity tiers are day 2 and day 7 — spec 20's exact pair`() {
        assertEquals(2, ReminderPlanner.INACTIVITY_TIER_1_DAYS)
        assertEquals(7, ReminderPlanner.INACTIVITY_TIER_2_DAYS)
        assertEquals(19, ReminderPlanner.INACTIVITY_HOUR)
        assertEquals(20, ReminderPlanner.STREAK_RISK_HOUR)
    }

    @Test
    fun `streak risk fires today at 20-00 when a streak is active and today is not done`() {
        val fireAt = ReminderPlanner.streakRiskFireTime(
            now = morning,
            currentStreak = 5,
            isCompletedToday = false,
            zone = zone,
        )
        assertEquals(Instant.parse("2026-09-14T20:00:00Z"), fireAt)
    }

    @Test
    fun `streak risk is null with no streak`() {
        assertNull(ReminderPlanner.streakRiskFireTime(morning, currentStreak = 0, isCompletedToday = false, zone = zone))
    }

    @Test
    fun `streak risk is null once today is already completed`() {
        assertNull(ReminderPlanner.streakRiskFireTime(morning, currentStreak = 5, isCompletedToday = true, zone = zone))
    }

    @Test
    fun `streak risk is null once 20-00 has already passed today — spec 11-2's 'only if still in the future'`() {
        val afterNine = Instant.parse("2026-09-14T21:30:00Z")
        assertNull(ReminderPlanner.streakRiskFireTime(afterNine, currentStreak = 5, isCompletedToday = false, zone = zone))
    }

    @Test
    fun `streak risk fires exactly at the boundary — one second before 20-00 is still schedulable`() {
        val justBefore = Instant.parse("2026-09-14T19:59:59Z")
        val fireAt = ReminderPlanner.streakRiskFireTime(justBefore, currentStreak = 1, isCompletedToday = false, zone = zone)
        assertTrue(fireAt != null && fireAt.isAfter(justBefore))
    }

    // -- The permission-sync rule (spec 11.2's "the permission bug worth not repeating") ----

    @Test
    fun `permission sync only disables when the app flag is on but the OS says denied`() {
        assertTrue(NotificationPermissionSync.shouldDisable(appFlagEnabled = true, osEnabled = false))
        assertFalseNoDisable(appFlagEnabled = false, osEnabled = false)
        assertFalseNoDisable(appFlagEnabled = false, osEnabled = true)
        assertFalseNoDisable(appFlagEnabled = true, osEnabled = true)
    }

    private fun assertFalseNoDisable(appFlagEnabled: Boolean, osEnabled: Boolean) {
        assertEquals(false, NotificationPermissionSync.shouldDisable(appFlagEnabled, osEnabled))
    }
}
