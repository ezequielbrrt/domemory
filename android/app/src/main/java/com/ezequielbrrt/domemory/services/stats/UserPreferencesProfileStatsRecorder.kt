package com.ezequielbrrt.domemory.services.stats

import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * Production [ProfileStatsRecorder]. Line-for-line mirrors iOS's
 * `ProfileStatsService.recordGameFinished`/`.recordMultiplayerWin`: `totalPlayed` always
 * increments; every other counter is gated on a win, and `bestRemaining` only replaces the
 * stored value when the new remaining time is strictly greater (a *faster* clear is a worse
 * `timeRemaining`, so this is "fewer seconds spent," matching iOS's
 * `if timeRemaining > current`).
 */
class UserPreferencesProfileStatsRecorder(private val prefs: UserPreferences) : ProfileStatsRecorder {
    override suspend fun recordGameFinished(
        didWin: Boolean,
        isPerfect: Boolean,
        difficulty: Difficulty,
        timeRemaining: Int,
    ) {
        prefs.incrementTotalPlayed()
        if (!didWin) return
        prefs.incrementTotalWon()
        if (isPerfect) prefs.incrementPerfectGames()
        val current = prefs.bestRemaining(difficulty).first() ?: 0
        if (timeRemaining > current) prefs.setBestRemaining(difficulty, timeRemaining)
    }

    override suspend fun recordMultiplayerWin() {
        prefs.incrementMultiplayerWins()
    }
}
