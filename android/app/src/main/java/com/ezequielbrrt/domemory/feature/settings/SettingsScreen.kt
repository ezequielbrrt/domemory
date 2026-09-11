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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import com.ezequielbrrt.domemory.ui.theme.ThemePreference

@Composable
fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onDifficulty: (Difficulty) -> Unit, onTheme: (ThemePreference) -> Unit, onHaptics: (Boolean) -> Unit, onReminders: (Boolean) -> Unit) {
    BackHandler(onBack = onBack)
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("‹  Settings", style = DoMemoryType.display(26), color = p.primary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        SettingGroup("Game") { Difficulty.entries.forEach { d -> Choice(d.name.lowercase().replaceFirstChar { it.uppercase() }, state.difficulty == d) { onDifficulty(d) } } }
        SettingGroup("Theme") { ThemePreference.entries.forEach { t -> Choice(t.name.lowercase().replaceFirstChar { it.uppercase() }, state.theme == t) { onTheme(t) } } }
        SettingToggle("Haptics", state.hapticsEnabled, onHaptics)
        SettingToggle("Reminders", state.remindersEnabled, onReminders)
    }
}
@Composable private fun SettingGroup(title: String, content: @Composable () -> Unit) { val p = LocalPalette.current; Column(Modifier.fillMaxWidth().background(p.surfacePrimary, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.Bold, color = p.textSecondary); content() } }
@Composable private fun Choice(label: String, selected: Boolean, click: () -> Unit) { val p = LocalPalette.current; Text(label, color = if (selected) p.primary else p.textPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 6.dp)) }
@Composable private fun SettingToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) { val p = LocalPalette.current; Row(Modifier.fillMaxWidth().background(p.surfacePrimary, RoundedCornerShape(16.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, fontWeight = FontWeight.SemiBold, color = p.textPrimary); Switch(checked = checked, onCheckedChange = change) } }
