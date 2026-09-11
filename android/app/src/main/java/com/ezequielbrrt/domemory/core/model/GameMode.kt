package com.ezequielbrrt.domemory.core.model

import com.ezequielbrrt.domemory.services.levels.LevelProgressStore

/**
 * One gameplay screen serves every mode (spec 5); this value decides which extra rules
 * apply. Kept in the core model from Phase 1 so the game loop never grows a
 * `if (isLevel)` ladder.
 */
sealed interface GameMode {
    data object Free : GameMode
    data object DailyChallenge : GameMode
    data class Level(val context: LevelContext) : GameMode

    val isLevel: Boolean get() = this is Level
}

/**
 * @param number     level as the player sees it, 1-based
 * @param store      who owns unlocks, stars and boards
 * @param seasonId   null = endless Levels
 * @param levelCount null = endless; a season's length otherwise
 */
data class LevelContext(
    val number: Int,
    val store: LevelProgressStore,
    val seasonId: String? = null,
    val levelCount: Int? = null,
) {
    val isSeason: Boolean get() = seasonId != null

    /** True when this is the last level of a bounded (season) run. */
    val isFinalLevel: Boolean get() = levelCount != null && number >= levelCount
}
