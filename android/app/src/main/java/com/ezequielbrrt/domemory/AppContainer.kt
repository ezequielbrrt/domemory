package com.ezequielbrrt.domemory

import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.core.time.SystemDayProvider
import com.ezequielbrrt.domemory.data.remote.FirebaseBoardCatalogSource
import com.ezequielbrrt.domemory.data.repository.BoardCatalogRepository
import com.ezequielbrrt.domemory.data.repository.BoardCatalogSource

/**
 * Manual DI (decision D4). Every service the app depends on is constructed here and
 * passed down, so a test can swap any of them for a fake without an annotation
 * processor. Phase 2 moves [playerDifficulty] into DataStore.
 */
class AppContainer(
    val dayProvider: DayProvider = SystemDayProvider,
    catalogSource: BoardCatalogSource = FirebaseBoardCatalogSource(),
) {
    val boardCatalog = BoardCatalogRepository(remote = catalogSource)

    /** Phase 2: persisted as `dificulty` (sic — the iOS attribute name, spec 4). */
    var playerDifficulty: Difficulty = Difficulty.MEDIUM

    fun todayKey(): String = DayKey.of(dayProvider.today())
}
