package com.ezequielbrrt.domemory.services.seasons

import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.levels.Stars
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Per-season unlock/star progress and deterministic board generation (spec 9.6).
 *
 * Mirrors [com.ezequielbrrt.domemory.services.levels.LevelProgressService], but namespaced
 * under `season.<id>.*` so a season ending cannot disturb endless-Levels progress and a
 * season that returns next year resumes exactly where it left off. Unlike the endless
 * store, this one is **finite**: [nextLevel] returns null past the season's [levelCount],
 * and completion is stored as `highestUnlocked = levelCount + 1` — which is why extending
 * a running season needs no migration (a player who cleared 20 of 20 simply finds level
 * 21 unlocked once the season grows to 30, reading "20 of 30").
 *
 * Deliberately no `season.<id>.lifetimeStars` mirror: `levels.lifetimeStars` is the
 * endless-Levels mastery score, and season play must credit only the shared spendable
 * wallet ([UserPreferences.recordSeasonLevelCompletion]), never that counter.
 */
class SeasonProgressService(
    val seasonId: String,
    private val prefs: UserPreferences,
    private val scope: CoroutineScope,
) {
    private val starsCache = mutableMapOf<Int, Int>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()
    private var highest = 1

    init {
        scope.launch {
            highest = prefs.seasonHighestUnlocked(seasonId).first().coerceAtLeast(1)
            _revision.value++
        }
    }

    /** Highest level currently playable. Levels `1..<highestUnlockedLevel` have been cleared. */
    val highestUnlockedLevel: Int get() = highest

    /** 0 if the level has not been cleared yet. */
    fun stars(level: Int): Int {
        if (level !in starsCache) scope.launch {
            starsCache[level] = prefs.seasonStars(seasonId, level).first()
            _revision.value++
        }
        return starsCache[level] ?: 0
    }

    fun isUnlocked(level: Int): Boolean = level <= highest

    /** True once the season's final level has been cleared. */
    fun isComplete(levelCount: Int): Boolean = highest > levelCount

    /** How many of the season's levels have been cleared, clamped to its length. */
    fun clearedLevelCount(levelCount: Int): Int = (highest - 1).coerceIn(0, levelCount.coerceAtLeast(0))

    /**
     * Stars earned across the whole season, bounded by [levelCount] and summed on demand
     * rather than stored — a season is finite and brand new, so there is no historical
     * total to migrate the way endless Levels' lifetime counter needed one.
     */
    suspend fun totalStars(levelCount: Int): Int {
        if (levelCount < 1) return 0
        return (1..levelCount).sumOf { prefs.seasonStars(seasonId, it).first() }
    }

    /** The level to offer after clearing [level], or null when that was the season's last one. */
    fun nextLevel(after: Int, levelCount: Int): Int? = if (after + 1 <= levelCount) after + 1 else null

    /**
     * Records the result of a season level attempt. A win may raise (never lower) the
     * level's stored star rating and unlocks the next level; only the *improvement* is
     * credited to the wallet, so replaying a cleared level for the same rating is not free
     * currency. Returns the awarded rating on a win, 0 on a loss (mirrors the
     * [com.ezequielbrrt.domemory.services.levels.LevelProgressStore] contract exactly).
     */
    fun recordCompletion(level: Int, didWin: Boolean, timeRemaining: Double, totalTime: Double, failedTries: Int): Int {
        if (!didWin) return 0
        val award = Stars.award(timeRemaining, totalTime, failedTries)
        scope.launch {
            val result = prefs.recordSeasonLevelCompletion(seasonId, level, award)
            starsCache[level] = maxOf(starsCache[level] ?: 0, award)
            highest = maxOf(highest, result.highestUnlocked)
            _revision.value++
        }
        return award
    }

    /**
     * Unlocks the next level without clearing this one. No stars are stored, so the
     * skipped level renders as cleared-with-0-stars and stays replayable for credit later.
     */
    fun skipLevel(level: Int) {
        if (!isUnlocked(level)) return
        scope.launch {
            highest = prefs.unlockSeasonLevelAtLeast(seasonId, level + 1)
            _revision.value++
        }
    }
}
