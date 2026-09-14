package com.ezequielbrrt.domemory

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ezequielbrrt.domemory.core.deeplink.DeepLinkRouter
import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.core.time.SystemDayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.data.prefs.createUserPreferences
import com.ezequielbrrt.domemory.data.remote.FirebaseBoardCatalogSource
import com.ezequielbrrt.domemory.data.remote.FirebaseSeasonCatalogSource
import com.ezequielbrrt.domemory.data.repository.BoardCatalogRepository
import com.ezequielbrrt.domemory.data.repository.BoardCatalogSource
import com.ezequielbrrt.domemory.services.daily.DailyChallengeService
import com.ezequielbrrt.domemory.services.notifications.NotificationService
import com.ezequielbrrt.domemory.widget.DailyChallengeGlanceWidget
import com.ezequielbrrt.domemory.services.levels.LevelProgressService
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.services.levels.LevelsIntroGate
import com.ezequielbrrt.domemory.services.levels.StarWalletService
import com.ezequielbrrt.domemory.services.seasons.Season
import com.ezequielbrrt.domemory.services.seasons.SeasonCatalogService
import com.ezequielbrrt.domemory.services.seasons.SeasonCatalogSource
import com.ezequielbrrt.domemory.services.seasons.SeasonLevelProgressStore
import com.ezequielbrrt.domemory.services.seasons.SeasonProgressService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    seasonCatalogSource: SeasonCatalogSource = FirebaseSeasonCatalogSource(),
) {
    private val appContext = context.applicationContext

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
    val dailyChallenge = DailyChallengeService(prefs, dayProvider)
    val deepLinkRouter = DeepLinkRouter()

    /** Local reminders (spec 11.2) — inactivity tiers, the streak-at-risk nudge, permission sync. */
    val notifications = NotificationService(appContext, prefs, dailyChallenge)

    /**
     * Spec 8's "finishing refreshes the streak-at-risk reminder and the home-screen widget",
     * and spec 11.2's "rescheduled ... after every game finish" for the streak reminder
     * specifically. [com.ezequielbrrt.domemory.feature.game.GameViewModel] is plain Kotlin
     * with no Android dependency, so it takes this as a bare `() -> Unit` rather than reaching
     * for [notifications] or the widget itself — wiring the two Android-framework side effects
     * together is this container's job, not the view model's.
     */
    val onDailyChallengeFinished: () -> Unit = {
        applicationScope.launch {
            notifications.refreshStreakAtRiskReminder()
            DailyChallengeGlanceWidget().updateAll(appContext)
        }
    }

    /**
     * Spec 11.2: "Both [inactivity tiers] are rescheduled (cancel + re-add) after every game
     * finish" — every mode, not just the Daily Challenge (that one additionally gets
     * [onDailyChallengeFinished] for the streak-specific reminder and the widget).
     */
    val onGameFinished: () -> Unit = {
        applicationScope.launch { notifications.scheduleInactivityReminders() }
    }

    /**
     * Cache-first `/seasons` catalog (spec 9.7). [loadCached] is launched immediately below
     * so the season card has an answer as soon as the cache read finishes, without blocking
     * app startup on it; [SeasonCatalogService.refresh] then corrects it over the network.
     */
    val seasonCatalog = SeasonCatalogService(prefs, seasonCatalogSource)

    /**
     * One [SeasonProgressService] per season id for the life of the process, so two screens
     * observing the same season (the card, the map) share one cache and one `revision`
     * stream rather than each reading DataStore cold.
     */
    private val seasonProgressServices = mutableMapOf<String, SeasonProgressService>()

    init {
        applicationScope.launch {
            seasonCatalog.loadCached(todayKey())
            seasonCatalog.refresh(todayKey())
        }
    }

    /** Re-evaluates the active season against today with no network round trip (spec 9.4: foreground). */
    fun refreshActiveSeason() = seasonCatalog.refreshActive(todayKey())

    /** The [SeasonLevelProgressStore] for [season], memoized per season id. */
    fun seasonProgressStore(season: Season): SeasonLevelProgressStore {
        val progress = seasonProgressServices.getOrPut(season.id) {
            SeasonProgressService(season.id, prefs, applicationScope)
        }
        return SeasonLevelProgressStore(season, progress)
    }

    fun todayKey(): String = DayKey.of(dayProvider.today())
}
