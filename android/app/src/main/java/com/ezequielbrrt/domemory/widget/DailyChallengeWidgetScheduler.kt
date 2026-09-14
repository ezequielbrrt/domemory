package com.ezequielbrrt.domemory.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Arms the widget's midnight refresh (spec 8.1). Called once from
 * [com.ezequielbrrt.domemory.DoMemoryApplication.onCreate] — one one-time request at the
 * *next* local midnight, phased by [millisUntilNextMidnight]. The worker enqueues its own
 * successor after refreshing. Recomputing from local calendar time on every run is important:
 * a fixed 24-hour periodic request would drift an hour away from midnight across daylight-
 * saving changes.
 *
 * "Roughly" is doing real work in that sentence: `WorkManager`, like iOS's own timeline
 * refresh, is a best-effort scheduler — Doze mode and battery optimization can delay a
 * periodic work item past its nominal fire time, the same imprecision `WidgetKit`'s timeline
 * policy has on iOS. The force-refresh after every completion
 * ([com.ezequielbrrt.domemory.AppContainer]'s `onDailyChallengeFinished`) is what keeps the
 * common case (the player actually opens the app and plays) exact regardless of this.
 */
object DailyChallengeWidgetScheduler {
    private const val UNIQUE_WORK_NAME = "daily_challenge_widget_midnight_refresh"

    fun scheduleMidnightRefresh(context: Context, zone: ZoneId = ZoneId.systemDefault()) {
        enqueueNext(context, zone, ExistingWorkPolicy.KEEP)
    }

    /** Called by the completed worker to append a newly calendar-aligned midnight refresh. */
    fun scheduleFollowingMidnightRefresh(context: Context, zone: ZoneId = ZoneId.systemDefault()) {
        // APPEND_OR_REPLACE queues this behind the currently executing unique worker rather
        // than cancelling it; if an old/cancelled chain is found, it starts a fresh one.
        enqueueNext(context, zone, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueueNext(context: Context, zone: ZoneId, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DailyChallengeWidgetRefreshWorker>()
            .setInitialDelay(millisUntilNextMidnight(Instant.now(), zone), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    /** Pure so the phasing math is testable without waiting for an actual midnight. */
    fun millisUntilNextMidnight(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = now.atZone(zone).toLocalDate()
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(now, nextMidnight).toMillis()
    }
}
