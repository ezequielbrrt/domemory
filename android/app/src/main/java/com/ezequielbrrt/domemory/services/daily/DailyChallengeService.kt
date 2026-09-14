package com.ezequielbrrt.domemory.services.daily

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.levels.BoardGenerators
import kotlinx.coroutines.flow.first

/**
 * Deterministic Daily Challenge + streak tracking (spec 8).
 *
 * One board per calendar day, identical *content* for every player (the pairs come from a
 * date-seeded RNG via [BoardGenerators.daily]) — the card **layout** is still shuffled per
 * play inside [com.ezequielbrrt.domemory.core.model.MemoryGame], so a shared screenshot
 * can't be used to memorize positions. One attempt per day: any finish, win or loss, locks
 * the card until the next local midnight — [DayProvider] is the seam that makes that
 * boundary testable without waiting for it.
 */
class DailyChallengeService(
    private val prefs: UserPreferences,
    private val dayProvider: DayProvider,
) {
    /** Stable per-day seed, e.g. `20260616`. Everything else here is keyed off this. */
    fun todaysSeed(): String = DayKey.of(dayProvider.today())

    /** Deterministic board for today. Same pairs for everyone; free of any I/O. */
    fun boardForToday(): Board = BoardGenerators.daily(todaysSeed())

    /** True once today's challenge has been finished (win or loss). */
    suspend fun isCompletedToday(): Boolean = prefs.dailyLastAttemptDay.first() == todaysSeed()

    suspend fun currentStreak(): Int = prefs.dailyStreakCurrent.first()

    suspend fun longestStreak(): Int = prefs.dailyStreakLongest.first()

    /** The result of recording one finish: the streak after recording, and whether it hit a milestone. */
    data class CompletionResult(val streak: Int, val isNewMilestone: Boolean, val alreadyCompleted: Boolean)

    /**
     * Records the result of today's challenge. Idempotent within a day — see
     * [UserPreferences.recordDailyCompletion] for the exact atomic transaction. [MILESTONES]
     * is exposed so a caller can log analytics later without duplicating the threshold list
     * (spec 8: "milestone analytics fire at streaks of 3, 7, 14, 30, 100" — Android has no
     * analytics wiring yet, so this only reports whether the streak *is* a milestone).
     */
    suspend fun recordCompletion(didWin: Boolean): CompletionResult {
        val today = todaysSeed()
        val result = prefs.recordDailyCompletion(today, didWin)
        return CompletionResult(
            streak = result.streak,
            isNewMilestone = didWin && !result.alreadyCompleted && result.streak in MILESTONES,
            alreadyCompleted = result.alreadyCompleted,
        )
    }

    companion object {
        const val PAIRS_PER_CHALLENGE = BoardGenerators.DAILY_PAIR_COUNT
        val MILESTONES = setOf(3, 7, 14, 30, 100)
    }
}
