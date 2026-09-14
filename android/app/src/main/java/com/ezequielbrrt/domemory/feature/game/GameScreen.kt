package com.ezequielbrrt.domemory.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.services.ads.AdMobBanner
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlin.math.ceil

/**
 * The gameplay screen. The board is laid out square-ish and never scrolls: columns =
 * ceil(sqrt(count)), rows = ceil(count / columns), and cards fill whatever space is
 * left (spec 3.5).
 *
 * The power-up bar and the lose-screen star purchases (spec 7.6, 7.7) only apply to
 * Levels/Seasons — [isLevel] and [starBalance] gate them off entirely for free play and
 * the Daily Challenge, whose callbacks are no-op defaults.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onChoose: (Int) -> Unit,
    onPauseToggle: () -> Unit,
    onQuit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isLevel: Boolean = false,
    starBalance: Int? = null,
    onBuyExtraTime: () -> Unit = {},
    onBuyPeek: () -> Unit = {},
    onBuyFreeze: () -> Unit = {},
    onBuyRevealPair: () -> Unit = {},
    onBuyLifeWithStars: () -> Unit = {},
    onForgiveMistakesWithStars: () -> Unit = {},
    onSkipLevelWithStars: () -> Unit = {},
    onWatchAdForLife: () -> Unit = {},
    onWatchAdToForgive: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val context = LocalContext.current

    // Drives the pie only. Repainting on frames is cheap; recomputing the model is not.
    var frameTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.showsPie, state.isPaused, state.isFinished) {
        while (state.showsPie && !state.isPaused && !state.isFinished) {
            withFrameMillis { }
            frameTime = System.currentTimeMillis()
        }
    }

    // Preloaded once per game instance so an ad is ready by the time it's actually eligible
    // (spec: iOS's `trackGameStarted` preloading). Self-heals on a cache miss regardless —
    // see `AdsService.notifyGameFinished`/`showRewarded`'s own load-on-miss fallback.
    LaunchedEffect(state.boardName, isLevel) {
        AdsService.loadInterstitial(context, AdPlacement.GAME_FINISHED_INTERSTITIAL)
        if (isLevel) {
            AdsService.loadRewarded(context, AdPlacement.LEVELS_REWARDED_LIFE)
            AdsService.loadRewarded(context, AdPlacement.LEVELS_REWARDED_FORGIVE)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.appBackground),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            GameHud(state = state, onPauseToggle = onPauseToggle, onQuit = onQuit)
            Spacer(Modifier.size(12.dp))
            BoardGrid(
                state = state,
                frameTime = frameTime,
                onChoose = onChoose,
                modifier = Modifier.weight(1f),
            )
            AdMobBanner(AdPlacement.GAME_BANNER, Modifier.fillMaxWidth())
            if (isLevel && starBalance != null && !state.isFinished) {
                Spacer(Modifier.size(12.dp))
                PowerUpBar(
                    starBalance = starBalance,
                    enabled = !state.isPaused,
                    onBuyExtraTime = onBuyExtraTime,
                    onBuyPeek = onBuyPeek,
                    onBuyFreeze = onBuyFreeze,
                    onBuyRevealPair = onBuyRevealPair,
                )
            }
        }

        state.outcome?.let { outcome ->
            OutcomeOverlay(
                outcome = outcome,
                state = state,
                isLevel = isLevel,
                starBalance = starBalance,
                onRetry = onRetry,
                onQuit = onQuit,
                onBuyLifeWithStars = onBuyLifeWithStars,
                onForgiveMistakesWithStars = onForgiveMistakesWithStars,
                onSkipLevelWithStars = onSkipLevelWithStars,
                canWatchAdForLife = isLevel && AdsService.isRewardedConfigured(AdPlacement.LEVELS_REWARDED_LIFE),
                canWatchAdToForgive = isLevel && AdsService.isRewardedConfigured(AdPlacement.LEVELS_REWARDED_FORGIVE),
                onWatchAdForLife = onWatchAdForLife,
                onWatchAdToForgive = onWatchAdToForgive,
            )
        }
    }
}

@Composable
private fun GameHud(state: GameUiState, onPauseToggle: () -> Unit, onQuit: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(
            label = stringResource(R.string.game_time_label),
            value = "${ceil(state.timeRemaining).toInt()}",
            tint = if (state.isFrozen) palette.freezeBlue else palette.primary,
        )
        Chip(
            label = stringResource(R.string.game_pairs_label),
            value = "${state.matchedPairs}/${state.totalPairs}",
            tint = palette.easyGreen,
        )
        Chip(
            label = stringResource(R.string.game_errors_label),
            value = state.maxFailures?.let { "${state.failedTries}/$it" }
                ?: "${state.failedTries}",
            tint = if (state.mistakesAreCritical) palette.secondary else palette.textSecondary,
        )
        TextButton(onClick = onPauseToggle) {
            Text(
                text = stringResource(R.string.game_pause_title),
                color = palette.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Chip(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = palette.textSecondary)
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

/** The power-up bar under the HUD (spec 7.6). No confirmation step: a purchase applies
 * immediately, so every button is a single tap. */
