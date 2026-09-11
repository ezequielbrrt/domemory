package com.ezequielbrrt.domemory.data.repository

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** What the last catalog load did, so the UI can distinguish empty from broken. */
enum class CatalogStatus { IDLE, LOADING, LOADED, UNAVAILABLE }

/**
 * Holds the catalog for the session and filters it (spec 13.1).
 *
 * The catalog is **shuffled once per load**, then filtered to the player's difficulty
 * and merged with local custom boards. Custom boards ignore difficulty filtering — they
 * always appear in "My memoramas" regardless of the current setting (spec 13.3).
 *
 * [fallback] is what the app shows when the remote catalog is unavailable. It is not a
 * cache: the spec's rule is that catalog failure is silent and non-fatal, and a player
 * who has never had a successful load still gets a playable app.
 *
 * [customBoardsSource] is the real source of truth for custom boards — production wires
 * it to `UserPreferences.customMemoramas` (a `Flow`, pulled via `.first()`). It is a
 * suspend snapshot rather than a continuously collected `Flow`, deliberately mirroring
 * [refresh]'s own pull model for the remote catalog: read once, cache in a `StateFlow`,
 * re-read on [refreshCustomBoards] after a mutation. This replaces the old in-memory
 * `setCustomBoards()` this class carried through Phase 1.
 */
class BoardCatalogRepository(
    private val remote: BoardCatalogSource,
    private val fallback: BoardCatalogSource = BundledBoardCatalogSource(),
    private val customBoardsSource: suspend () -> List<Board> = { emptyList() },
    private val random: Random = Random.Default,
) {
    private val _boards = MutableStateFlow<List<Board>>(emptyList())
    val boards: StateFlow<List<Board>> = _boards.asStateFlow()

    private val _status = MutableStateFlow(CatalogStatus.IDLE)
    val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    private val _customBoards = MutableStateFlow<List<Board>>(emptyList())
    val customBoardsFlow: StateFlow<List<Board>> = _customBoards.asStateFlow()

    suspend fun refresh(): CatalogStatus {
        _status.value = CatalogStatus.LOADING
        val loaded = remote.load()
        return if (loaded.isEmpty()) {
            _boards.value = fallback.load().shuffled(random)
            CatalogStatus.UNAVAILABLE
        } else {
            _boards.value = loaded.shuffled(random)
            CatalogStatus.LOADED
        }.also { _status.value = it }
    }

    /** Re-reads [customBoardsSource]. Call once on menu load and after any add/remove. */
    suspend fun refreshCustomBoards() {
        _customBoards.value = customBoardsSource().filter { it.isCustom }
    }

    /** Catalog boards matching [difficulty], plus every custom board. */
    fun boards(difficulty: Difficulty): List<Board> =
        catalogBoards(difficulty) + _customBoards.value

    fun catalogBoards(difficulty: Difficulty): List<Board> =
        _boards.value.filter { Difficulty.parse(it.difficulty) == difficulty }

    fun customBoards(): List<Board> = _customBoards.value

    fun board(id: String): Board? =
        _boards.value.firstOrNull { it.id == id } ?: _customBoards.value.firstOrNull { it.id == id }
}
