package com.ezequielbrrt.domemory.services.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ezequielbrrt.domemory.DoMemoryApplication
import com.ezequielbrrt.domemory.MainActivity
import com.ezequielbrrt.domemory.R
import kotlinx.coroutines.flow.first

/**
 * Posts one of the two reminder notifications (spec 11.2). Scheduled by
 * [NotificationService] as a one-time [androidx.work.WorkRequest] with an initial delay —
 * this worker itself does not decide *when* to fire, only *what to post* once WorkManager
 * runs it.
 *
 * Reads [com.ezequielbrrt.domemory.data.prefs.UserPreferences] off the running
 * [DoMemoryApplication]'s [com.ezequielbrrt.domemory.AppContainer] rather than through a
 * custom `WorkerFactory` — this is the only Android-framework class in the app that needs a
 * dependency WorkManager's default reflection-based instantiation can't hand it, and reading
 * the already-constructed container avoids standing up `Configuration.Provider` machinery
 * for one call site.
 *
 * **Defense in depth, not the primary guard:** [NotificationService.cancelAll] already
 * cancels pending work the moment reminders are turned off, so in the common case this
 * worker never runs after that. The `notificationsEnabled` check here covers the narrow
 * window where work was already handed to the OS scheduler before a toggle-off landed.
 */
class ReminderNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = (applicationContext as DoMemoryApplication).container.prefs
        if (!prefs.notificationsEnabled.first()) return Result.success()

        ensureChannel()

        when (inputData.getString(KEY_TYPE)) {
            TYPE_INACTIVITY -> post(
                id = NOTIFICATION_ID_INACTIVITY,
                title = applicationContext.getString(R.string.notification_reminder_title),
                body = applicationContext.getString(R.string.notification_reminder_body),
            )
            TYPE_STREAK_RISK -> post(
                id = NOTIFICATION_ID_STREAK_RISK,
                title = applicationContext.getString(R.string.notification_streak_risk_title),
                body = applicationContext.getString(
                    R.string.notification_streak_risk_body,
                    inputData.getInt(KEY_STREAK, 0),
                ),
            )
        }
        return Result.success()
    }

    private fun ensureChannel() {
        // minSdk 26 == the API level NotificationChannel was introduced, so this is always
        // available — no Build.VERSION.SDK_INT guard needed, unlike most of the rest of the
        // notification-channel story on older Android codebases.
        val channel = NotificationChannel(
            NotificationService.CHANNEL_ID,
            applicationContext.getString(R.string.notification_reminder_title),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun post(id: Int, title: String, body: String) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        // Belt-and-suspenders: notificationsEnabled (the app flag) should already imply this
        // on API 33+ since activateReminders() only ever gets called after a granted runtime
        // request (feature/notifications/NotificationPermission.kt), but a revoke between
        // scheduling and firing is a real window WorkManager doesn't close for us.
        if (!hasPermission) return

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            Intent(applicationContext, MainActivity::class.java)
                .setData(Uri.parse("domemory://daily"))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_STREAK = "streak"
        const val TYPE_INACTIVITY = "inactivity"
        const val TYPE_STREAK_RISK = "streak_risk"

        private const val NOTIFICATION_ID_INACTIVITY = 1001
        private const val NOTIFICATION_ID_STREAK_RISK = 1002
    }
}
