package com.ezequielbrrt.domemory.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Recomposes the widget at the next local midnight (spec 8.1's "Timeline refreshes at the
 * next local midnight, so the 'completed' lock clears for the new day"), scheduled by
 * [DailyChallengeWidgetScheduler]. [androidx.glance.appwidget.GlanceAppWidget.updateAll]
 * re-runs `provideGlance` for every placed instance, which re-reads
 * [com.ezequielbrrt.domemory.services.daily.DailyChallengeService] — a new day's `dayKey`
 * means `isCompletedToday()` naturally flips back to false with no special "it's midnight"
 * branch needed here.
 */
class DailyChallengeWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DailyChallengeGlanceWidget().updateAll(applicationContext)
        // Do not rely on a fixed 24-hour interval: recompute the next local calendar
        // midnight so daylight-saving transitions cannot move this refresh by an hour.
        DailyChallengeWidgetScheduler.scheduleFollowingMidnightRefresh(applicationContext)
        return Result.success()
    }
}
