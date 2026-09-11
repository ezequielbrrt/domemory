package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Endless-level implementation of [LevelProgressStore]. Seasons get their own store later. */
class LevelProgressService(
    private val prefs: UserPreferences,
    private val scope: CoroutineScope,
) : LevelProgressStore {
    private val ratings = mutableMapOf<Int, Int>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()
    private var highest = 1

    init {
        scope.launch {
            highest = prefs.levelsHighestUnlocked.first().coerceAtLeast(1)
            _revision.value++
        }
    }

    override val highestUnlockedLevel: Int get() = highest

    override fun stars(level: Int): Int {
        if (level !in ratings) scope.launch {
            ratings[level] = prefs.levelStars(level).first()
            _revision.value++
        }
        return ratings[level] ?: 0
    }

    override fun isUnlocked(level: Int): Boolean = level in 1..highest

    override fun board(level: Int): Board = BoardGenerators.endlessLevel(level)

    override fun recordCompletion(level: Int, didWin: Boolean, timeRemaining: Double, totalTime: Double, failedTries: Int): Int {
        if (!didWin) return 0
        val award = Stars.award(timeRemaining, totalTime, failedTries)
        scope.launch {
            val result = prefs.recordLevelCompletion(level, award)
            ratings[level] = maxOf(ratings[level] ?: 0, award)
            highest = maxOf(highest, result.highestUnlocked)
            _revision.value++
        }
        return award
    }

    override fun skipLevel(level: Int) {
        if (!isUnlocked(level)) return
        scope.launch {
            val next = maxOf(highest, level + 1)
            prefs.setLevelsHighestUnlocked(next)
            highest = next
            _revision.value++
        }
    }

    override fun nextLevel(after: Int): Int = after + 1
}
