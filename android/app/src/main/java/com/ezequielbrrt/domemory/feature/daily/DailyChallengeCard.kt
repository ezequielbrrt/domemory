package com.ezequielbrrt.domemory.feature.daily

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.components.CompactCardLayout
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/**
 * The menu's Daily Challenge entry point (spec 8, 9.1). Two layouts, matching
 * `MenuView.swift`'s own branch exactly (lines 149-177): full-width and primary-coloured
 * with an icon circle, title, subtitle/streak text and a trailing badge/play glyph
 * (`DailyChallengeCard.swift`-equivalent, `MenuView.swift:657-710`) when no season is
 * active; the shared [CompactCardLayout] (icon-on-top, single combined badge line —
 * `CompactDailyChallengeCard`, `MenuView.swift:585-612`) when a season shares the row.
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
    compact: Boolean = false,
) {
    val palette = LocalPalette.current
    val cardModifier = modifier
        .fillMaxWidth()
        .clickable(enabled = !isCompletedToday, onClick = onClick)
        .alpha(if (isCompletedToday) 0.85f else 1f)

    if (compact) {
        CompactCardLayout(
            icon = {
                Icon(
                    if (isCompletedToday) Icons.Filled.Check else Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            },
            title = stringResource(R.string.daily_challenge_title),
            background = palette.primary,
            badge = {
                Text(
                    dailyChallengeBadgeText(streak, isCompletedToday),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            },
            modifier = cardModifier,
            artworkRes = R.drawable.daily_challenge_card,
        )
    } else {
        Box(
            cardModifier
                .clip(RoundedCornerShape(18.dp))
                .background(palette.primary),
        ) {
            // Sized the same way as `CompactCardLayout`: `matchParentSize` takes the
            // card's measured size without influencing it, so the filled artwork crops to
            // the card rather than stretching it.
            Image(
                painter = painterResource(R.drawable.daily_challenge_card),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // A heavier scrim than the compact card's. This layout is roughly four times
            // as wide as it is tall, so filling it from 4:3 art crops a narrow band from
            // the middle and scales the motif up — and the title, subtitle and streak
            // badge all sit on top of it in unshadowed white.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                palette.primary.copy(alpha = 0.92f),
                                palette.primary.copy(alpha = 0.35f),
                            ),
                        ),
                    ),
            )
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isCompletedToday) Icons.Filled.Check else Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.daily_challenge_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = DoMemoryType.display(17),
                    )
                    Text(
                        text = if (isCompletedToday) {
                            stringResource(R.string.daily_challenge_completed)
                        } else {
                            stringResource(R.string.daily_challenge_subtitle)
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (streak > 0) {
                    Box(
                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            stringResource(R.string.daily_challenge_streak_format, streak),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                        )
                    }
                } else if (!isCompletedToday) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

/** The compact badge's single combined line — streak takes precedence over the completed
 * state, which takes precedence over the plain subtitle, matching
 * `CompactDailyChallengeCard.badgeText` (`MenuView.swift:590-593`) exactly. */
@Composable
private fun dailyChallengeBadgeText(streak: Int, isCompletedToday: Boolean): String = when {
    streak > 0 -> stringResource(R.string.daily_challenge_streak_format, streak)
    isCompletedToday -> stringResource(R.string.daily_challenge_completed)
    else -> stringResource(R.string.daily_challenge_subtitle)
}
