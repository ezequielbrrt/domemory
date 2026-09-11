package com.ezequielbrrt.domemory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ezequielbrrt.domemory.AppContainer
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.model.GameMode
import com.ezequielbrrt.domemory.feature.game.GameScreen
import com.ezequielbrrt.domemory.feature.game.GameViewModel
import com.ezequielbrrt.domemory.feature.game.UserPreferencesGameStatsRecorder
import com.ezequielbrrt.domemory.feature.menu.CreateMemoramaScreen
import com.ezequielbrrt.domemory.feature.menu.CreateMemoramaViewModel
import com.ezequielbrrt.domemory.feature.menu.MenuScreen
import com.ezequielbrrt.domemory.feature.menu.MenuViewModel
import kotlinx.coroutines.launch

/**
 * Replaces `Phase1Root` (spec 2): Menu -> Game -> back, plus Menu -> create memorama ->
 * back to Menu. Every screen owns a real [androidx.lifecycle.ViewModel] scoped to its
 * nav back-stack entry, so leaving a screen (a `popBackStack`) tears its view model down
 * through the normal `ViewModelStore` -> `onCleared()` path — no more `remember`-only
 * state, and no more a "try again" leaving a countdown ticking forever (`Phase1Root`'s
 * own doc comment named that exact bug; see [GameViewModel.restart]).
 */
private object Routes {
    const val MENU = "menu"
    const val CREATE_MEMORAMA = "create_memorama"
    private const val GAME_PATTERN = "game/{boardId}/{difficultyKey}"
    const val GAME = GAME_PATTERN

    fun game(boardId: String, difficulty: Difficulty) = "game/$boardId/${difficulty.key}"
}

@Composable
fun NavGraph(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            val viewModel: MenuViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { MenuViewModel(container.boardCatalog, container.prefs) }
                },
            )
            val state by viewModel.state.collectAsState()
            MenuScreen(
                state = state,
                onSelectTab = viewModel::selectTab,
                onDifficultyChange = viewModel::setDifficulty,
                onToggleFavorite = viewModel::toggleFavorite,
                onDeleteCustomMemorama = viewModel::deleteCustomMemorama,
                onBoardSelected = { board ->
                    navController.navigate(Routes.game(board.id, state.difficulty))
                },
                onCreateMemorama = { navController.navigate(Routes.CREATE_MEMORAMA) },
            )
        }

        composable(Routes.CREATE_MEMORAMA) {
            val viewModel: CreateMemoramaViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { CreateMemoramaViewModel(container.prefs) }
                },
            )
            // The same MenuViewModel instance still on the back stack — reused (not
            // recreated) so telling it about the new board lands on the view the player
            // returns to, rather than a throwaway second instance.
            val menuEntry = remember { navController.getBackStackEntry(Routes.MENU) }
            val menuViewModel: MenuViewModel = viewModel(
                viewModelStoreOwner = menuEntry,
                factory = viewModelFactory {
                    initializer { MenuViewModel(container.boardCatalog, container.prefs) }
                },
            )
            val state by viewModel.state.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            CreateMemoramaScreen(
                state = state,
                onNameChange = viewModel::updateName,
                onEmojiInputChange = viewModel::updateEmojiInput,
                onAddEmoji = viewModel::addEmoji,
                onRemoveEmoji = viewModel::removeEmoji,
                onSave = {
                    coroutineScope.launch {
                        if (viewModel.save()) {
                            menuViewModel.onCustomMemoramaChanged()
                            navController.popBackStack()
                        }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.GAME,
            arguments = listOf(
                navArgument("boardId") { type = NavType.StringType },
                navArgument("difficultyKey") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val boardId = backStackEntry.arguments?.getString("boardId").orEmpty()
            val difficulty = Difficulty.parseOrDefault(
                backStackEntry.arguments?.getString("difficultyKey"),
            )
            // The board comes from the same in-session repository the menu just read
            // from — free play never needs a second network round trip.
            val board = remember(boardId) { container.boardCatalog.board(boardId) }

            if (board == null) {
                // The board vanished between selection and this composition (e.g. a
                // custom board was deleted from another screen) — bounce back rather
                // than crash on a null board.
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }

            val viewModel: GameViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        GameViewModel(
                            board = board,
                            mode = GameMode.Free,
                            playerDifficulty = difficulty,
                            stats = UserPreferencesGameStatsRecorder(container.prefs),
                            statsScope = container.applicationScope,
                        )
                    }
                },
            )
            val state by viewModel.state.collectAsState()
            GameScreen(
                state = state,
                onChoose = viewModel::choose,
                onPauseToggle = {
                    if (state.isPaused) viewModel.resume() else viewModel.pause()
                },
                onQuit = { navController.popBackStack() },
                onRetry = viewModel::restart,
            )
        }
    }
}
