package com.ezequielbrrt.domemory.data.repository

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.core.rng.EmojiPool

/**
 * Where the board catalog comes from (spec 13.1). One snapshot read per menu load.
 *
 * Failure is silent and non-fatal by contract: an implementation that cannot reach
 * Firebase, cannot authenticate, or gets an unexpected payload returns an **empty
 * list** rather than throwing. Levels, Daily and custom boards all still work, so the
 * app stays usable offline.
 */
fun interface BoardCatalogSource {
    suspend fun load(): List<Board>
}

/**
 * Phase 1 stand-in, kept as the offline/dev source. Boards are sized to their declared
 * difficulty the way the real catalog is — difficulty is expressed through board size,
 * not the clock alone.
 */
class BundledBoardCatalogSource : BoardCatalogSource {

    private val catalog: List<Board> = listOf(
        sample("bundled_1", "Faces", Difficulty.EASY, 0, 4),
        sample("bundled_2", "Animals", Difficulty.EASY, 9, 4),
        sample("bundled_3", "Wildlife", Difficulty.MEDIUM, 9, 6),
        sample("bundled_4", "Nature", Difficulty.MEDIUM, 23, 6),
        sample("bundled_5", "Food", Difficulty.HARD, 26, 10),
        sample("bundled_6", "Treats", Difficulty.HARD, 30, 10),
        sample("bundled_7", "Everything", Difficulty.VERY_HARD, 34, 12),
        sample("bundled_8", "Grand tour", Difficulty.VERY_HARD, 0, 12),
        // No board in the real catalog uses the adjacency constructor, but the model
        // and multiplayer both support it, so one is kept alive here to exercise it.
        Board(
            id = "bundled_9",
            name = "Pairs by adjacency",
            category = "demo",
            difficulty = Difficulty.MEDIUM.key,
            items = listOf("🌞", "☀️", "🌛", "🌜", "🐺", "🐶", "🌊", "💧"),
            isDoubleItem = false,
        ),
    )

    override suspend fun load(): List<Board> = catalog

    private fun sample(
        id: String,
        name: String,
        difficulty: Difficulty,
        offset: Int,
        pairs: Int,
    ) = Board(
        id = id,
        name = name,
        category = "emoji",
        description = "emoji",
        difficulty = difficulty.key,
        items = List(pairs) { EmojiPool.all[(offset + it) % EmojiPool.size] },
        itemType = "String",
        isDoubleItem = true,
    )
}
