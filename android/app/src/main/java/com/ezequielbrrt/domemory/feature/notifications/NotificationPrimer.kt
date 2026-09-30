package com.ezequielbrrt.domemory.feature.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.notificationpermissionkit.authorization.NotificationAuthorizationClient
import com.ezequielbrrt.notificationpermissionkit.authorization.NotificationPermissionResult
import com.ezequielbrrt.notificationpermissionkit.authorization.NotificationPermissionStatus
import com.ezequielbrrt.notificationpermissionkit.authorization.rememberSystemNotificationAuthorizationClient
import com.ezequielbrrt.notificationpermissionkit.components.NotificationPreviewCard
import com.ezequielbrrt.notificationpermissionkit.presentation.NotificationPermissionBenefit
import com.ezequielbrrt.notificationpermissionkit.presentation.NotificationPermissionConfiguration
import com.ezequielbrrt.notificationpermissionkit.presentation.NotificationPermissionScreen
import com.ezequielbrrt.notificationpermissionkit.presentation.NotificationPermissionTheme
import com.ezequielbrrt.notificationpermissionkit.presentation.NotificationPreviewContent

/**
 * The notification permission primer (spec 11.3), drawn by NotificationPermissionKit-Android
 * (the `:notificationpermissionkit` module vendored under
 * `android/NotificationPermissionKit-Android`). Port of iOS's `NotificationPrimerContent.swift`:
 * the same violet theme, the same copy and benefits, and the same completion contract —
 * `onAuthorized` arms reminder scheduling, every outcome logs
 * `notification_primer_completed` and then calls `onFinished` so the caller can dismiss.
 *
 * Two call sites, as on iOS: the menu shows it once per install (`NavGraph.kt`) and the
 * Settings reminders toggle reuses it whenever the OS has not already authorized
 * notifications (`SettingsScreen.kt`). Both go through this host, which keeps
 * `NotificationService.activateReminders()` the only path that turns reminders on — see that
 * class's doc for the permission-sync bug iOS shipped once.
 */
