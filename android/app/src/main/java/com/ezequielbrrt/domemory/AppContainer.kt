package com.ezequielbrrt.domemory

import android.content.Context
import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.core.time.SystemDayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.data.prefs.createUserPreferences
import com.ezequielbrrt.domemory.data.remote.FirebaseBoardCatalogSource
import com.ezequielbrrt.domemory.data.repository.BoardCatalogRepository
import com.ezequielbrrt.domemory.data.repository.BoardCatalogSource
import com.ezequielbrrt.domemory.services.levels.LevelProgressService
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelsIntroGate
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * Manual DI (decision D4). Every service the app depends on is constructed here and
 * passed down, so a test can swap any of them for a fake without an annotation
 * processor.
 *
 * The player's chosen difficulty now lives in [prefs] (`UserPreferences.playerDifficulty`)
 * rather than as an in-memory var — see that accessor's doc for why it is a fresh key
 * rather than the legacy iOS `dificulty` one. [boardCatalog]'s custom-board list is
 * likewise sourced from `prefs.customMemoramas` instead of the old in-memory
 * `setCustomBoards()`.
 */
class AppContainer(
    context: Context,
    val dayProvider: DayProvider = SystemDayProvider,
    catalogSource: BoardCatalogSource = FirebaseBoardCatalogSource(),
) {
    /**
     * Work that must outlive an individual screen's ViewModel. At present this is only
     * the completion-stat write started as a player leaves a finished game; keeping it
     * here prevents NavController teardown from cancelling the DataStore edit.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The typed DataStore Preferences surface for every key in spec 13.2. */
    val prefs: UserPreferences = createUserPreferences(context)

    val boardCatalog = BoardCatalogRepository(
        remote = catalogSource,
        customBoardsSource = { prefs.customMemoramas.first() },
    )

    val levelProgress = LevelProgressService(prefs, applicationScope)
    val levelLives = LevelLivesService(prefs, dayProvider)
    val starWallet = StarWalletService(prefs, applicationScope)
    val levelsIntroGate = LevelsIntroGate(prefs)

    fun todayKey(): String = DayKey.of(dayProvider.today())
}
