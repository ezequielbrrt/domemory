package com.ezequielbrrt.domemory.services.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.daily.DailyChallengeService
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/**
 * Android's counterpart to `ios/DoMemory/DoMemory/Services/Notifications/
 * NotificationService.swift` (spec 11.2, 11.3). Schedules the three local reminders
 * through [WorkManager] one-time work rather than `AlarmManager` exact alarms — none of
 * these need to-the-second precision (a "come back and play" nudge landing a few minutes
 * late is not a correctness bug the way a missed alarm clock would be), and `WorkManager`
 * survives process death and doesn't need the `SCHEDULE_EXACT_ALARM`/
 * `USE_EXACT_ALARM` permission story Android has grown around `AlarmManager` since API 31.
 *
 * **The permission bug this exists not to repeat (spec 11.2):** granting OS notification
 * permission is not enough on its own. [scheduleInactivityReminders] and
 * [refreshStreakAtRiskReminder] both early-return on `prefs.notificationsEnabled`, so a
 * grant that skips setting that flag leaves the user authorized and silently un-reminded —
 * exactly the shape of bug iOS already shipped once. There is deliberately no bare
 * "request the OS permission and call it done" path anywhere in this class or in the
 * Compose call sites that use it (`feature/notifications/`); every one of them routes a
 * grant through [activateReminders], the same shape as iOS's `activateReminders()`.
 */
class NotificationService(
    context: Context,
    private val prefs: UserPreferences,
    private val dailyChallenge: DailyChallengeService,
    private val now: () -> Instant = Instant::now,
) {
    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)

    /**
     * Turns reminders on and arms every scheduled nudge, in that order. This is the *only*
     * path that should ever set `notificationsEnabled = true` — see the class doc's warning
     * about the permission-sync bug. Call this once OS authorization is confirmed (or is not
     * needed, below API 33).
     */
    suspend fun activateReminders() {
        prefs.setNotificationsEnabled(true)
        scheduleInactivityReminders()
        refreshStreakAtRiskReminder()
    }

    /** Re-arms both inactivity tiers. Cancel + re-add, called after every game finish (spec 11.2). */
    suspend fun scheduleInactivityReminders() {
        workManager.cancelUniqueWork(WORK_INACTIVITY_DAY2)
        workManager.cancelUniqueWork(WORK_INACTIVITY_DAY7)
        if (!prefs.notificationsEnabled.first()) return

        ReminderPlanner.inactivityFireTimes(now()).forEach { fire ->
            val workName = if (fire.tierDays == ReminderPlanner.INACTIVITY_TIER_1_DAYS) WORK_INACTIVITY_DAY2 else WORK_INACTIVITY_DAY7
            enqueueReminder(
                workName = workName,
                fireAt = fire.fireAt,
                data = workDataOf(ReminderNotificationWorker.KEY_TYPE to ReminderNotificationWorker.TYPE_INACTIVITY),
            )
        }
    }

    /**
     * Schedules (or cancels) an end-of-day nudge when the player has an active streak they
     * have not kept alive today. Refreshed on launch, on foreground, and after every daily
     * completion (spec 8, 11.2) — the caller owns *when* to call this; this function only
     * decides *whether* and *when to fire*, via [ReminderPlanner.streakRiskFireTime].
     */
    suspend fun refreshStreakAtRiskReminder() {
        workManager.cancelUniqueWork(WORK_STREAK_RISK)
        if (!prefs.notificationsEnabled.first()) return

        val streak = dailyChallenge.currentStreak()
        val fireAt = ReminderPlanner.streakRiskFireTime(
            now = now(),
            currentStreak = streak,
            isCompletedToday = dailyChallenge.isCompletedToday(),
        ) ?: return

        enqueueReminder(
            workName = WORK_STREAK_RISK,
            fireAt = fireAt,
            data = workDataOf(
                ReminderNotificationWorker.KEY_TYPE to ReminderNotificationWorker.TYPE_STREAK_RISK,
                ReminderNotificationWorker.KEY_STREAK to streak,
            ),
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(WORK_INACTIVITY_DAY2)
        workManager.cancelUniqueWork(WORK_INACTIVITY_DAY7)
        workManager.cancelUniqueWork(WORK_STREAK_RISK)
    }

    /**
     * Spec 11.2: "On launch, sync the flag with OS state: if the app thinks reminders are on
     * but the OS says denied, turn the flag off and cancel everything." Call from
     * `MainActivity.onResume` (mirrors iOS calling this from the launch sequence and every
     * foreground). [NotificationPermissionSync.shouldDisable] is the pure decision this
     * wraps; this function is the impure half that reads the real OS state and acts on it.
     */
    suspend fun syncAuthorizationStatus() {
        val osEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        if (NotificationPermissionSync.shouldDisable(prefs.notificationsEnabled.first(), osEnabled)) {
            prefs.setNotificationsEnabled(false)
            cancelAll()
        }
    }

    private fun enqueueReminder(workName: String, fireAt: Instant, data: androidx.work.Data) {
        val delay = Duration.between(now(), fireAt).let { if (it.isNegative) Duration.ZERO else it }
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delay)
            .setInputData(data)
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_INACTIVITY_DAY2 = "reminder_inactivity_day2"
        const val WORK_INACTIVITY_DAY7 = "reminder_inactivity_day7"
        const val WORK_STREAK_RISK = "reminder_streak_at_risk"
        const val CHANNEL_ID = "reminders"
    }
}
