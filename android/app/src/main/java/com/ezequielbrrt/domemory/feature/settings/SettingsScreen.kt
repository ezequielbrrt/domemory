package com.ezequielbrrt.domemory.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.feature.notifications.rememberNotificationPermissionRequester
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.services.share.PlayStoreLinks
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import com.ezequielbrrt.domemory.ui.theme.ThemePreference

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
    // Same "one path" discipline as the primer (feature/notifications/NotificationPrimerDialog.kt):
    // turning the toggle on requests the OS permission first (a no-op below API 33) and only
    // calls onEnableReminders — which flips notificationsEnabled — once that resolves.
    // Denial leaves the flag untouched, matching spec 11.2's permission-sync rule.
    val requestPermission = rememberNotificationPermissionRequester(onGranted = onEnableReminders, onDenied = {})
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("‹  " + stringResource(R.string.settings_title), style = DoMemoryType.display(26), color = p.primary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        SettingGroup(stringResource(R.string.settings_section_game)) {
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
            SettingRow(
                title = stringResource(R.string.achievements_title),
                description = stringResource(R.string.achievements_subtitle),
                onClick = { HapticsService.fire(HapticIntent.TAP); onAchievements() },
            )
            // Opens the Play Store listing directly — the standard manual "rate the app"
            // entry. This is deliberately separate from the win-triggered Play In-App Review
            // prompt (AppReviews.recordSuccessfulGameWin, fired from GameViewModel on a real
            // win): that flow cannot be launched on demand by design — Google's ReviewManager
            // API has no "show the dialog now" call, only "request a flow, which Play Core
            // may or may not actually present" — so a manual button can only ever be this
            // storefront link, never a way to force the in-app prompt open.
            SettingRow(
                title = stringResource(R.string.settings_review_title),
                description = stringResource(R.string.settings_review_description),
                onClick = { HapticsService.fire(HapticIntent.TAP); openPlayStoreListing(context) },
            )
            SettingRow(
                title = stringResource(R.string.settings_whats_new_title),
                description = stringResource(R.string.settings_whats_new_description),
                onClick = { HapticsService.fire(HapticIntent.TAP); onWhatsNew() },
            )
        }
    }
}

/** `market://details` routes straight into the Play Store app when it's installed (the
 * common case); `setPackage` pins the intent to Play Store specifically so no other app
 * that happens to claim the `market` scheme can intercept it. Falls back to the plain
 * `https://play.google.com` listing URL — resolvable by any browser — when the Play Store
 * app can't handle it at all (an emulator with no Play Store image, mirrors this repo's own
 * `Pixel_10` note about `google_apis_playstore` vs plain `google_apis` images). */
private fun openPlayStoreListing(context: Context) {
    val appId = context.packageName
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId")).apply {
        setPackage("com.android.vending")
    }
    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PlayStoreLinks.listingUrl(context))))
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
