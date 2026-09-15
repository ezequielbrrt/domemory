package com.ezequielbrrt.domemory.feature.seasons

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.feature.levels.LevelTile
import com.ezequielbrrt.domemory.feature.levels.LivesPill
import com.ezequielbrrt.domemory.feature.levels.OutOfLivesModal
import com.ezequielbrrt.domemory.feature.levels.StarBalancePill
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.ads.findActivity
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.services.seasons.Season
import com.ezequielbrrt.domemory.services.seasons.SeasonLevelProgressStore
import com.ezequielbrrt.domemory.services.seasons.SeasonLocaleResolver
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The shared level map under a season header (spec 9.1): icon, title, a `cleared / total`
 * progress bar that eases with a spring rather than snapping, a days-left countdown, and —
 * matching `SeasonLevelsView.swift`'s trailing column exactly — the same daily-lives and
 * star-wallet pills the endless map shows, since both draw from the same shared budgets
 * (spec 7.4, 9.1). Tapping a tile with no lives left refuses the attempt and shows the same
 * [OutOfLivesModal] the endless map uses, via [SeasonLevelsViewModel].
 *
 * Finite by construction — the grid runs `1..season.levelCount` with no paging, unlike the
 * endless map's ever-growing list.
 */
@Composable
fun SeasonLevelsScreen(
    season: Season,
    store: SeasonLevelProgressStore,
    viewModel: SeasonLevelsViewModel,
    todayKey: String,
    onLevelSelected: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = store.progress
    val revision by progress.revision.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

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
    val hasBackgroundArtwork = backgroundUrl != null
    val headerTextColor = if (hasBackgroundArtwork) Color.White else palette.textPrimary

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
    val backAccessibilityLabel = stringResource(R.string.common_back)

    // Keep the artwork fixed behind the complete map, as LevelMapView does on iOS.
    // In particular, it must not be a short header-only image: the level grid scrolls
    // over the same full-screen artwork all the way to the bottom of the season.
    Box(Modifier.fillMaxSize().background(palette.appBackground)) {
        backgroundUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(Modifier.fillMaxSize()) {
            // Matches iOS's nav-bar toggle (`SeasonLevelsView.swift`'s
            // `.toolbarBackground(hasBackgroundArtwork ? .hidden : .visible, ...)`): opaque
            // over the flat background when a season carries no artwork, transparent (so the
            // image reaches the very top of the screen) when it does.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (hasBackgroundArtwork) Color.Transparent else palette.appBackground)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { HapticsService.fire(HapticIntent.TAP); onBack() },
                    modifier = Modifier.semantics { contentDescription = backAccessibilityLabel },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = headerTextColor)
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(season.icon, fontSize = 24.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                title,
                                style = DoMemoryType.display(20),
                                color = headerTextColor,
                                maxLines = 2,
                            )
                        }
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, color = headerTextColor.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.season_progress_format, clearedCount, season.levelCount),
                                color = accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                            )
                            if (daysRemaining != null && !isComplete) {
                                Text(
                                    seasonCountdownLabel(daysRemaining),
                                    color = headerTextColor.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // One daily budget and one wallet across endless Levels and every
                    // season (spec 9.1) — matches SeasonLevelsView.swift's trailing
                    // VStack of LivesRow + star-wallet pills exactly, including the same
                    // heart-break/refill and star-sparkle Lottie effects (`1dc9282`) the
                    // endless map's header plays.
                    Column(horizontalAlignment = Alignment.End) {
                        LivesPill(
                            uiState.livesRemaining,
                            effect = uiState.livesEffect,
                            onEffectFinished = viewModel::consumeLivesEffect,
                        )
                        Spacer(Modifier.height(6.dp))
                        StarBalancePill(
                            uiState.starBalance,
                            credited = uiState.starsCredited,
                            onCreditedFinished = viewModel::consumeStarsCredited,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (isComplete) {
                    SeasonCompletionBanner(accentColor)
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(palette.surfaceSecondary, RoundedCornerShape(999.dp))
                            .semantics { contentDescription = progressDescription },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(animatedFraction)
                                .height(8.dp)
                                .background(accentColor, RoundedCornerShape(999.dp)),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items((1..season.levelCount).toList()) { level ->
                    val unlocked = store.isUnlocked(level)
                    val stars = store.stars(level)
                    LevelTile(
                        level = level,
                        unlocked = unlocked,
                        // Seasonal progress has no current-level concept, unlike the endless
                        // map, so its nodes use the cleared/locked variants without a pulse.
                        isCurrent = false,
                        stars = stars,
                        onClick = {
                            coroutineScope.launch {
                                if (viewModel.attemptStart(level)) onLevelSelected(level)
                            }
                        },
                    )
                }
            }
        }

        if (uiState.showOutOfLivesPrompt) {
            LaunchedEffect(Unit) {
                if (AdsService.isRewardedConfigured(AdPlacement.LEVELS_REWARDED_LIFE)) {
                    context.applicationContext.let { AdsService.loadRewarded(it, AdPlacement.LEVELS_REWARDED_LIFE) }
                }
            }
            OutOfLivesModal(
                starBalance = uiState.starBalance,
                canWatchAd = AdsService.isRewardedConfigured(AdPlacement.LEVELS_REWARDED_LIFE),
                onWatchAd = {
                    HapticsService.fire(HapticIntent.TAP)
                    AdsService.showRewarded(
                        activity = activity,
                        placement = AdPlacement.LEVELS_REWARDED_LIFE,
                        onReward = { viewModel.applyLifeRewardFromAd() },
                    )
                },
                onBuyWithStars = viewModel::buyLifeWithStars,
                onDismiss = { HapticsService.fire(HapticIntent.TAP); viewModel.dismissOutOfLivesPrompt() },
            )
        }
    }
}

/** A cleared season needs somewhere to land — mirrors `SeasonLevelsView.swift`'s
 * `completionBanner`: a tinted, bordered card with a seal-checkmark icon rather than the
 * two bare lines of text this used before. */
@Composable
private fun SeasonCompletionBanner(accentColor: Color) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = accentColor, modifier = Modifier.size(26.dp))
        Column {
            Text(
                stringResource(R.string.season_complete_title),
                style = DoMemoryType.display(16),
                color = palette.textPrimary,
            )
            Text(
                stringResource(R.string.season_complete_message),
                color = palette.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