@Composable
fun NotificationPrimerHost(
    visible: Boolean,
    source: String,
    onAuthorized: () -> Unit,
    onFinished: () -> Unit,
) {
    if (!visible) return
    LaunchedEffect(Unit) {
        AnalyticsService.log(AnalyticsEvent.NotificationPrimerShown(source = source))
    }
    val complete: (NotificationPermissionResult) -> Unit = { result ->
        if (result is NotificationPermissionResult.Authorized) onAuthorized()
        AnalyticsService.log(
            AnalyticsEvent.NotificationPrimerCompleted(source = source, outcome = result.analyticsOutcome()),
        )
        onFinished()
    }
    // iOS presents the primer as a sheet; a full-screen dialog window is the Android
    // equivalent, and the library's screen pads for the system bars itself. The back
    // gesture is "Maybe Later": the system dialog was never shown.
    Dialog(
        onDismissRequest = { complete(NotificationPermissionResult.Deferred) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val configuration = doMemoryNotificationPermissionConfiguration()
        NotificationPermissionScreen(
            configuration = configuration,
            onCompletion = complete,
            theme = DoMemoryNotificationPermissionTheme,
            client = rememberDoMemoryNotificationAuthorizationClient(),
            artwork = { DoMemoryNotificationPrimerArtwork(configuration.preview) },
        )
    }
}

/**
 * Port of iOS's `NotificationPrimerArtwork`: Flippo ringing a reminder bell above the
 * library's own notification preview card. It is `DefaultNotificationArtwork`'s layout with
 * the bell glyph swapped for the mascot, except that the card sits below Flippo instead of
 * across the lower half of the art, where it would cover his face.
 */
@Composable
private fun DoMemoryNotificationPrimerArtwork(preview: NotificationPreviewContent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.flippo_reminder),
            contentDescription = null,
            modifier = Modifier.height(190.dp).aspectRatio(834f / 880f),
        )
        NotificationPreviewCard(
            content = preview,
            tint = DoMemoryNotificationPermissionTheme.previewTint,
            surface = DoMemoryNotificationPermissionTheme.previewSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * Whether the OS will currently deliver this app's notifications — the check both call
 * sites make before deciding to show the primer at all, the Android reading of iOS's
 * `UNUserNotificationCenter.notificationSettings().authorizationStatus == .authorized`.
 * Below API 33 there is no runtime permission, only the system toggle.
 */
fun isNotificationAuthorized(context: Context): Boolean {
    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return enabled
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return granted && enabled
}

/** The `outcome` value of `notification_primer_completed`, verbatim from iOS's `analyticsOutcome`. */
internal fun NotificationPermissionResult.analyticsOutcome(): String = when (this) {
    is NotificationPermissionResult.Authorized -> "authorized"
    NotificationPermissionResult.Denied -> "denied"
    NotificationPermissionResult.Deferred -> "deferred"
    is NotificationPermissionResult.Failed -> "failed"
}

/**
 * Port of iOS's `NotificationPermissionTheme.doMemory`: the violet gradient reads the same
 * in light and dark mode, so the screen keeps one identity while the rest of the app follows
 * the appearance. Literal values rather than palette tokens for that reason.
 */
val DoMemoryNotificationPermissionTheme = NotificationPermissionTheme(
    backgroundTop = Color(75, 63, 200),
    backgroundBottom = Color(142, 129, 255),
    primaryText = Color.White,
    secondaryText = Color.White.copy(alpha = 0.86f),
    accent = Color.White,
    primaryButtonBackground = Color.White,
    primaryButtonForeground = Color(56, 47, 150),
    previewTint = Color(56, 47, 150),
    previewSurface = Color.White.copy(alpha = 0.22f),
)

/**
 * Port of iOS's `NotificationPermissionConfiguration.doMemory`. Every benefit maps to a
 * reminder `NotificationService` actually schedules — the streak-at-risk nudge and the two
 * inactivity tiers; nothing promises a notification the app does not send. Material icons
 * stand in for the SF Symbols (flame, brain, raised hand). Remembered because the library's
 * benefit model mints a random `id` per instance.
 */
@Composable
private fun doMemoryNotificationPermissionConfiguration(): NotificationPermissionConfiguration {
    val strings = listOf(
        R.string.notification_primer_title,
        R.string.notification_primer_message,
        R.string.notification_primer_benefit_streak,
        R.string.notification_primer_benefit_inactive,
        R.string.notification_primer_benefit_no_spam,
        R.string.common_app_name,
        R.string.notification_primer_preview_title,
        R.string.notification_primer_preview_message,
        R.string.notification_primer_enable,
        R.string.notification_primer_later,
        R.string.settings_notifications_denied_title,
        R.string.settings_notifications_denied_message,
        R.string.settings_notifications_open_settings,
    ).map { stringResource(it) }
    return remember(strings) {
        NotificationPermissionConfiguration(
            title = strings[0],
            message = strings[1],
            benefits = listOf(
                NotificationPermissionBenefit(text = strings[2], icon = Icons.Filled.LocalFireDepartment),
                NotificationPermissionBenefit(text = strings[3], icon = Icons.Filled.Psychology),
                NotificationPermissionBenefit(text = strings[4], icon = Icons.Filled.FrontHand),
            ),
            preview = NotificationPreviewContent(
                appName = strings[5],
                title = strings[6],
                message = strings[7],
                icon = Icons.Filled.LocalFireDepartment,
            ),
            primaryButtonTitle = strings[8],
            secondaryButtonTitle = strings[9],
            deniedTitle = strings[10],
            deniedMessage = strings[11],
            settingsButtonTitle = strings[12],
        )
    }
}

/**
 * The library's system client, with one adjustment below API 33: there is no runtime
 * permission there, so the system client reports `Authorized` whenever notifications are
 * enabled and the screen would complete on its own before the player saw it — silently
 * turning reminders on for everyone on Android 8–12. iOS never has that problem (its fresh
 * state is `.notDetermined`), and neither did the primer this replaces, whose "Turn On
 * Reminders" was the consent. [ConsentGatedNotificationAuthorizationClient] reports
 * `NotDetermined` there instead until that tap, so the tap stays the consent; the request
 * then answers with the system toggle's state. On API 33+ the real permission state passes
 * through untouched.
 */
@Composable
private fun rememberDoMemoryNotificationAuthorizationClient(): NotificationAuthorizationClient {
    val system = rememberSystemNotificationAuthorizationClient()
    return remember(system) { ConsentGatedNotificationAuthorizationClient(system) }
}

/**
 * Masks the system client's `Authorized` as `NotDetermined` below API 33 until
 * [requestAuthorization] has run once. The mask has to lift after the tap: the library's
 * model re-reads the status right after the request to decide the outcome, and an
 * `Authorized` that stayed masked would read as [NotificationPermissionResult.Denied] —
 * reminders would never turn on from the primer on Android 8–12. `sdkInt` is injected so
 * the JVM test can exercise both sides of the API 33 line.
 */
internal class ConsentGatedNotificationAuthorizationClient(
    private val system: NotificationAuthorizationClient,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : NotificationAuthorizationClient {
    private var requested = false

    override suspend fun authorizationStatus(): NotificationPermissionStatus {
        val status = system.authorizationStatus()
        val needsExplicitConsent = sdkInt < Build.VERSION_CODES.TIRAMISU && !requested &&
            status == NotificationPermissionStatus.Authorized
        return if (needsExplicitConsent) NotificationPermissionStatus.NotDetermined else status
    }

    override suspend fun requestAuthorization(): Boolean {
        requested = true
        return system.requestAuthorization()
    }
}
