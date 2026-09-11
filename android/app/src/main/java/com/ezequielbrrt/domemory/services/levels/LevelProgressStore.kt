package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.core.model.Board

/**
 * The seam that lets endless Levels and Seasons share one gameplay screen (spec 5).
 *
 * Defined in Phase 1 even though only Phase 3 implements it, because building Levels
 * against this interface rather than against a concrete endless service is what makes
 * Seasons cheap in Phase 4 — and it is the single place the "a season has a last
 * level" ceiling is enforced, so the win screen cannot offer level 21 of a 20-level
 * season.
 */
interface LevelProgressStore {
    val highestUnlockedLevel: Int

    fun stars(level: Int): Int

    fun isUnlocked(level: Int): Boolean

    fun board(level: Int): Board

    /** Records a finished attempt and returns the stars awarded (0 on a loss). */
    fun recordCompletion(
        level: Int,
        didWin: Boolean,
        timeRemaining: Double,
        totalTime: Double,
        failedTries: Int,
    ): Int

    fun skipLevel(level: Int)

    /** Endless: always level + 1. Season: null past `levelCount`. */
    fun nextLevel(after: Int): Int?
}
