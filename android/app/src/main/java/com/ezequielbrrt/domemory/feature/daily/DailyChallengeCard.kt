package com.ezequielbrrt.domemory.feature.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/**
 * The menu's Daily Challenge entry point (spec 8, 9.1). Shares a row with the season card
 * when one is active (shrinking to fit); full-width otherwise.
 *
 * Locked-for-today is shown as a state on this same card rather than a separate screen —
 * there is no result/win-lose UI yet to send the player to (Phase 3 leaves that a
 * placeholder), and tapping while completed is a no-op, mirroring the `domemory://daily`
 * deep link's own "no-op if already completed" rule (spec 11.1).
 */
@Composable
fun DailyChallengeCard(
    streak: Int,
    isCompletedToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceSecondary)
            .clickable(enabled = !isCompletedToday, onClick = onClick)
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.daily_challenge_title),
            color = palette.textPrimary,
            fontWeight = FontWeight.Bold,
            style = DoMemoryType.display(16),
        )
        Text(
            text = if (isCompletedToday) {
                stringResource(R.string.daily_challenge_completed)
            } else if (streak > 0) {
                stringResource(R.string.daily_challenge_streak_format, streak)
            } else {
                stringResource(R.string.daily_challenge_subtitle)
            },
            color = palette.textSecondary,
            fontSize = 12.sp,
        )
    }
}
