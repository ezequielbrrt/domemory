package com.ezequielbrrt.domemory.feature.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.ads.findActivity
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/**
 * Endless procedural level map (spec 7.8-7.9). Tiles come straight from
 * [LevelsViewModel]'s underlying [com.ezequielbrrt.domemory.services.levels.LevelProgressService];
 * lives/star display and the out-of-lives gate come from the view model's [LevelsViewModel.UiState].
 */
@Composable
fun LevelsScreen(viewModel: LevelsViewModel, onLevelSelected: (Int) -> Unit) {
    // Recomposes the tile grid whenever cached progress changes (spec 2's fix: stars
    // and unlocks now arrive as a StateFlow, not a manually-invalidated cache).
    viewModel.progressRevision.collectAsState().value
    val uiState by viewModel.uiState.collectAsState()
    val palette = LocalPalette.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val highest = viewModel.highestUnlockedLevel
    val levels = (1..(highest + 20)).toList()

    Box(Modifier.fillMaxSize().background(palette.appBackground)) {
        Column(Modifier.fillMaxSize()) {
            LevelsHeader(
                currentLevel = highest,
                livesRemaining = uiState.livesRemaining,
                starBalance = uiState.starBalance,
                onInfoClick = viewModel::presentIntro,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(levels) { level ->
                    val unlocked = viewModel.isUnlocked(level)
                    val stars = viewModel.stars(level)
                    val isCurrent = level == highest
                    LevelTile(
                        level = level,
                        unlocked = unlocked,
                        isCurrent = isCurrent,
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
                    AdsService.showRewarded(
                        activity = activity,
                        placement = AdPlacement.LEVELS_REWARDED_LIFE,
                        onReward = { viewModel.applyLifeRewardFromAd() },
                    )
                },
                onBuyWithStars = viewModel::buyLifeWithStars,
                onDismiss = viewModel::dismissOutOfLivesPrompt,
            )
        }

        if (uiState.showIntro) {
            LevelsIntroDialog(onDismiss = viewModel::dismissIntro)
        }
    }
}

@Composable
private fun LevelsHeader(
    currentLevel: Int,
    livesRemaining: Int,
    starBalance: Int,
    onInfoClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.levels_screen_title),
                    style = DoMemoryType.display(20),
                    color = palette.textPrimary,
                )
                IconButton(onClick = onInfoClick) {
                    Text("?", fontWeight = FontWeight.Bold, color = palette.textSecondary)
                }
            }
            Text(
                stringResource(R.string.levels_current_level_format, currentLevel),
                fontSize = 13.sp,
                color = palette.textSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Pill(text = stringResource(R.string.levels_lives_remaining_format, livesRemaining, 4))
            Spacer(Modifier.size(6.dp))
            Pill(text = "★ $starBalance")
        }
    }
}

@Composable
private fun Pill(text: String) {
    val palette = LocalPalette.current
    Surface(
        shape = RoundedCornerShape(50),
        color = palette.surfacePrimary,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun LevelTile(
    level: Int,
    unlocked: Boolean,
    isCurrent: Boolean,
    stars: Int,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val background = when {
        isCurrent -> palette.primary
        unlocked -> palette.surfacePrimary
        else -> palette.surfaceSecondary
    }
    val foreground = when {
        isCurrent -> palette.surfacePrimary
        unlocked -> palette.primary
        else -> palette.textSecondary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(16.dp))
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (unlocked) level.toString() else "🔒",
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
        Text(
            if (stars > 0) "★".repeat(stars) else "",
            fontSize = 11.sp,
            color = palette.hardAmber,
        )
    }
}

/** Shown from the level map when tapping a tile with no lives left today (spec 7.4). */
@Composable
private fun OutOfLivesModal(
    starBalance: Int,
    canWatchAd: Boolean,
    onWatchAd: () -> Unit,
    onBuyWithStars: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val canBuyWithStars = starBalance >= LevelPowerUp.LIFE_COST
    Box(Modifier.fillMaxSize().background(palette.overlayBackdrop), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = palette.surfacePrimary,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder),
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("💔", fontSize = 40.sp)
                Text(
                    stringResource(R.string.levels_out_of_lives_title),
                    style = DoMemoryType.display(20),
                    color = palette.textPrimary,
                )
                Text(
                    // Message text mirrors whether a rewarded-ad refill is actually on offer
                    // (spec 7.4) rather than always assuming the star-only wording.
                    stringResource(
                        if (canWatchAd) R.string.levels_out_of_lives_message
                        else R.string.levels_out_of_lives_message_no_ad,
                    ),
                    color = palette.textSecondary,
                )
                if (canWatchAd) {
                    Button(
                        onClick = onWatchAd,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    ) {
                        Text(stringResource(R.string.levels_watch_ad_for_life))
                    }
                }
                if (canBuyWithStars) {
                    Button(
                        onClick = onBuyWithStars,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.hardAmber),
                    ) {
                        Text(stringResource(R.string.levels_buy_life_format, LevelPowerUp.LIFE_COST))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel), color = palette.primary)
                }
            }
        }
    }
}

/** The 4-slide one-shot intro carousel (spec 7.9), reachable again from the map's info
 * button. A simple stacked layout today rather than a swipeable pager — the paging
 * gesture and slide transitions are presentation polish tracked separately from the
 * one-shot gating logic this exists to exercise. */
@Composable
private fun LevelsIntroDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().background(palette.overlayBackdrop), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = palette.surfacePrimary,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder),
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IntroSlideText(
                    stringResource(R.string.levels_intro_progress_title),
                    stringResource(R.string.levels_intro_progress_subtitle),
                )
                IntroSlideText(
                    stringResource(R.string.levels_intro_stars_title),
                    stringResource(R.string.levels_intro_stars_subtitle),
                )
                IntroSlideText(
                    stringResource(R.string.levels_intro_lives_title),
                    stringResource(R.string.levels_intro_lives_subtitle, 4),
                )
                IntroSlideText(
                    stringResource(R.string.levels_intro_mistakes_title),
                    stringResource(R.string.levels_intro_mistakes_subtitle),
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) {
                    Text(stringResource(R.string.levels_intro_done))
                }
            }
        }
    }
}

@Composable
private fun IntroSlideText(title: String, subtitle: String) {
    val palette = LocalPalette.current
    Column {
        Text(title, style = DoMemoryType.display(16), color = palette.textPrimary)
        Text(subtitle, fontSize = 13.sp, color = palette.textSecondary)
    }
}
