package com.ezequielbrrt.domemory.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlin.math.ceil

/**
 * The gameplay screen. The board is laid out square-ish and never scrolls: columns =
 * ceil(sqrt(count)), rows = ceil(count / columns), and cards fill whatever space is
 * left (spec 3.5).
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onChoose: (Int) -> Unit,
    onPauseToggle: () -> Unit,
    onQuit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    // Drives the pie only. Repainting on frames is cheap; recomputing the model is not.
    var frameTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.showsPie, state.isPaused, state.isFinished) {
        while (state.showsPie && !state.isPaused && !state.isFinished) {
            withFrameMillis { }
            frameTime = System.currentTimeMillis()
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
        }

        state.outcome?.let { outcome ->
            OutcomeOverlay(
                outcome = outcome,
                state = state,
                onRetry = onRetry,
                onQuit = onQuit,
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
    onRetry: () -> Unit,
    onQuit: () -> Unit,
) {
    val palette = LocalPalette.current
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
                val won = outcome is GameOutcome.Won
                Text(
                    text = stringResource(
                        if (won) R.string.game_win_title else R.string.game_lose_message,
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
}
