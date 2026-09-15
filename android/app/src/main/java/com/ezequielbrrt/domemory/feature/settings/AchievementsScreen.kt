package com.ezequielbrrt.domemory.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.stats.Achievement
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlin.math.roundToInt

/**
 * Android counterpart of `ios/.../Modules/Settings/SettingsView/AchievementsView.swift`: a
 * "Your Stats" 2-column grid, then a "Badges" list, each badge showing locked/unlocked state
 * and a progress bar only while locked and `progress > 0` (matching iOS's exact rule). No
 * icon library exists in this codebase and none should be added for this — every badge and
 * the locked state are emoji, the same visual language the Daily Challenge streak already
 * uses (`daily_challenge_streak_format`'s 🔥).
 */
@Composable
fun AchievementsScreen(state: AchievementsUiState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val p = LocalPalette.current
    val winRateText = "${(state.stats.winRate * 100).roundToInt()}%"

    Column(
        Modifier
            .fillMaxSize()
            .background(p.appBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "‹  " + stringResource(R.string.achievements_title),
            style = DoMemoryType.display(26),
            color = p.primary,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
        )

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = p.primary)
            }
            return@Column
        }

        Text(
            stringResource(R.string.achievements_stats_section),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = p.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "${state.stats.totalPlayed}",
                    stringResource(R.string.stat_total_games),
                    p.primary,
                    Modifier.weight(1f),
                )
                StatCard(
                    "${state.stats.totalWon}",
                    stringResource(R.string.stat_total_wins),
                    p.easyGreen,
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(winRateText, stringResource(R.string.stat_win_rate), p.primary, Modifier.weight(1f))
                StatCard(
                    "${state.stats.perfectGames}",
                    stringResource(R.string.stat_perfect_games),
                    p.secondary,
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "${state.stats.longestStreak}",
                    stringResource(R.string.stat_longest_streak),
                    p.hardAmber,
                    Modifier.weight(1f),
                )
                StatCard(
                    "${state.stats.multiplayerWins}",
                    stringResource(R.string.stat_multiplayer_wins),
                    p.primary,
                    Modifier.weight(1f),
                )
            }
        }

        Text(
            stringResource(R.string.achievements_badges_section),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = p.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.achievements.forEach { achievement -> AchievementRow(achievement) }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .background(p.surfacePrimary, RoundedCornerShape(18.dp))
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = tint)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surfacePrimary, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(
                    if (achievement.isUnlocked) p.primary.copy(alpha = 0.15f) else p.textSecondary.copy(alpha = 0.12f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (achievement.isUnlocked) achievement.emoji else "🔒", fontSize = 19.sp)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = achievementTitle(achievement),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (achievement.isUnlocked) p.textPrimary else p.textSecondary,
            )
            Text(achievementDetail(achievement), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary)
            if (!achievement.isUnlocked && achievement.progress > 0f) {
                LinearProgressIndicator(
                    progress = { achievement.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = p.primary,
                )
            }
        }

        if (achievement.isUnlocked) {
            Text("✅", fontSize = 18.sp)
        }
    }
}

@Composable
private fun achievementTitle(achievement: Achievement): String =
    achievement.formatArg?.let { stringResource(achievement.titleRes, it) } ?: stringResource(achievement.titleRes)

@Composable
private fun achievementDetail(achievement: Achievement): String =
    achievement.formatArg?.let { stringResource(achievement.detailRes, it) } ?: stringResource(achievement.detailRes)
