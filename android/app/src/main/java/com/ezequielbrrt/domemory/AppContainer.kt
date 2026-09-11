package com.ezequielbrrt.domemory

import android.content.Context
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.core.time.SystemDayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.data.prefs.createUserPreferences
import com.ezequielbrrt.domemory.data.remote.FirebaseBoardCatalogSource
import com.ezequielbrrt.domemory.data.repository.BoardCatalogRepository
import com.ezequielbrrt.domemory.data.repository.BoardCatalogSource

/**
 * Manual DI (decision D4). Every service the app depends on is constructed here and
 * passed down, so a test can swap any of them for a fake without an annotation
 * processor.
 *
 * [playerDifficulty] is still the in-memory var it was before this DataStore surface
 * existed. There is deliberately no `playerDifficulty`-shaped key in [prefs]: spec
 * 13.2's only related entry is the legacy `dificulty` Prefs key, which iOS itself marks
 * "superseded by CoreData" and this port does not resurrect (see the note on
 * [UserPreferences.hasOnboarded]). The CoreData row's *other* job — remembering the
 * player's chosen difficulty across launches — has no clean-named Android key yet.
 * Wiring this var through [prefs] (under a fresh key, e.g. `playerDifficulty`) is left
 * for the menu-rebuild task: `Phase1Root` reads/writes it synchronously today, and
 * DataStore is async, so making it durable means touching that call site's shape, not
 * just this container.
 */
class AppContainer(
    context: Context,
    val dayProvider: DayProvider = SystemDayProvider,
    catalogSource: BoardCatalogSource = FirebaseBoardCatalogSource(),
) {
    val boardCatalog = BoardCatalogRepository(remote = catalogSource)

    /** The typed DataStore Preferences surface for every key in spec 13.2. */
    val prefs: UserPreferences = createUserPreferences(context)

    var playerDifficulty: Difficulty = Difficulty.MEDIUM

    fun todayKey(): String = DayKey.of(dayProvider.today())
}
