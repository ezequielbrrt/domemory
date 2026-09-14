package com.ezequielbrrt.domemory.services.notifications

import java.time.Instant
import java.time.ZoneId

/**
 * Pure fire-time computation for the three local reminders (spec 11.2). No Android
 * framework, no side effects, no DataStore — [NotificationService] is the impure layer
 * that turns these instants into actual scheduled [androidx.work.WorkRequest]s. Mirrors
 * the `Calendar`-based date math in `ios/DoMemory/DoMemory/Services/Notifications/
 * NotificationService.swift`, so a fire-time bug here is directly comparable to the iOS
 * source rather than something you have to re-derive.
 *
 * Every function takes [now] and [zone] explicitly rather than reading the system clock,
 * the same discipline `DayKey`/`DayProvider` use elsewhere in this codebase — day-boundary
 * logic that reads `Instant.now()` internally cannot be tested without waiting for the
 * boundary it is testing.
 */
object ReminderPlanner {
    /** Inactivity reminders fire in two tiers so a player who ignores the first still gets a later one. */
    const val INACTIVITY_TIER_1_DAYS = 2
    const val INACTIVITY_TIER_2_DAYS = 7
    const val INACTIVITY_HOUR = 19
    const val STREAK_RISK_HOUR = 20

    data class InactivityFire(val tierDays: Int, val fireAt: Instant)

    /**
     * Day 2 and day 7 from [now], both at 19:00 local (spec 11.2, spec 20 "Notification
     * hours"). Callers early-return before calling this when `notificationsEnabled` is
     * false — this function only computes *when*, never *whether*, matching the pure/impure
     * split the rest of this file's doc describes.
     */
    fun inactivityFireTimes(now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<InactivityFire> {
        val today = now.atZone(zone).toLocalDate()
        return listOf(INACTIVITY_TIER_1_DAYS, INACTIVITY_TIER_2_DAYS).map { days ->
            val fireAt = today.plusDays(days.toLong()).atTime(INACTIVITY_HOUR, 0).atZone(zone).toInstant()
            InactivityFire(days, fireAt)
        }
    }

    /**
     * Today at 20:00 local — only when there is a streak worth protecting, today's
     * challenge is not already done, and that time has not already passed (spec 11.2:
     * "only if it is still in the future"). Null means "do not schedule"; callers must not
     * substitute some other time when this returns null.
     */
    fun streakRiskFireTime(
        now: Instant,
        currentStreak: Int,
        isCompletedToday: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant? {
        if (currentStreak <= 0 || isCompletedToday) return null
        val fireAt = now.atZone(zone).toLocalDate().atTime(STREAK_RISK_HOUR, 0).atZone(zone).toInstant()
        return fireAt.takeIf { it.isAfter(now) }
    }
}

/**
 * The permission-sync decision from spec 11.2: "if the app thinks reminders are on but the
 * OS says denied, turn the flag off and cancel everything." Isolated as a pure boolean so
 * the rule itself — not the `NotificationManagerCompat` call that supplies [osEnabled] — is
 * what a test pins, per the exact bug this spec section names ("a grant that skips setting
 * [the] flag leaves the user authorized and silently un-reminded"): the dangerous direction
 * is the flag drifting out of sync with the OS in *either* direction, and this function is
 * the single place that drift is corrected.
 */
object NotificationPermissionSync {
    fun shouldDisable(appFlagEnabled: Boolean, osEnabled: Boolean): Boolean = appFlagEnabled && !osEnabled
}
