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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.feature.notifications.rememberNotificationPermissionRequester
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
) {
    BackHandler(onBack = onBack)
    val p = LocalPalette.current
    // Same "one path" discipline as the primer (feature/notifications/NotificationPrimerDialog.kt):
    // turning the toggle on requests the OS permission first (a no-op below API 33) and only
    // calls onEnableReminders — which flips notificationsEnabled — once that resolves.
    // Denial leaves the flag untouched, matching spec 11.2's permission-sync rule.
    val requestPermission = rememberNotificationPermissionRequester(onGranted = onEnableReminders, onDenied = {})
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("‹  " + stringResource(R.string.settings_title), style = DoMemoryType.display(26), color = p.primary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        SettingGroup(stringResource(R.string.settings_section_game)) { Difficulty.entries.forEach { d -> Choice(stringResource(d.labelRes()), state.difficulty == d) { onDifficulty(d) } } }
        SettingGroup(stringResource(R.string.settings_theme_title)) { ThemePreference.entries.forEach { t -> Choice(stringResource(t.labelRes()), state.theme == t) { onTheme(t) } } }
        SettingToggle(stringResource(R.string.settings_haptics_title), state.hapticsEnabled, onHaptics)
        SettingToggle(stringResource(R.string.settings_notifications_title), state.remindersEnabled) { turningOn ->
            if (turningOn) requestPermission() else onDisableReminders()
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