@Composable
private fun PowerUpBar(
    starBalance: Int,
    enabled: Boolean,
    onBuyExtraTime: () -> Unit,
    onBuyPeek: () -> Unit,
    onBuyFreeze: () -> Unit,
    onBuyRevealPair: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PowerUpButton(
            label = stringResource(R.string.levels_powerup_extra_time),
            cost = LevelPowerUp.EXTRA_TIME.cost,
            enabled = enabled && starBalance >= LevelPowerUp.EXTRA_TIME.cost,
            onClick = onBuyExtraTime,
            modifier = Modifier.weight(1f),
        )
        PowerUpButton(
            label = stringResource(R.string.levels_powerup_peek),
            cost = LevelPowerUp.PEEK.cost,
            enabled = enabled && starBalance >= LevelPowerUp.PEEK.cost,
            onClick = onBuyPeek,
            modifier = Modifier.weight(1f),
        )
        PowerUpButton(
            label = stringResource(R.string.levels_powerup_freeze),
            cost = LevelPowerUp.FREEZE.cost,
            enabled = enabled && starBalance >= LevelPowerUp.FREEZE.cost,
            onClick = onBuyFreeze,
            modifier = Modifier.weight(1f),
        )
        PowerUpButton(
            label = stringResource(R.string.levels_powerup_reveal_pair),
            cost = LevelPowerUp.REVEAL_PAIR.cost,
            enabled = enabled && starBalance >= LevelPowerUp.REVEAL_PAIR.cost,
            onClick = onBuyRevealPair,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.size(2.dp))
    Text(
        stringResource(R.string.levels_star_balance_format, starBalance),
        fontSize = 11.sp,
        color = palette.textSecondary,
    )
}

@Composable
private fun PowerUpButton(
    label: String,
    cost: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .background(
                if (enabled) palette.surfacePrimary else palette.surfaceSecondary,
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text("★$cost", fontSize = 11.sp, color = palette.hardAmber)
    }
}

