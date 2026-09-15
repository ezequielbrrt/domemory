package com.ezequielbrrt.domemory.services.stats

import com.ezequielbrrt.domemory.core.model.Difficulty

/**
 * The narrow slice of persistence [com.ezequielbrrt.domemory.feature.game.GameViewModel]
 * and [com.ezequielbrrt.domemory.feature.multiplayer.MultiplayerViewModel] need to record
 * lifetime profile aggregates (spec 13.2), mirroring iOS's
 * `ProfileStatsService.recordGameFinished` / `.recordMultiplayerWin`. An interface, the
 * same seam shape as [com.ezequielbrrt.domemory.feature.game.GameStatsRecorder], so a test
 * injects a synchronous in-memory fake instead of a real `DataStore`.
 */
interface ProfileStatsRecorder {
    /**
     * Fires once per finished game, from the same single-fire commit points that already
     * record per-board stats (`GameViewModel.commit()` / `.commitLossIfNeeded()`). [isPerfect]
     * only matters when [didWin] is true — mirrors iOS's `guard didWin else return` before
     * touching wins/perfect-games/bestRemaining at all.
     */
    suspend fun recordGameFinished(didWin: Boolean, isPerfect: Boolean, difficulty: Difficulty, timeRemaining: Int)

    /** Fires once per room, only for the actual winner, on a room reaching `FINISHED`. */
    suspend fun recordMultiplayerWin()
}
