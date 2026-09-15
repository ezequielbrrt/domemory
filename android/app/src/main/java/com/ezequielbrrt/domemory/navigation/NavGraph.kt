package com.ezequielbrrt.domemory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ezequielbrrt.domemory.AppContainer
import com.ezequielbrrt.domemory.core.deeplink.DeepLink
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.model.GameMode
import com.ezequielbrrt.domemory.core.model.LevelContext
import com.ezequielbrrt.domemory.feature.game.GameScreen
import com.ezequielbrrt.domemory.feature.game.GameViewModel
import com.ezequielbrrt.domemory.feature.game.UserPreferencesGameStatsRecorder
import com.ezequielbrrt.domemory.feature.levels.LevelsViewModel
import com.ezequielbrrt.domemory.feature.menu.CreateMemoramaScreen
import com.ezequielbrrt.domemory.feature.menu.CreateMemoramaViewModel
import com.ezequielbrrt.domemory.feature.menu.MenuScreen
import com.ezequielbrrt.domemory.feature.menu.MenuViewModel
import com.ezequielbrrt.domemory.feature.notifications.NotificationPrimerHost
import com.ezequielbrrt.domemory.feature.onboarding.OnboardingScreen
import com.ezequielbrrt.domemory.feature.onboarding.OnboardingViewModel
import com.ezequielbrrt.domemory.feature.seasons.SeasonLevelsScreen
import com.ezequielbrrt.domemory.feature.settings.AchievementsScreen
import com.ezequielbrrt.domemory.feature.settings.AchievementsViewModel
import com.ezequielbrrt.domemory.feature.settings.SettingsScreen
import com.ezequielbrrt.domemory.feature.settings.SettingsViewModel
import com.ezequielbrrt.domemory.feature.whatsnew.WhatsNewDialog
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.ads.findActivity
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.services.review.AppReviews
import com.ezequielbrrt.domemory.services.stats.UserPreferencesProfileStatsRecorder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "achievements"
    const val CREATE_MEMORAMA = "create_memorama"
    const val LEVEL_GAME = "level/{level}"
    const val SEASON_LEVELS = "season/{seasonId}"
    const val SEASON_GAME = "season/{seasonId}/level/{level}"
    const val DAILY_GAME = "daily"
    const val MULTIPLAYER = "multiplayer?code={code}"
    private const val GAME_PATTERN = "game/{boardId}/{difficultyKey}"
    const val GAME = GAME_PATTERN

    fun game(boardId: String, difficulty: Difficulty) = "game/$boardId/${difficulty.key}"
    fun level(level: Int) = "level/$level"
    fun seasonLevels(seasonId: String) = "season/$seasonId"
    fun seasonGame(seasonId: String, level: Int) = "season/$seasonId/level/$level"
    fun multiplayer(code: String = "") = "multiplayer?code=$code"
}