@Composable
private fun BoardGrid(
    state: GameUiState,
    frameTime: Long,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = state.columns.coerceAtLeast(1)
    val rows = state.cards.chunked(columns)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { card ->
                    CardView(
                        card = card,
                        isHidden = card.id in state.hiddenCardIds,
                        showsPie = state.showsPie,
                        pieFraction = card.bonusRemainingFraction(frameTime),
                        onClick = { onChoose(card.id) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OutcomeOverlay(
    outcome: GameOutcome,
    state: GameUiState,
    isLevel: Boolean,
    starBalance: Int?,
    onRetry: () -> Unit,
    onQuit: () -> Unit,
    onBuyLifeWithStars: () -> Unit,
    onForgiveMistakesWithStars: () -> Unit,
    onSkipLevelWithStars: () -> Unit,
    canWatchAdForLife: Boolean = false,
    canWatchAdToForgive: Boolean = false,
    onWatchAdForLife: () -> Unit = {},
    onWatchAdToForgive: () -> Unit = {},
) {
    val palette = LocalPalette.current
    var showSkipConfirm by remember { mutableStateOf(false) }
    val won = outcome is GameOutcome.Won
    val lostToMistakes = outcome is GameOutcome.Lost && outcome.reason == LoseReason.TOO_MANY_MISTAKES

    Box(
        Modifier.fillMaxSize().background(palette.overlayBackdrop),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.surfacePrimary,
            modifier = Modifier
                .padding(24.dp)
                .border(1.dp, palette.surfaceBorder, RoundedCornerShape(16.dp)),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        when {
                            won -> R.string.game_win_title
                            lostToMistakes -> R.string.levels_lose_too_many_mistakes
                            else -> R.string.game_lose_message
                        },
                    ),
                    style = DoMemoryType.display(24),
                    color = palette.textPrimary,
                )
                if (won) {
                    Text(
                        text = stringResource(R.string.game_win_description),
                        color = palette.textSecondary,
                    )
                }
                Text(
                    text = "${stringResource(R.string.game_errors_label)}: ${state.failedTries}",
                    color = palette.textSecondary,
                )

                // Lose-screen star purchases (spec 7.7) — Levels/Seasons only.
                if (!won && isLevel && starBalance != null) {
                    if (lostToMistakes && canWatchAdToForgive) {
                        Button(
                            onClick = onWatchAdToForgive,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        ) {
                            Text(stringResource(R.string.levels_forgive_ad_format, LevelPowerUp.FORGIVE_AMOUNT))
                        }
                    }
                    if (lostToMistakes && starBalance >= LevelPowerUp.FORGIVE_COST) {
                        Button(
                            onClick = onForgiveMistakesWithStars,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.hardAmber),
                        ) {
                            Text(
                                stringResource(
                                    R.string.levels_forgive_stars_format,
                                    LevelPowerUp.FORGIVE_AMOUNT,
                                    LevelPowerUp.FORGIVE_COST,
                                ),
                            )
                        }
                    }
                    if (canWatchAdForLife) {
                        Button(
                            onClick = onWatchAdForLife,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        ) {
                            Text(stringResource(R.string.levels_watch_ad_for_life))
                        }
                    }
                    if (starBalance >= LevelPowerUp.LIFE_COST) {
                        Button(
                            onClick = onBuyLifeWithStars,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.hardAmber),
                        ) {
                            Text(stringResource(R.string.levels_buy_life_format, LevelPowerUp.LIFE_COST))
                        }
                    }
                    if (starBalance >= LevelPowerUp.SKIP_LEVEL_COST) {
                        TextButton(onClick = { showSkipConfirm = true }) {
                            Text(
                                stringResource(R.string.levels_skip_level_format, LevelPowerUp.SKIP_LEVEL_COST),
                                color = palette.secondary,
                            )
                        }
                    }
                }

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) {
                    Text(stringResource(R.string.game_try_again))
                }
                TextButton(onClick = onQuit) {
                    Text(
                        text = stringResource(R.string.game_go_to_menu),
                        color = palette.textSecondary,
                    )
                }
            }
        }
    }

    // Skip level requires a confirmation dialog (spec 7.7) — every other power-up and
    // lose-screen purchase deliberately does not.
    if (showSkipConfirm) {
        AlertDialog(
            onDismissRequest = { showSkipConfirm = false },
            title = { Text(stringResource(R.string.levels_skip_level_confirm_title)) },
            text = { Text(stringResource(R.string.levels_skip_level_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSkipConfirm = false
                    onSkipLevelWithStars()
                }) {
                    Text(stringResource(R.string.levels_skip_level_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
