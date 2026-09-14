package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Endless-level implementation of [LevelProgressStore]. Seasons get their own store later.
 *
 * DataStore is asynchronous end to end, but [LevelProgressStore] is a synchronous
 * interface — [GameViewModel][com.ezequielbrrt.domemory.feature.game.GameViewModel] and
 * `LevelsScreen` both read [stars] and [highestUnlockedLevel] as plain properties from
 * Compose, the same way iOS reads a synchronous `UserDefaults` integer. [ratings] and
 * [highestRef] are the bridge: an in-memory cache that a fetch coroutine fills in and every
 * mutation (`recordCompletion`, `skipLevel`) updates directly, in the *same* coroutine
 * that performs the write — not a separately-launched observer of a `StateFlow` derived
 * from DataStore's own `data` flow. That distinction matters: a `stateIn(..., Eagerly,
 * ...)` bridge was tried first and reads correctly in production, but its update never
 * reliably became visible to `advanceUntilIdle()` in tests — the write and the "cache
 * observes the write" step are two independently-scheduled coroutines connected only
 * through DataStore's internal flow machinery, and virtual-time test dispatchers don't
 * deterministically settle that. Updating the cache inline, right after the same
 * `dataStore.edit` call that performed the write, removes the second coroutine entirely.
 *
 * The two real bugs an earlier version of this cache had, that this one fixes without
 * touching that shape:
 *  - **No de-duplication.** Every call to `stars(level)` for an unloaded level queued
 *    *another* redundant disk read instead of joining the one already in flight.
 *    [loadingLevels] (a concurrent set, `.add()` racing safely) now guarantees exactly
 *    one fetch per level no matter how many callers ask for it at once.
 *  - **No synchronization.** The old cache was a plain `mutableMapOf` and a plain `var`,
 *    mutated from coroutines launched on [scope] — in production,
 *    `AppContainer.applicationScope` is `Dispatchers.IO`, a real thread pool, not a
 *    confined thread. [ratings] is a [ConcurrentHashMap] and [highestRef] an
 *    [AtomicInteger], updated with a real compare-and-set ([raiseHighestTo]) rather than
 *    a plain read-then-write that two racing completions could turn into a lost update.
 *
 * [init] additionally warms the cache for every level already cleared before this
 * instance existed, the same way `highestUnlockedLevel` itself is warmed — without it, the *first*
 * `stars(level)` call for an already-cleared level would flash a stale 0 for one
 * dispatcher hop after every cold start.
 */
class LevelProgressService(
    private val prefs: UserPreferences,
    private val scope: CoroutineScope,
) : LevelProgressStore {

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val ratings = ConcurrentHashMap<Int, Int>()
    private val loadingLevels: MutableSet<Int> = Collections.newSetFromMap(ConcurrentHashMap())

    /** Atomic, not `@Volatile`, so the "raise but never lower" update below (racing
     * completions on different levels, each unlocking a different next level) is a real
     * compare-and-set rather than a plain read-then-write that could lose an update. */
    private val highestRef = AtomicInteger(1)

    init {
        scope.launch {
            val bootHighest = prefs.levelsHighestUnlocked.first().coerceAtLeast(1)
            for (level in 1 until bootHighest) {
                ratings[level] = prefs.levelStars(level).first()
            }
            raiseHighestTo(bootHighest)
            _revision.update { it + 1 }
        }
    }

    override val highestUnlockedLevel: Int get() = highestRef.get()

    private fun raiseHighestTo(candidate: Int) {
        highestRef.updateAndGet { current -> maxOf(current, candidate) }
    }

    override fun stars(level: Int): Int {
        ratings[level]?.let { return it }
        if (loadingLevels.add(level)) {
            scope.launch {
                val value = prefs.levelStars(level).first()
                ratings.putIfAbsent(level, value)
                loadingLevels.remove(level)
                _revision.update { it + 1 }
            }
        }
        return ratings[level] ?: 0
    }

    override fun isUnlocked(level: Int): Boolean = level in 1..highestUnlockedLevel

    override fun board(level: Int): Board = BoardGenerators.endlessLevel(level)

    /**
     * Records a finished attempt. A win may raise (never lower) the level's stored
     * rating; the return value is the level's star count *after* recording — the
     * high-water mark, not necessarily this run's own rating — mirroring iOS's
     * `recordCompletion`. A loss changes nothing and echoes the level's current rating.
     */
    override fun recordCompletion(level: Int, didWin: Boolean, timeRemaining: Double, totalTime: Double, failedTries: Int): Int {
        if (!didWin) return stars(level)
        val existing = stars(level)
        val award = Stars.award(timeRemaining, totalTime, failedTries)
        scope.launch {
            val result = prefs.recordLevelCompletion(level, award)
            ratings.merge(level, award) { old, new -> maxOf(old, new) }
            raiseHighestTo(result.highestUnlocked)
            _revision.update { it + 1 }
        }
        return maxOf(existing, award)
    }

    override fun skipLevel(level: Int) {
        if (!isUnlocked(level)) return
        scope.launch {
            // maxOf against the live cache at write time, not a snapshot taken before this
            // coroutine was scheduled — a concurrent completion could have raised
            // highestUnlockedLevel past `level + 1` in the meantime.
            val next = maxOf(highestUnlockedLevel, level + 1)
            prefs.setLevelsHighestUnlocked(next)
            raiseHighestTo(next)
            _revision.update { it + 1 }
        }
    }

    override fun nextLevel(after: Int): Int = after + 1
}
