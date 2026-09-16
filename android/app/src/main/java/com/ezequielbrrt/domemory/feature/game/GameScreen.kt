package com.ezequielbrrt.domemory.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.feature.levels.LivesEffect
import com.ezequielbrrt.domemory.feature.share.ResultShareData
import com.ezequielbrrt.domemory.feature.share.ShareResultCardView
import com.ezequielbrrt.domemory.feature.share.shareResultCard
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelPowerUp
import com.ezequielbrrt.domemory.services.ads.AdMobBanner
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.ui.anim.NumericTransition
import com.ezequielbrrt.domemory.ui.anim.rememberReduceMotion
import com.ezequielbrrt.domemory.ui.components.LivesRow
import com.ezequielbrrt.domemory.ui.lottie.BundledLottie
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /** Fired by the in-HUD quit button once the player confirms, i.e. abandoning a game
     * that hasn't reached an outcome yet. Unlike [onQuit] (which the Levels/Seasons win
     * and lose screens use, and which commits that outcome first), this must never spend
     * a life, touch stats or lifetime stars, or log a finish — quitting mid-game is a pure
     * abandon (spec §3.6, matching iOS's `tapOnExit()`). Defaults to [onQuit] because for
     * free play and the Daily Challenge, "leave" already has no side effects either way. */
    onQuitDuringPlay: () -> Unit = onQuit,
    onNextLevel: () -> Unit = onQuit,
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
    /** Mirrors [onWatchAdForHint]'s shape: the callback always runs after the ad closes,
     * which is what drives the lose screen's loading state (spec: iOS's
     * `isRewardedAdInProgress`, disabling the button and swapping its label while the ad
     * is in flight). */
    onWatchAdForLife: (onFinished: () -> Unit) -> Unit = { onFinished -> onFinished() },
    onWatchAdToForgive: (onFinished: () -> Unit) -> Unit = { onFinished -> onFinished() },
    /** Starts the pause-sheet rewarded hint. The callback always runs after the ad closes. */
    onWatchAdForHint: (onFinished: () -> Unit) -> Unit = { onFinished -> onFinished() },
    /** True only for the Daily Challenge (spec: the share card's title and streak row
     * depend on this — see `feature/share/ShareResultCard.kt`). */
    isDailyChallenge: Boolean = false,
    /** The Daily Challenge's current streak at the moment of this win, for the share
     * card's streak row (shown only when [isDailyChallenge] and this is > 0). Irrelevant,
     * and left at its default, for every other mode. */
    dailyStreak: Int = 0,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var isHintAdInProgress by remember { mutableStateOf(false) }
    var showQuitConfirm by remember { mutableStateOf(false) }

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
        AdsService.loadRewarded(context, AdPlacement.GAME_REWARDED_HINT)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.appBackground),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            GameHud(
                state = state,
                onPauseToggle = onPauseToggle,
                onQuitTapped = {
                    // Pause first so the timer can't run out from underneath the
                    // confirmation dialog (spec §3.6, matching iOS's `tapOnQuitPrompt()`
                    // stopping the timer before it ever shows the modal).
                    onPauseToggle()
                    showQuitConfirm = true
                },
            )
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
                isDailyChallenge = isDailyChallenge,
                dailyStreak = dailyStreak,
                onRetry = onRetry,
                onQuit = onQuit,
                onNextLevel = onNextLevel,
                onBuyLifeWithStars = onBuyLifeWithStars,
                onForgiveMistakesWithStars = onForgiveMistakesWithStars,
                onSkipLevelWithStars = onSkipLevelWithStars,
                canWatchAdForLife = isLevel && AdsService.isRewardedConfigured(AdPlacement.LEVELS_REWARDED_LIFE),
                canWatchAdToForgive = isLevel && AdsService.isRewardedConfigured(AdPlacement.LEVELS_REWARDED_FORGIVE),
                onWatchAdForLife = onWatchAdForLife,
                onWatchAdToForgive = onWatchAdToForgive,
            )
        }

        // Suppressed while the quit dialog is up — that dialog paused the game via the
        // same `onPauseToggle`, and showing both at once would stack an unrelated pause
        // sheet behind the confirmation.
        if (state.isPaused && state.outcome == null && !showQuitConfirm) {
            PauseOverlay(
                showRewardedHint = AdsService.isRewardedConfigured(AdPlacement.GAME_REWARDED_HINT),
                isRewardedHintInProgress = isHintAdInProgress,
                showRetry = !isDailyChallenge,
                onRewardedHint = {
                    isHintAdInProgress = true
                    onWatchAdForHint { isHintAdInProgress = false }
                },
                onRetry = onRetry,
                onContinue = onPauseToggle,
            )
        }

        // Android counterpart to iOS's QuitModal (spec §3.6): cancel resumes the game
        // exactly where it left off, confirm abandons it with no stats/lives/win-loss
        // side effect — see `onQuitDuringPlay`'s doc above.
        if (showQuitConfirm) {
            AlertDialog(
                onDismissRequest = { showQuitConfirm = false; onPauseToggle() },
                title = { Text(stringResource(R.string.game_quit_confirmation)) },
                confirmButton = {
                    // Deliberately leaves `showQuitConfirm` true: `onQuitDuringPlay` pops
                    // the back stack, which unmounts this screen a frame later rather than
                    // immediately. Clearing the flag here left `isPaused` (still true from
                    // this dialog's own pause) as the only condition guarding the pause
                    // sheet, so it flashed on screen for that one frame during the exit
                    // transition. Leaving the dialog's own condition true keeps it — not
                    // the pause sheet — showing until the screen is actually gone.
                    TextButton(onClick = { onQuitDuringPlay() }) {
                        Text(stringResource(R.string.common_accept))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuitConfirm = false; onPauseToggle() }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

/** Android counterpart to iOS's PauseModal: hint, retry and continue in a focused card. */
@Composable
private fun PauseOverlay(
    showRewardedHint: Boolean,
    isRewardedHintInProgress: Boolean,
    showRetry: Boolean,
    onRewardedHint: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val palette = LocalPalette.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.overlayBackdrop)
            // A plain background doesn't consume touches in Compose — without a click
            // handler here, taps at the same screen position as the HUD's "×" quit
            // button or "Pause" toggle (drawn earlier, and so *behind* this overlay only
            // visually) would fall straight through to them. That's exactly the case the
            // new quit-confirmation flow's own pause-first guarantee (spec §3.6) depends
            // on holding: reaching the HUD's quit button while already paused must not be
            // able to silently toggle the game back to running underneath the dialog.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 32.dp),
            shape = RoundedCornerShape(28.dp),
            color = palette.surfacePrimary,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🧐", fontSize = 64.sp)
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.game_pause_title),
                    style = DoMemoryType.display(36),
                    color = palette.primary,
                )
                Spacer(Modifier.size(28.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showRewardedHint) {
                        PauseActionButton(
                            text = stringResource(
                                if (isRewardedHintInProgress) R.string.ads_loading else R.string.game_rewarded_hint,
                            ),
                            color = palette.hardAmber,
                            enabled = !isRewardedHintInProgress,
                            onClick = onRewardedHint,
                        )
                    }
                    if (showRetry) {
                        PauseActionButton(
                            text = stringResource(R.string.game_try_again),
                            color = palette.secondary,
                            onClick = onRetry,
                        )
                    }
                    PauseActionButton(
                        text = stringResource(R.string.game_continue),
                        color = palette.primary,
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseActionButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameHud(state: GameUiState, onPauseToggle: () -> Unit, onQuitTapped: () -> Unit) {
    val palette = LocalPalette.current
    val quitAccessibilityLabel = stringResource(R.string.game_quit_accessibility)

    // Spec 14.5: the timer only announces while frozen — "an always-on label would replace
    // the icon-plus-number screen readers already read by default with a bare, contextless
    // number" — so outside a freeze this stays null and the chip is left to its default
    // label+value reading.
    val frozenSeconds = timerAccessibilitySeconds(ceil(state.timeRemaining).toInt(), state.isFrozen)
    val timerAccessibilityLabel = frozenSeconds?.let {
        stringResource(R.string.levels_timer_frozen_format, it)
    }

    // Spec 14.5: the fails chip announces "N of M mistakes used". There's only an "M" to
    // report in modes with a mistake budget (Levels/Seasons) — free play and the Daily
    // Challenge leave `maxFailures` null, so there's nothing to announce and the chip keeps
    // its default label+value reading there too.
    val failsAccessibilityValues = failsChipAccessibilityValues(state.failedTries, state.maxFailures)
    val failsAccessibilityLabel = failsAccessibilityValues?.let { (used, max) ->
        stringResource(R.string.levels_mistakes_remaining_format, used, max)
    }

    // The ice-shatter burst over the timer chip when a Freeze runs out. The clock
    // restarting is otherwise easy to miss — the number just starts moving again —
    // which is why a haptic already marks the moment (spec 7.6).
    val reduceMotion = rememberReduceMotion()
    var wasFrozen by remember { mutableStateOf(state.isFrozen) }
    var showThaw by remember { mutableStateOf(false) }
    LaunchedEffect(state.isFrozen) {
        if (wasFrozen && !state.isFrozen && !reduceMotion) showThaw = true
        wasFrozen = state.isFrozen
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onQuitTapped,
            modifier = Modifier.semantics { contentDescription = quitAccessibilityLabel },
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = palette.textSecondary)
        }
        Box(contentAlignment = Alignment.Center) {
            Chip(
                label = stringResource(R.string.game_time_label),
                value = "${ceil(state.timeRemaining).toInt()}",
                tint = if (state.isFrozen) palette.freezeBlue else palette.primary,
                accessibilityLabel = timerAccessibilityLabel,
            )
            if (showThaw) {
                BundledLottie(
                    name = "freeze-thaw",
                    tint = palette.freezeBlue,
                    modifier = Modifier.size(96.dp),
                    onFinished = { showThaw = false },
                )
            }
        }
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
            accessibilityLabel = failsAccessibilityLabel,
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
private fun Chip(
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    accessibilityLabel: String? = null,
) {
    val palette = LocalPalette.current
    Column(
        modifier = if (accessibilityLabel != null) {
            Modifier.semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
        } else {
            Modifier
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 11.sp, color = palette.textSecondary)
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

/** Pure selection logic behind the timer chip's accessibility description (spec 14.5): only
 * announced while frozen. Kept separate from the `@Composable` that calls `stringResource` so
 * it is unit-testable without Compose/Robolectric. */
fun timerAccessibilitySeconds(timeRemainingSeconds: Int, isFrozen: Boolean): Int? =
    if (isFrozen) timeRemainingSeconds else null

/** Pure selection logic behind the fails chip's accessibility description (spec 14.5): "N of M
 * mistakes used" only applies where there's an M to report against — modes with no mistake
 * budget ([maxFailures] null) have nothing to announce. Kept separate from the `@Composable`
 * that calls `stringResource` so it is unit-testable without Compose/Robolectric. */
fun failsChipAccessibilityValues(failedTries: Int, maxFailures: Int?): Pair<Int, Int>? =
    maxFailures?.let { failedTries to it }

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
    // The one place iOS actually uses `.contentTransition(.numericText())` — see
    // `PowerUpBar.swift`'s `balanceChip`. Everywhere else a plain `Text($count)` was left
    // unanimated on purpose: iOS itself never applies the numeric transition to the HUD
    // chips or the Levels header's star pill, so animating those here would be inventing
    // behavior the source of truth doesn't have.
    NumericTransition(targetValue = starBalance, label = "powerUpStarBalance") { balance ->
        Text(
            stringResource(R.string.levels_star_balance_format, balance),
            fontSize = 11.sp,
            color = palette.textSecondary,
        )
    }
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
    // Spec 14.5: "power-up buttons announce '<name>, costs N stars'" — and "disabled controls
    // stay silent", mirroring the haptics slice's own disabled-subtree rule, so this is only
    // set while the button is actually enabled; disabled falls back to the default label+cost
    // text reading instead of the crafted sentence.
    val accessibilityLabel = if (enabled) {
        stringResource(R.string.levels_powerup_cost_format, label, cost)
    } else {
        null
    }
    Column(
        modifier
            .background(
                if (enabled) palette.surfacePrimary else palette.surfaceSecondary,
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(12.dp))
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
                } else {
                    Modifier
                },
            )
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
    isDailyChallenge: Boolean,
    dailyStreak: Int,
    onRetry: () -> Unit,
    onQuit: () -> Unit,
    onNextLevel: () -> Unit,
    onBuyLifeWithStars: () -> Unit,
    onForgiveMistakesWithStars: () -> Unit,
    onSkipLevelWithStars: () -> Unit,
    canWatchAdForLife: Boolean = false,
    canWatchAdToForgive: Boolean = false,
    onWatchAdForLife: (onFinished: () -> Unit) -> Unit = { onFinished -> onFinished() },
    onWatchAdToForgive: (onFinished: () -> Unit) -> Unit = { onFinished -> onFinished() },
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSkipConfirm by remember { mutableStateOf(false) }
    var isRewardedAdInProgress by remember { mutableStateOf(false) }
    val won = outcome is GameOutcome.Won
    val lostToMistakes = outcome is GameOutcome.Lost && outcome.reason == LoseReason.TOO_MANY_MISTAKES
    // Mirrors iOS's `LoseModal.isOutOfLives`: null outside Levels/Seasons, so this is
    // always false for free play and the Daily Challenge.
    val isOutOfLives = state.isOutOfLives

    // Built once per outcome — the pairs/time/mistakes/difficulty of the game that just
    // finished (spec: "renders the card from the just-finished game's own state").
    val shareData = remember(state.totalPairs, state.timeRemaining, state.failedTries, state.recordedDifficulty, isDailyChallenge, dailyStreak) {
        ResultShareData(
            pairs = state.totalPairs,
            timeRemaining = ceil(state.timeRemaining).toInt(),
            failedTries = state.failedTries,
            difficulty = state.recordedDifficulty,
            isDailyChallenge = isDailyChallenge,
            streak = dailyStreak,
        )
    }
    val shareCardLayer = rememberGraphicsLayer()

    if (won) {
        // Off-screen render target for the share card (spec: "Compose has no direct
        // SwiftUI-ImageRenderer analogue"). alpha(0f) keeps it invisible without removing
        // it from composition/layout — graphicsLayer.record needs real drawn content to
        // capture, and a Box stacks children without reflowing siblings, so this has no
        // effect on the rest of this overlay's layout.
        Box(
            Modifier
                .width(320.dp)
                .height(440.dp)
                .alpha(0f)
                .drawWithContent {
                    shareCardLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(shareCardLayer)
                },
        ) {
            ShareResultCardView(shareData)
        }
    }

    val reduceMotion = rememberReduceMotion()

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.overlayBackdrop)
            // Same touch-passthrough fix as PauseOverlay: the HUD's "×" quit button sits
            // underneath this backdrop too, and without consuming the tap here it would
            // reach that button, pop the quit-confirmation dialog over the finished-game
            // screen, and call `onPauseToggle` against a `GameViewModel` that has already
            // moved past this game (`pause()`'s `isFinished` guard makes that call a
            // no-op, but the stray dialog itself is still a confusing false affordance).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = palette.surfacePrimary,
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .border(1.dp, palette.surfaceBorder, RoundedCornerShape(28.dp)),
        ) {
            if (won) {
                WinOutcomeContent(
                    state = state,
                    onShare = { coroutineScope.launch { shareResultCard(context, shareCardLayer, shareData) } },
                    onNextLevel = onNextLevel,
                    onQuit = onQuit,
                )
            } else {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The hero says *why*, as on iOS's LoseModal: a clock rings and cracks
                    // on a timeout, a red badge stamps in and shakes its head on a mistake
                    // bust. Both end on a still picture. Reduce-motion players get the
                    // static face iOS has always shown there.
                    if (reduceMotion) {
                        Text("😳", fontSize = 56.sp)
                    } else {
                        BundledLottie(
                            name = if (lostToMistakes) "x-shake" else "clock-crack",
                            tint = palette.secondary,
                            modifier = Modifier.size(72.dp),
                        )
                    }

                    // Reason pill chip — port of iOS's capsule (icon + "You lose" /
                    // "Too many mistakes"), tinted `secondary` at ~10% background opacity.
                    // Replaces the plain heading iOS's LoseModal never actually has.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(palette.secondary.copy(alpha = 0.1f), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = if (lostToMistakes) Icons.Filled.Cancel else Icons.Filled.Timer,
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(
                                if (lostToMistakes) R.string.levels_lose_too_many_mistakes else R.string.game_lose_message,
                            ),
                            color = palette.secondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Lives row (spec 7.4/7.7) — Levels/Seasons only, port of iOS's
                    // `LivesRow(remaining:effect:)`. `livesRemaining` is already the value
                    // `GameViewModel` reads at loss time, so the just-lost heart is the
                    // first empty slot (see `GameUiState.livesRemaining`'s doc).
                    state.livesRemaining?.let { lives ->
                        LivesRow(
                            remaining = lives,
                            fontSize = 15.sp,
                            effect = if (lives < LevelLivesService.MAX_LIVES) LivesEffect.Lost(lives) else null,
                        )
                        if (isOutOfLives) {
                            Text(
                                text = stringResource(
                                    if (canWatchAdForLife) R.string.levels_out_of_lives_message
                                    else R.string.levels_out_of_lives_message_no_ad,
                                ),
                                color = palette.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // Ad / star rescues (spec 7.7) — Levels/Seasons only, gating mirrored
                    // exactly from iOS's `LoseModal.body`: the ad slot offers at most one
                    // rescue (life > forgive; iOS's third, lives-agnostic "extra time" ad
                    // offer has no Android call site yet — see `AdsService.kt`'s
                    // `game_rewarded_extra_time` note and `ANDROID_PLAN.md` Phase 7), buy-
                    // life-with-stars only out of lives, forgive-with-stars only mid-budget
                    // and not out of lives.
                    if (isLevel && starBalance != null) {
                        if (isOutOfLives && canWatchAdForLife) {
                            LoseAdButton(
                                text = stringResource(R.string.levels_watch_ad_for_life),
                                isLoading = isRewardedAdInProgress,
                                onClick = {
                                    isRewardedAdInProgress = true
                                    onWatchAdForLife { isRewardedAdInProgress = false }
                                },
                            )
                        } else if (!isOutOfLives && lostToMistakes && canWatchAdToForgive) {
                            LoseAdButton(
                                text = stringResource(R.string.levels_forgive_ad_format, LevelPowerUp.FORGIVE_AMOUNT),
                                isLoading = isRewardedAdInProgress,
                                onClick = {
                                    isRewardedAdInProgress = true
                                    onWatchAdToForgive { isRewardedAdInProgress = false }
                                },
                            )
                        }

                        if (isOutOfLives && starBalance >= LevelPowerUp.LIFE_COST) {
                            LoseStarButton(
                                text = stringResource(R.string.levels_buy_life_format, LevelPowerUp.LIFE_COST),
                                onClick = onBuyLifeWithStars,
                            )
                        }

                        if (!isOutOfLives && lostToMistakes && starBalance >= LevelPowerUp.FORGIVE_COST) {
                            LoseStarButton(
                                text = stringResource(
                                    R.string.levels_forgive_stars_format,
                                    LevelPowerUp.FORGIVE_AMOUNT,
                                    LevelPowerUp.FORGIVE_COST,
                                ),
                                onClick = onForgiveMistakesWithStars,
                            )
                        }
                    }

                    // "Try again" — filled `secondary` (not primary), hidden for the Daily
                    // Challenge or while out of lives (spec 7.7: `tapOnTryAgain` has nothing
                    // useful to do in either case).
                    if (!isDailyChallenge && !isOutOfLives) {
                        PauseActionButton(
                            text = stringResource(R.string.game_try_again),
                            color = palette.secondary,
                            onClick = onRetry,
                        )
                    }

                    if (isLevel && starBalance != null && starBalance >= LevelPowerUp.SKIP_LEVEL_COST) {
                        LoseStarButton(
                            text = stringResource(R.string.levels_skip_level_format, LevelPowerUp.SKIP_LEVEL_COST),
                            onClick = { showSkipConfirm = true },
                        )
                    }

                    LoseOutlineButton(
                        text = stringResource(R.string.game_go_to_menu),
                        color = palette.primary,
                        onClick = onQuit,
                    )
                }
            }
        }

        // A one-shot celebratory burst in front of the card. Skipped for reduce-motion
        // players, who see the card with no motion at all rather than a burst that plays
        // regardless of the setting. Drawn after the Surface so it sits in front of the
        // card; it takes no input (LottieAnimation adds no pointer input of its own), so
        // the card's buttons stay reachable underneath it.
        if (won && !reduceMotion) {
            BundledLottie(
                name = "confetti-burst",
                modifier = Modifier.size(400.dp),
            )
        }
    }

    // Skip level requires a confirmation dialog (spec 7.7) — every other power-up and
    // lose-screen purchase deliberately does not. iOS replaces the whole modal's content
    // in place with its own `skipConfirmCard`; a native `AlertDialog` reads fine as the
    // Android convention for a confirmation step, so only the button copy/styling (cost +
    // star icon on confirm) is ported, not the inline-card replacement mechanic.
    if (showSkipConfirm) {
        AlertDialog(
            onDismissRequest = { showSkipConfirm = false },
            title = { Text(stringResource(R.string.levels_skip_level_confirm_title)) },
            text = { Text(stringResource(R.string.levels_skip_level_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showSkipConfirm = false
                        onSkipLevelWithStars()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.hardAmber),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${stringResource(R.string.levels_skip_level_confirm_action)} ${LevelPowerUp.SKIP_LEVEL_COST}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSkipConfirm = false },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.primary.copy(alpha = 0.4f)),
                ) {
                    Text(stringResource(R.string.common_cancel), color = palette.primary, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

/** Rewarded-ad rescue on the lose screen (spec 7.7) — port of iOS's `LoseModal.adButton`:
 * filled `hardAmber`, white bold text, swaps to the loading copy and disables itself while
 * the ad is in flight. The free path, so it always leads its star-priced alternative. */
@Composable
private fun LoseAdButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.hardAmber),
    ) {
        Text(
            text = if (isLoading) stringResource(R.string.ads_loading) else text,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Star-priced lose-screen rescue (spec 7.7) — port of iOS's `LoseModal.starButton`:
 * outlined (not filled) `hardAmber`, so it reads as an alternative to the ad path above
 * rather than a replacement for it, with a trailing star glyph. */
@Composable
private fun LoseStarButton(text: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(vertical = 16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.hardAmber.copy(alpha = 0.5f)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.hardAmber)
            Icon(Icons.Filled.Star, contentDescription = null, tint = palette.hardAmber, modifier = Modifier.size(13.dp))
        }
    }
}

/** Outlined capsule text action — port of iOS's LoseModal "Go to menu" style (and its
 * `skipConfirmCard`'s "Cancel"): an outlined [color] border rather than the muted plain
 * text button Android used to render here. */
@Composable
private fun LoseOutlineButton(text: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(vertical = 16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(text, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Android counterpart to iOS's WinModal, including level stars and completion stats. */
@Composable
private fun WinOutcomeContent(
    state: GameUiState,
    onShare: () -> Unit,
    onNextLevel: () -> Unit,
    onQuit: () -> Unit,
) {
    val palette = LocalPalette.current
    val levelNumber = state.levelNumber
    val offersNextLevel = levelNumber != null && state.hasNextLevel
    val primaryTitle = when {
        offersNextLevel -> stringResource(R.string.level_next)
        levelNumber != null -> stringResource(R.string.level_back_to_levels)
        else -> stringResource(R.string.game_go_to_menu)
    }

    Column(
        modifier = Modifier.padding(top = 32.dp, start = 28.dp, end = 28.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("😎", fontSize = 68.sp)
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(if (levelNumber != null) R.string.level_cleared else R.string.game_win_title),
            style = DoMemoryType.display(36),
            color = palette.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        if (levelNumber != null) {
            Text(
                text = stringResource(R.string.level_title_format, levelNumber),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
            )
            WinStarRow(starsEarned = state.starsEarned)
        } else {
            Text(
                text = stringResource(R.string.game_win_description),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            WinStat(value = state.totalPairs.toString(), label = stringResource(R.string.game_pairs_label), color = palette.primary)
            WinStat(value = "${ceil(state.timeRemaining).toInt()}s", label = stringResource(R.string.game_remaining_label), color = palette.easyGreen)
            WinStat(value = state.failedTries.toString(), label = stringResource(R.string.game_errors_label), color = palette.secondary)
        }

        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.surfacePrimary,
                contentColor = palette.primary,
            ),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.primary.copy(alpha = 0.45f)),
        ) {
            Text(stringResource(R.string.share_result), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        PauseActionButton(
            text = primaryTitle,
            color = palette.primary,
            onClick = if (offersNextLevel) onNextLevel else onQuit,
        )
        if (offersNextLevel) {
            TextButton(onClick = onQuit) {
                Text(stringResource(R.string.level_back_to_levels), color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The three-star row of a Levels/Seasons win, popping in one earned star at a time —
 * the port of iOS `WinModal`'s `starView(for:)` + `revealStars()`.
 *
 * `startedCount` paces when each star gets its go-ahead (one every
 * [WIN_STAR_STAGGER_MILLIS]); each star's own Lottie completion raises `completedCount`,
 * which is what actually settles it to the static filled glyph. See [winStarSlot] for why
 * the two counters are kept apart.
 */
@Composable
private fun WinStarRow(starsEarned: Int) {
    val palette = LocalPalette.current
    val reduceMotion = rememberReduceMotion()
    var startedCount by remember { mutableIntStateOf(0) }
    var completedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(starsEarned, reduceMotion) {
        if (reduceMotion || starsEarned <= 0) return@LaunchedEffect
        for (index in 0 until starsEarned) {
            startedCount = index + 1
            delay(WIN_STAR_STAGGER_MILLIS)
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
    ) {
        repeat(3) { index ->
            when (winStarSlot(index, starsEarned, startedCount, completedCount, reduceMotion)) {
                WinStarSlot.DIM -> Text(
                    text = "☆",
                    fontSize = 24.sp,
                    color = palette.textSecondary.copy(alpha = 0.3f),
                )
                WinStarSlot.FILLED -> Text(
                    text = "★",
                    fontSize = 24.sp,
                    color = palette.hardAmber,
                )
                WinStarSlot.POPPING -> BundledLottie(
                    name = "star-pop",
                    modifier = Modifier.size(28.dp),
                    onFinished = { completedCount = maxOf(completedCount, index + 1) },
                )
            }
        }
    }
}

@Composable
private fun WinStat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
    }
}
