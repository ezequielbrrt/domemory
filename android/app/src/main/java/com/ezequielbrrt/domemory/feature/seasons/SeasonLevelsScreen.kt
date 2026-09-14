package com.ezequielbrrt.domemory.feature.seasons

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.seasons.Season
import com.ezequielbrrt.domemory.services.seasons.SeasonLevelProgressStore
import com.ezequielbrrt.domemory.services.seasons.SeasonLocaleResolver
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import java.util.Locale

/**
 * The shared level map under a season header (spec 9.1): icon, title, a `cleared / total`
 * progress bar that eases with a spring rather than snapping, the star total and a
 * days-left countdown. Once every level is cleared the header switches to a completion
 * state instead of leaving the player looking at a wall of tiles.
 *
 * Finite by construction — the grid runs `1..season.levelCount` with no paging, unlike the
 * endless map's ever-growing list.
 */
@Composable
fun SeasonLevelsScreen(
    season: Season,
    store: SeasonLevelProgressStore,
    todayKey: String,
    onLevelSelected: (Int) -> Unit,
) {
    val palette = LocalPalette.current
    val progress = store.progress
    val revision by progress.revision.collectAsState()

    var totalStars by remember(season.id) { mutableIntStateOf(0) }
    LaunchedEffect(season.id, revision) {
        totalStars = progress.totalStars(season.levelCount)
    }

    val languageTag = remember { Locale.getDefault().toLanguageTag() }
    val resolved = remember(season, languageTag) { SeasonLocaleResolver.resolveText(season, languageTag) }
    val title = resolved?.first ?: stringResource(R.string.season_fallback_title)
    val subtitle = resolved?.second.orEmpty()
    val accentColor = season.parsedAccentColor() ?: palette.primary
    val daysRemaining = remember(season, todayKey) { season.daysRemaining(todayKey) }

    val isDark = isSystemInDarkTheme()
    val backgroundUrl = remember(season, isDark) {
        (if (isDark) season.backgroundImageURLDark else null) ?: season.backgroundImageURL
    }
    val headerTextColor = if (backgroundUrl != null) androidx.compose.ui.graphics.Color.White else palette.textPrimary

    val clearedCount = progress.clearedLevelCount(season.levelCount)
    val isComplete = progress.isComplete(season.levelCount)
    val fraction = if (season.levelCount > 0) clearedCount.toFloat() / season.levelCount else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        // "Eases to its new width with a spring rather than snapping" (spec 9.1).
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "seasonProgress",
    )
    val progressDescription = stringResource(R.string.season_progress_accessibility_format, clearedCount, season.levelCount)

    Column(Modifier.fillMaxSize().background(palette.appBackground)) {
        Box(Modifier.fillMaxWidth()) {
            backgroundUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(season.icon, fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(title, style = DoMemoryType.display(22), color = headerTextColor)
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = headerTextColor.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (isComplete) {
                    Text(
                        stringResource(R.string.season_complete_title),
                        style = DoMemoryType.display(16),
                        color = headerTextColor,
                    )
                    Text(
                        stringResource(R.string.season_complete_message),
                        color = headerTextColor.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(palette.surfaceSecondary, RoundedCornerShape(999.dp))
                            .semantics { contentDescription = progressDescription },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(animatedFraction)
                                .height(10.dp)
                                .background(accentColor, RoundedCornerShape(999.dp)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.season_progress_format, clearedCount, season.levelCount),
                            color = headerTextColor,
                            fontSize = 13.sp,
                        )
                        Text("★ $totalStars", color = palette.hardAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                daysRemaining?.let { days ->
                    Spacer(Modifier.height(4.dp))
                    Text(seasonCountdownLabel(days), color = headerTextColor.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items((1..season.levelCount).toList()) { level ->
                val unlocked = store.isUnlocked(level)
                val stars = store.stars(level)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (unlocked) palette.surfacePrimary else palette.surfaceSecondary, RoundedCornerShape(16.dp))
                        .clickable(enabled = unlocked) { onLevelSelected(level) }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (unlocked) level.toString() else "🔒",
                        fontWeight = FontWeight.Bold,
                        color = if (unlocked) accentColor else palette.textSecondary,
                    )
                    Text(if (stars > 0) "★".repeat(stars) else "", fontSize = 11.sp, color = palette.hardAmber)
                }
            }
        }
    }
}
