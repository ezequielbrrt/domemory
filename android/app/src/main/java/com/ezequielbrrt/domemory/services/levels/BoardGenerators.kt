package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.rng.EmojiPool
import com.ezequielbrrt.domemory.core.rng.SeededGenerator

/**
 * The three deterministic generators (spec 6.3). All produce `isDoubleItem = true`,
 * `difficulty = "medium"`, `itemType = "string"`.
 *
 * | Feature         | Seed                        | Pool                | Pairs               |
 * |-----------------|-----------------------------|---------------------|---------------------|
 * | Daily Challenge | `YYYYMMDD` (local calendar) | EmojiPool.all       | 6, fixed            |
 * | Endless Levels  | `level-{n}`                 | EmojiPool.all       | LevelCurve.pairs(n) |
 * | Season Levels   | `season-{id}-{n}`           | the season's pool   | LevelCurve.pairs(n) |
 */
object BoardGenerators {

    const val DAILY_PAIR_COUNT = 6

    fun daily(dayKey: String): Board = generate(
        id = "daily_$dayKey",
        name = "Daily Challenge",
        category = "daily",
        seed = dayKey,
        pool = EmojiPool.all,
        pairCount = DAILY_PAIR_COUNT,
    )

    fun endlessLevel(level: Int): Board = generate(
        id = "level_$level",
        name = "Level $level",
        category = "level",
        seed = "level-$level",
        pool = EmojiPool.all,
        pairCount = LevelCurve.pairs(level),
    )

    fun seasonLevel(seasonId: String, level: Int, pool: List<String>): Board = generate(
        id = "season_${seasonId}_$level",
        name = "Level $level",
        category = "season",
        seed = "season-$seasonId-$level",
        pool = pool,
        pairCount = LevelCurve.pairs(level),
    )

    private fun generate(
        id: String,
        name: String,
        category: String,
        seed: String,
        pool: List<String>,
        pairCount: Int,
    ): Board {
        require(pool.size >= pairCount) {
            "pool of ${pool.size} cannot fill $pairCount pairs"
        }
        val items = SeededGenerator(seed).shuffled(pool).take(pairCount)
        return Board(
            id = id,
            name = name,
            category = category,
            description = category,
            difficulty = "medium",
            items = items,
            itemType = "string",
            isDoubleItem = true,
        )
    }
}
