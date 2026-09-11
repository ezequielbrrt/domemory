package com.ezequielbrrt.domemory.core.model

import kotlin.random.Random

/**
 * A card set as it arrives from the catalog, from local storage, or from a generator
 * (spec 3.1 / 13.1). Ids prefixed `custom_` are player-authored (spec 13.3).
 */
data class Board(
    val id: String,
    val name: String,
    val category: String = "",
    val description: String = "",
    /** Raw difficulty string from the payload; may be absent or unparseable. */
    val difficulty: String? = null,
    val publishedDate: String? = null,
    val items: List<String>,
    val itemType: String = "String",
    val isDoubleItem: Boolean = true,
) {
    val isCustom: Boolean get() = id.startsWith(CUSTOM_ID_PREFIX)

    /** The board's own difficulty, falling back to the player's setting (spec 4). */
    fun resolvedDifficulty(playerDifficulty: Difficulty): Difficulty =
        Difficulty.parse(difficulty) ?: playerDifficulty

    val pairCount: Int
        get() = if (isDoubleItem) items.size else items.size / 2

    /**
     * Builds the shuffled card array (spec 3.1).
     *
     * Two constructors, selected by [isDoubleItem]:
     *  - `true`  — each item yields **two** cards sharing that item's index as itemId.
     *              N items -> 2N cards -> N pairs. Every board in the catalog is this.
     *  - `false` — each item yields **one** card and items pair by *adjacency*:
     *              0&1 match, 2&3 match, ... N items -> N cards -> N/2 pairs.
     *              For pairs whose two faces differ (a word and a picture). Unused by
     *              the current catalog, but the model and multiplayer both support it.
     *
     * The shuffle happens per play even for deterministic boards: the *content* is
     * deterministic, the *layout* never is, so a shared screenshot cannot be used to
     * memorize positions.
     */
    fun buildCards(random: Random = Random.Default): List<Card> {
        val cards = if (isDoubleItem) {
            items.flatMapIndexed { itemIndex, content ->
                listOf(
                    Card(id = itemIndex * 2, itemId = itemIndex, content = content),
                    Card(id = itemIndex * 2 + 1, itemId = itemIndex, content = content),
                )
            }
        } else {
            items.mapIndexed { pairIndex, content ->
                Card(
                    id = pairIndex,
                    itemId = if (pairIndex % 2 == 0) pairIndex else pairIndex - 1,
                    content = content,
                )
            }
        }
        return cards.shuffled(random)
    }

    companion object {
        const val CUSTOM_ID_PREFIX = "custom_"
    }
}
