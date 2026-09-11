package com.ezequielbrrt.domemory.feature.game

/**
 * The narrow slice of persistence [GameViewModel] needs for spec 13.2's per-board
 * `stats.<boardId>.played` / `.won` counters — an interface, like [BoardCatalogSource]-
 * style seams elsewhere in this codebase, so tests inject a synchronous in-memory fake
 * instead of a real `DataStore`. `GameViewModel.finish()` is reachable from a plain
 * (non-suspend) call site (`choose()`, itself a UI click handler), so recording always
 * happens inside a fire-and-forget `launch`; a real `DataStore` write in there is genuine
 * cross-thread async I/O that a virtual-time `TestScope` cannot fast-forward through
 * `advanceUntilIdle()`, so keeping this seam narrow and fake-able is what makes the
 * timing deterministic in [GameViewModel]'s own tests.
 */
fun interface GameStatsRecorder {
    suspend fun recordFinished(boardId: String, didWin: Boolean)
}
