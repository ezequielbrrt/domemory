package com.ezequielbrrt.domemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.feature.boardpicker.BoardPickerScreen
import com.ezequielbrrt.domemory.feature.game.GameScreen
import com.ezequielbrrt.domemory.feature.game.GameViewModel
import com.ezequielbrrt.domemory.ui.theme.DoMemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as DoMemoryApplication).container
        setContent {
            DoMemoryTheme {
                Scaffold { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        Phase1Root(container)
                    }
                }
            }
        }
    }
}

/**
 * Phase 1 navigation: difficulty + board -> game -> back. Phase 2 replaces this with a
 * real nav graph (menu, tabs, settings, onboarding) and the deep-link router.
 */
@Composable
private fun Phase1Root(container: AppContainer) {
    var difficulty by remember { mutableStateOf(container.playerDifficulty) }
    var activeBoard by remember { mutableStateOf<Board?>(null) }
    var replayToken by remember { mutableStateOf(0) }

    val catalog by container.boardCatalog.boards.collectAsState()
    val status by container.boardCatalog.status.collectAsState()

    // One snapshot read per menu load (spec 13.1). Phase 2 re-runs this when the
    // player changes difficulty in Settings, and only when the value actually changed.
    LaunchedEffect(Unit) { container.boardCatalog.refresh() }

    val board = activeBoard
    if (board == null) {
        BoardPickerScreen(
            difficulty = difficulty,
            boards = remember(catalog, difficulty) {
                container.boardCatalog.boards(difficulty)
            },
            status = status,
            onDifficultyChange = {
                difficulty = it
                container.playerDifficulty = it
            },
            onBoardSelected = { activeBoard = it },
        )
        return
    }

    // Keyed on the replay token so "try again" builds a freshly shuffled board.
    val viewModel = remember(board.id, replayToken, difficulty) {
        GameViewModel(board = board, playerDifficulty = difficulty)
    }
    val state by viewModel.state.collectAsState()

    // The view model is remembered rather than owned by a ViewModelStore, so nothing
    // calls onCleared for it. Without this, "try again" leaves the previous game's
    // countdown ticking forever.
    DisposableEffect(viewModel) {
        onDispose { viewModel.stop() }
    }

    GameScreen(
        state = state,
        onChoose = viewModel::choose,
        onPauseToggle = {
            if (state.isPaused) viewModel.resume() else viewModel.pause()
        },
        onQuit = { activeBoard = null },
        onRetry = { replayToken += 1 },
    )
}