@Composable
fun NavGraph(
    container: AppContainer,
    hasOnboarded: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    // Resolved once and captured by every route below — a full-screen ad needs the
    // Activity to present against; DoMemory is single-activity, so this is MainActivity
    // for the life of the process.
    val activity = LocalContext.current.findActivity()

    // A domemory://daily link can arrive before onboarding resolves (spec 11.1: "links can
    // arrive before the UI exists"); only route it once there's a Menu to land on, and only
    // if today isn't already locked — mirrors the no-op DailyChallengeCard tap above.
    val pendingDeepLink by container.deepLinkRouter.pending.collectAsState()
    LaunchedEffect(hasOnboarded, pendingDeepLink) {
        if (!hasOnboarded) return@LaunchedEffect
        when (container.deepLinkRouter.pending.value) {
            is DeepLink.Daily -> {
                val alreadyDone = container.dailyChallenge.isCompletedToday()
                container.deepLinkRouter.consume()
                if (!alreadyDone) navController.navigate(Routes.DAILY_GAME)
            }
            is DeepLink.Join -> {
                val code = (container.deepLinkRouter.pending.value as DeepLink.Join).code
                container.deepLinkRouter.consume()
                navController.navigate(Routes.multiplayer(code))
            }
            null -> Unit
        }
    }

    NavHost(navController = navController, startDestination = if (hasOnboarded) Routes.MENU else Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = viewModel(factory = viewModelFactory { initializer { OnboardingViewModel(container.prefs) } })
            val state by viewModel.state.collectAsState()
            OnboardingScreen(state, viewModel::next, viewModel::skipIntro, viewModel::selectDifficulty) {
                viewModel.finish { navController.navigate(Routes.MENU) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
            }
        }
        composable(Routes.MENU) {
            val viewModel: MenuViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { MenuViewModel(container.boardCatalog, container.prefs) }
                },
            )
            val state by viewModel.state.collectAsState()
            val levelsViewModel: LevelsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LevelsViewModel(
                            progress = container.levelProgress,
                            lives = container.levelLives,
                            wallet = container.starWallet,
                            introGate = container.levelsIntroGate,
                            onHaptic = HapticsService::fire,
                        )
                    }
                },
            )
            val activeSeason by container.seasonCatalog.activeSeason.collectAsState()
            val dailyStreak by container.prefs.dailyStreakCurrent.collectAsState(initial = 0)
            val dailyLastAttemptDay by container.prefs.dailyLastAttemptDay.collectAsState(initial = null)
            val isDailyCompletedToday = dailyLastAttemptDay == container.todayKey()
            // Default true so the primer never flashes on screen for one frame before
            // DataStore's real value (almost always "already shown", after the first
            // install) arrives — spec 11.3 shows it once per install, not once per launch.
            val notificationPrimerShown by container.prefs.notificationPrimerShown.collectAsState(initial = true)

            Box(Modifier.fillMaxSize()) {
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
                    onMultiplayer = { navController.navigate(Routes.multiplayer()) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    levelsViewModel = levelsViewModel,
                    onLevelSelected = { navController.navigate(Routes.level(it)) },
                    activeSeason = activeSeason,
                    todayKey = container.todayKey(),
                    onSeasonSelected = { season -> navController.navigate(Routes.seasonLevels(season.id)) },
                    dailyStreak = dailyStreak,
                    isDailyChallengeCompletedToday = isDailyCompletedToday,
                    onDailyChallengeSelected = {
                        // Mirrors the domemory://daily deep link: a no-op once today is done,
                        // since there's no result screen yet to send the player back to (spec 11.1).
                        // Matches iOS's DailyChallengeCard/CompactDailyChallengeCard buttons,
                        // which only fire .tap inside the same !isCompleted guard.
                        if (!isDailyCompletedToday) {
                            HapticsService.fire(HapticIntent.TAP)
                            navController.navigate(Routes.DAILY_GAME)
                        }
                    },
                )

                // Spec 11.3: "Shown once per install on the menu, and reused by the Settings
                // toggle" (that reuse is SettingsScreen's own
                // rememberNotificationPermissionRequester call, not this composable).
                NotificationPrimerHost(
                    visible = !notificationPrimerShown,
                    onEnable = { container.applicationScope.launch { container.notifications.activateReminders() } },
                    onDismiss = { container.applicationScope.launch { container.prefs.setNotificationPrimerShown(true) } },
                )
            }
        }

        composable(
            route = Routes.MULTIPLAYER,
            arguments = listOf(navArgument("code") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val initialCode = entry.arguments?.getString("code").orEmpty()
            val viewModel: com.ezequielbrrt.domemory.feature.multiplayer.MultiplayerViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        com.ezequielbrrt.domemory.feature.multiplayer.MultiplayerViewModel(
                            container.multiplayer,
                            UserPreferencesProfileStatsRecorder(container.prefs),
                            onHaptic = HapticsService::fire,
                        )
                    }
                },
            )
            val boards by container.boardCatalog.boards.collectAsState(initial = emptyList())
            com.ezequielbrrt.domemory.feature.multiplayer.MultiplayerScreen(
                boards = boards,
                vm = viewModel,
                initialCode = initialCode,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DAILY_GAME) {
            // Daily Challenge takes the *player's* stored difficulty for the clock/pie, not
            // the board's own — the board always declares medium (spec 8's "medium difficulty"
            // is the board's, distinct from the player setting that drives the timer).
            val playerDifficulty by container.prefs.playerDifficulty.collectAsState(initial = Difficulty.MEDIUM)
            val viewModel: GameViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        GameViewModel(
                            board = container.dailyChallenge.boardForToday(),
                            mode = GameMode.DailyChallenge,
                            playerDifficulty = playerDifficulty,
                            stats = UserPreferencesGameStatsRecorder(container.prefs),
                            profileStats = UserPreferencesProfileStatsRecorder(container.prefs),
                            statsScope = container.applicationScope,
                            dailyChallenge = container.dailyChallenge,
                            onDailyChallengeFinished = container.onDailyChallengeFinished,
                            onGameFinished = container.onGameFinished,
                            onCompletionInterstitial = { difficulty, durationMs ->
                                AdsService.notifyGameFinished(activity, difficulty, durationMs)
                            },
                            onHaptic = HapticsService::fire,
                            onGameWon = { AppReviews.recordSuccessfulGameWin(activity) },
                        )
                    }
                },
            )
            val state by viewModel.state.collectAsState()
            val dailyStreak by container.prefs.dailyStreakCurrent.collectAsState(initial = 0)
            GameScreen(
                state,
                viewModel::choose,
                { if (state.isPaused) viewModel.resume() else viewModel.pause() },
                { HapticsService.fire(HapticIntent.TAP); navController.popBackStack() },
                { HapticsService.fire(HapticIntent.TAP); viewModel.restart() },
                isDailyChallenge = true,
                dailyStreak = dailyStreak,
            )
        }

        composable(Routes.SEASON_LEVELS, arguments = listOf(navArgument("seasonId") { type = NavType.StringType })) { entry ->
            val seasonId = entry.arguments?.getString("seasonId").orEmpty()
            val seasons by container.seasonCatalog.seasons.collectAsState()
            val season = seasons.firstOrNull { it.id == seasonId }
            if (season == null) {
                // The season expired or vanished from the catalog between selection and
                // this composition (e.g. a foreground re-check dropped it) — bounce back
                // rather than crash on a null season.
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val store = remember(season.id) { container.seasonProgressStore(season) }
            SeasonLevelsScreen(
                season = season,
                store = store,
                todayKey = container.todayKey(),
                onLevelSelected = { level -> navController.navigate(Routes.seasonGame(season.id, level)) },
            )
        }

        composable(
            route = Routes.SEASON_GAME,
            arguments = listOf(
                navArgument("seasonId") { type = NavType.StringType },
                navArgument("level") { type = NavType.IntType },
            ),
        ) { entry ->
            val seasonId = entry.arguments?.getString("seasonId").orEmpty()
            val level = entry.arguments?.getInt("level") ?: 1
            val seasons by container.seasonCatalog.seasons.collectAsState()
            val season = seasons.firstOrNull { it.id == seasonId }
            if (season == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val store = remember(season.id) { container.seasonProgressStore(season) }
            val viewModel: GameViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        GameViewModel(
                            board = store.board(level),
                            mode = GameMode.Level(
                                LevelContext(number = level, store = store, seasonId = season.id, levelCount = season.levelCount),
                            ),
                            stats = UserPreferencesGameStatsRecorder(container.prefs),
                            profileStats = UserPreferencesProfileStatsRecorder(container.prefs),
                            statsScope = container.applicationScope,
                            levelLives = container.levelLives,
                            // Power-ups and the lose-screen star purchases are "Levels and
                            // Seasons only" (spec 7.6) — season play is a GameMode.Level the
                            // same way endless is, so it needs the same wallet.
                            starWallet = container.starWallet,
                            onGameFinished = container.onGameFinished,
                            onCompletionInterstitial = { difficulty, durationMs ->
                                AdsService.notifyGameFinished(activity, difficulty, durationMs)
                            },
                            onHaptic = HapticsService::fire,
                            onGameWon = { AppReviews.recordSuccessfulGameWin(activity) },
                        )
                    }
                },
            )
            val state by viewModel.state.collectAsState()
            val starBalance by container.starWallet.balance.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            GameScreen(
                state = state,
                onChoose = viewModel::choose,
                onPauseToggle = { if (state.isPaused) viewModel.resume() else viewModel.pause() },
                onQuit = {
                    // A season loss is deferred exactly like an endless one (see
                    // GameViewModel's class doc) — quitting without this would leave the
                    // life unspent and the attempt unrecorded. acknowledgeLossAndQuit()
                    // fires its own TAP haptic.
                    viewModel.acknowledgeLossAndQuit()
                    navController.popBackStack()
                },
                onRetry = {
                    coroutineScope.launch {
                        if (!viewModel.retry()) {
                            // Out of lives after this loss committed. There is no
                            // season-specific out-of-lives prompt yet (deferred — see
                            // ANDROID_PLAN.md); bounce back to the season map rather than
                            // strand the player on a screen with nothing left to do.
                            navController.popBackStack()
                        }
                    }
                },
                isLevel = true,
                starBalance = starBalance,
                onBuyExtraTime = { coroutineScope.launch { viewModel.buyExtraTime() } },
                onBuyPeek = { coroutineScope.launch { viewModel.buyPeek() } },
                onBuyFreeze = { coroutineScope.launch { viewModel.buyFreeze() } },
                onBuyRevealPair = { coroutineScope.launch { viewModel.buyRevealPair() } },
                onBuyLifeWithStars = { coroutineScope.launch { viewModel.buyLifeWithStars() } },
                onForgiveMistakesWithStars = { coroutineScope.launch { viewModel.forgiveMistakesWithStars() } },
                onSkipLevelWithStars = {
                    coroutineScope.launch {
                        if (viewModel.skipLevelWithStars()) navController.popBackStack()
                    }
                },
                onWatchAdForLife = {
                    HapticsService.fire(HapticIntent.TAP)
                    AdsService.showRewarded(activity, AdPlacement.LEVELS_REWARDED_LIFE, onReward = {
                        coroutineScope.launch { viewModel.applyLifeReward() }
                    })
                },
                onWatchAdToForgive = {
                    HapticsService.fire(HapticIntent.TAP)
                    AdsService.showRewarded(activity, AdPlacement.LEVELS_REWARDED_FORGIVE, onReward = {
                        coroutineScope.launch { viewModel.applyForgiveMistakesReward() }
                    })
                },
            )
        }

        composable(Routes.LEVEL_GAME, arguments = listOf(navArgument("level") { type = NavType.IntType })) { entry ->
            val level = entry.arguments?.getInt("level") ?: 1
            val store = container.levelProgress
            val viewModel: GameViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        GameViewModel(
                            board = store.board(level),
                            mode = GameMode.Level(com.ezequielbrrt.domemory.core.model.LevelContext(level, store)),
                            stats = UserPreferencesGameStatsRecorder(container.prefs),
                            profileStats = UserPreferencesProfileStatsRecorder(container.prefs),
                            statsScope = container.applicationScope,
                            levelLives = container.levelLives,
                            starWallet = container.starWallet,
                            onGameFinished = container.onGameFinished,
                            onCompletionInterstitial = { difficulty, durationMs ->
                                AdsService.notifyGameFinished(activity, difficulty, durationMs)
                            },
                            onHaptic = HapticsService::fire,
                            onGameWon = { AppReviews.recordSuccessfulGameWin(activity) },
                        )
                    }
                },
            )
            val state by viewModel.state.collectAsState()
            val starBalance by container.starWallet.balance.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            // The same LevelsViewModel instance still on the Menu back-stack entry —
            // refreshed on the way out so newly-spent lives and stars show up the moment
            // the player is back on the map (mirrors iOS's `LevelsView.onDisappear`).
            val menuEntry = remember { navController.getBackStackEntry(Routes.MENU) }
            val levelsViewModel: LevelsViewModel = viewModel(
                viewModelStoreOwner = menuEntry,
                factory = viewModelFactory {
                    initializer {
                        LevelsViewModel(
                            progress = container.levelProgress,
                            lives = container.levelLives,
                            wallet = container.starWallet,
                            introGate = container.levelsIntroGate,
                            onHaptic = HapticsService::fire,
                        )
                    }
                },
            )
            DisposableEffect(Unit) {
                onDispose { levelsViewModel.refresh() }
            }

            GameScreen(
                state = state,
                onChoose = viewModel::choose,
                onPauseToggle = { if (state.isPaused) viewModel.resume() else viewModel.pause() },
                onQuit = {
                    viewModel.acknowledgeLossAndQuit()
                    navController.popBackStack()
                },
                onRetry = {
                    coroutineScope.launch {
                        if (!viewModel.retry()) {
                            // Out of lives after this loss committed — stay put; the
                            // Levels map's own out-of-lives prompt is where the player
                            // buys back in, matching iOS's LoseModal that re-renders into
                            // its out-of-lives state instead of restarting (spec 7.4).
                            navController.popBackStack()
                        }
                    }
                },
                isLevel = true,
                starBalance = starBalance,
                onBuyExtraTime = { coroutineScope.launch { viewModel.buyExtraTime() } },
                onBuyPeek = { coroutineScope.launch { viewModel.buyPeek() } },
                onBuyFreeze = { coroutineScope.launch { viewModel.buyFreeze() } },
                onBuyRevealPair = { coroutineScope.launch { viewModel.buyRevealPair() } },
                onBuyLifeWithStars = { coroutineScope.launch { viewModel.buyLifeWithStars() } },
                onForgiveMistakesWithStars = { coroutineScope.launch { viewModel.forgiveMistakesWithStars() } },
                onSkipLevelWithStars = {
                    coroutineScope.launch {
                        if (viewModel.skipLevelWithStars()) navController.popBackStack()
                    }
                },
                onWatchAdForLife = {
                    HapticsService.fire(HapticIntent.TAP)
                    AdsService.showRewarded(activity, AdPlacement.LEVELS_REWARDED_LIFE, onReward = {
                        coroutineScope.launch { viewModel.applyLifeReward() }
                    })
                },
                onWatchAdToForgive = {
                    HapticsService.fire(HapticIntent.TAP)
                    AdsService.showRewarded(activity, AdPlacement.LEVELS_REWARDED_FORGIVE, onReward = {
                        coroutineScope.launch { viewModel.applyForgiveMistakesReward() }
                    })
                },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container.prefs, container.notifications) } })
            val state by viewModel.state.collectAsState()
            val menuEntry = remember { navController.getBackStackEntry(Routes.MENU) }
            val menuViewModel: MenuViewModel = viewModel(viewModelStoreOwner = menuEntry, factory = viewModelFactory { initializer { MenuViewModel(container.boardCatalog, container.prefs) } })
            // A second, screen-local presentation of the same dialog MainActivity shows
            // automatically after a version upgrade (WhatsNewDialog's own doc: "intentionally
            // reused for automatic and Settings presentation"). No prefs write on dismiss here
            // — reopening it manually never needs to change whatsNewLastSeenVersion, which is
            // already at the running version by the time this screen is reachable at all.
            var showWhatsNew by remember { mutableStateOf(false) }
            SettingsScreen(
                state,
                onBack = { if (state.difficultyChanged) menuViewModel.onSettingsDifficultyChanged(); navController.popBackStack() },
                onDifficulty = viewModel::setDifficulty,
                onTheme = viewModel::setTheme,
                onHaptics = viewModel::setHaptics,
                onEnableReminders = viewModel::enableReminders,
                onDisableReminders = viewModel::disableReminders,
                onWhatsNew = { showWhatsNew = true },
                onAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
            )
            if (showWhatsNew) {
                WhatsNewDialog(onDismiss = { showWhatsNew = false })
            }
        }

        composable(Routes.ACHIEVEMENTS) {
            val viewModel: AchievementsViewModel = viewModel(
                factory = viewModelFactory { initializer { AchievementsViewModel(container.profileStats) } },
            )
            val state by viewModel.state.collectAsState()
            AchievementsScreen(state = state, onBack = { navController.popBackStack() })
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
                            profileStats = UserPreferencesProfileStatsRecorder(container.prefs),
                            statsScope = container.applicationScope,
                            onGameFinished = container.onGameFinished,
                            onCompletionInterstitial = { difficulty, durationMs ->
                                AdsService.notifyGameFinished(activity, difficulty, durationMs)
                            },
                            onHaptic = HapticsService::fire,
                            onGameWon = { AppReviews.recordSuccessfulGameWin(activity) },
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
                onQuit = { HapticsService.fire(HapticIntent.TAP); navController.popBackStack() },
                onRetry = { HapticsService.fire(HapticIntent.TAP); viewModel.restart() },
            )
        }
    }
}
