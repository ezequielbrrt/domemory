package com.ezequielbrrt.domemory.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.feature.notifications.NotificationPrimerHost
import com.ezequielbrrt.domemory.feature.notifications.isNotificationAuthorized
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.ui.components.BackButton
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import com.ezequielbrrt.domemory.ui.theme.ThemePreference
import com.ezequielbrrt.reviewflow.openPlayStoreReviewPage

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onTheme: (ThemePreference) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onEnableReminders: () -> Unit,
    onDisableReminders: () -> Unit,
    onWhatsNew: () -> Unit,
    onAchievements: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val p = LocalPalette.current
    val context = LocalContext.current
    // Spec 11.3's "reused by the Settings toggle", the way iOS's `handleEnableNotifications`
    // does it: already authorized → enable straight away; otherwise the same primer the menu
    // shows (feature/notifications/NotificationPrimer.kt), which asks the OS with context
    // behind it, or offers the system settings once the dialog can no longer appear.
    // onEnableReminders — which flips notificationsEnabled — only ever runs after a confirmed
    // grant; denial leaves the flag untouched, matching spec 11.2's permission-sync rule.
    var showNotificationPrimer by remember { mutableStateOf(false) }
    val requestPermission = {
        if (isNotificationAuthorized(context)) onEnableReminders() else showNotificationPrimer = true
    }
    NotificationPrimerHost(
        visible = showNotificationPrimer,
        source = "settings",
        onAuthorized = onEnableReminders,
        onFinished = { showNotificationPrimer = false },
    )
    Column(
        Modifier.fillMaxSize().background(p.appBackground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BackButton(onClick = onBack)
            Text(stringResource(R.string.settings_title), style = DoMemoryType.display(26), color = p.primary)
        }
        SettingGroup(stringResource(R.string.settings_section_game)) {
            // First row of the first section, as on iOS (`SettingsView`'s Game section).
            SettingRow(
                title = stringResource(R.string.achievements_title),
                description = stringResource(R.string.achievements_subtitle),
                onClick = { HapticsService.fire(HapticIntent.TAP); onAchievements() },
            )
            HorizontalDivider(color = p.surfaceBorder)
            Difficulty.entries.forEach { d ->
                Choice(stringResource(d.labelRes()), state.difficulty == d) {
                    HapticsService.fire(HapticIntent.TAP)
                    onDifficulty(d)
                }
            }
        }
        SettingGroup(stringResource(R.string.settings_theme_title)) {
            ThemePreference.entries.forEach { t ->
                Choice(stringResource(t.labelRes()), state.theme == t) {
                    HapticsService.fire(HapticIntent.TAP)
                    onTheme(t)
                }
            }
        }
        // Fires before flipping the flag (matches iOS's own SettingsToggleRow comment: "so
        // turning haptics OFF still confirms the tap; turning them on is confirmed by the
        // next interaction") — HapticsService.isEnabled is read synchronously off the value
        // in effect *before* this toggle's own write lands, the same way iOS's isEnabled
        // reads UserDefaults before its own binding setter writes the new value.
        SettingToggle(stringResource(R.string.settings_haptics_title), state.hapticsEnabled) { turningOn ->
            HapticsService.fire(HapticIntent.TAP)
            onHaptics(turningOn)
        }
        SettingToggle(stringResource(R.string.settings_notifications_title), state.remindersEnabled) { turningOn ->
            HapticsService.fire(HapticIntent.TAP)
            if (turningOn) requestPermission() else onDisableReminders()
        }
        SettingGroup(stringResource(R.string.settings_section_about)) {
            // Opens the Play Store listing directly — the standard manual "rate the app"
            // entry, and ReviewFlow's "persistent review link": `market://details` pinned to
            // the Play Store app, falling back to the web listing. Deliberately separate from
            // the win-triggered review invitation (`AppContainer.reviews`, presented by
            // `MainActivity`), which ReviewFlow's cooldown and per-version policy gate; this
            // row is always available, matching iOS's `AppStoreReviewLink` Settings row.
            SettingRow(
                title = stringResource(R.string.settings_review_title),
                description = stringResource(R.string.settings_review_description),
                onClick = {
                    HapticsService.fire(HapticIntent.TAP)
                    AnalyticsService.log(AnalyticsEvent.ReviewLinkOpened(source = "settings"))
                    context.openPlayStoreReviewPage()
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_whats_new_title),
                description = stringResource(R.string.settings_whats_new_description),
                onClick = {
                    HapticsService.fire(HapticIntent.TAP)
                    AnalyticsService.log(
                        AnalyticsEvent.WhatsNewOpenedFromSettings(version = com.ezequielbrrt.domemory.BuildConfig.VERSION_NAME),
                    )
                    onWhatsNew()
                },
            )
        }
    }
}

private fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.VERY_HARD -> R.string.difficulty_very_hard
}

private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.SYSTEM -> R.string.theme_system
    ThemePreference.LIGHT -> R.string.theme_light
    ThemePreference.DARK -> R.string.theme_dark
}
@Composable private fun SettingGroup(title: String, content: @Composable () -> Unit) { val p = LocalPalette.current; Column(Modifier.fillMaxWidth().background(p.surfacePrimary, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.Bold, color = p.textSecondary); content() } }
@Composable private fun Choice(label: String, selected: Boolean, click: () -> Unit) { val p = LocalPalette.current; Text(label, color = if (selected) p.primary else p.textPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 6.dp)) }
@Composable private fun SettingToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) { val p = LocalPalette.current; Row(Modifier.fillMaxWidth().background(p.surfacePrimary, RoundedCornerShape(16.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, fontWeight = FontWeight.SemiBold, color = p.textPrimary); Switch(checked = checked, onCheckedChange = change) } }

/** A tappable info row — the "Rate DoMemory" / "What's New" shape: a title plus a
 * secondary description line, no switch. */
@Composable private fun SettingRow(title: String, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
        Text(description, color = p.textSecondary, fontSize = 13.sp)
    }
}
