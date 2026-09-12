package com.ezequielbrrt.domemory.services.seasons

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.services.levels.BoardGenerators
import com.ezequielbrrt.domemory.services.levels.LevelProgressStore

/**
 * A [SeasonProgressService] bound to the season it tracks, satisfying
 * [LevelProgressStore] so a season level can be handed straight to
 * [com.ezequielbrrt.domemory.core.model.LevelContext] — the same seam endless Levels use
 * (spec 5).
 *
 * The pool and length live here rather than as stored properties on
 * [SeasonProgressService] for the same reason as iOS's twin type: the service is
 * addressed by season id alone (that is how it namespaces its DataStore keys and how its
 * own tests exercise it), so giving it an optional stored pool would let a pool-less
 * instance silently deal an empty board.
 */
class SeasonLevelProgressStore(
    private val season: Season,
    val progress: SeasonProgressService,
) : LevelProgressStore {

    override val highestUnlockedLevel: Int get() = progress.highestUnlockedLevel

    override fun stars(level: Int): Int = progress.stars(level)

    override fun isUnlocked(level: Int): Boolean = progress.isUnlocked(level)

    override fun board(level: Int): Board = BoardGenerators.seasonLevel(season.id, level, season.emojiPool)

    override fun recordCompletion(
        level: Int,
        didWin: Boolean,
        timeRemaining: Double,
        totalTime: Double,
        failedTries: Int,
    ): Int = progress.recordCompletion(level, didWin, timeRemaining, totalTime, failedTries)

    override fun skipLevel(level: Int) = progress.skipLevel(level)

    /** Null past the season's last level — the win screen must not offer level 21 of a 20-level season. */
    override fun nextLevel(after: Int): Int? = progress.nextLevel(after, season.levelCount)
}
