package com.ezequielbrrt.domemory.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.data.repository.BoardCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The real menu (spec 2), replacing `Phase1Root` + `BoardPickerScreen`. Owns tab
 * selection, difficulty filtering, favourites and custom-memorama create/delete —
 * everything in scope for this task except Levels itself, which is a placeholder tile.
 *
 * There is no Settings screen yet to hold a difficulty picker (that is a separate, later
 * task), so the difficulty control lives inline on the "All" tab for now — the only
 * place in this phase a player can change it. It persists through
 * [UserPreferences.playerDifficulty] rather than the old in-memory
 * `AppContainer.playerDifficulty`, so it survives process death.
 *
 * State is rebuilt by an explicit pull ([refreshFromPrefs]) after every mutation, rather
 * than a continuously collected [kotlinx.coroutines.flow.combine] — mirroring
 * [BoardCatalogRepository]'s own pull model for the same reason: this view model is the
 * *only* writer of favourites, difficulty and custom boards, so nothing external needs
 * to be observed, and a pull keeps every mutation (and its test) a single, deterministic
 * suspend chain instead of a race against a background collector's dispatcher.
 */
class MenuViewModel(
    private val catalog: BoardCatalogRepository,
    private val prefs: UserPreferences,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    private val workScope: CoroutineScope get() = scope ?: viewModelScope

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    init {
        workScope.launch {
            catalog.refresh()
            catalog.refreshCustomBoards()
            refreshFromPrefs()
        }
    }

    fun selectTab(tab: MenuTab) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    /** Spec 4: filters the All tab and seeds the next game's clock and pie. */
    fun setDifficulty(difficulty: Difficulty) {
        if (difficulty == _state.value.difficulty) return
        workScope.launch {
            prefs.setPlayerDifficulty(difficulty)
            refreshFromPrefs()
        }
    }

    fun toggleFavorite(boardId: String) {
        workScope.launch {
            prefs.toggleFavorite(boardId)
            refreshFromPrefs()
        }
    }

    /** Deleting also clears the board's stats and favourite (spec 13.3) — see [UserPreferences.removeCustomMemorama]. */
    fun deleteCustomMemorama(boardId: String) {
        workScope.launch {
            prefs.removeCustomMemorama(boardId)
            catalog.refreshCustomBoards()
            refreshFromPrefs()
        }
    }

    /** Call after a memorama is created elsewhere (the create screen) to pick it up. */
    fun onCustomMemoramaChanged() {
        workScope.launch {
            catalog.refreshCustomBoards()
            refreshFromPrefs()
        }
    }

    /** Settings changed the difficulty: mirror iOS by re-fetching and re-filtering once. */
    fun onSettingsDifficultyChanged() {
        workScope.launch {
            catalog.refresh()
            catalog.refreshCustomBoards()
            refreshFromPrefs()
        }
    }

    fun board(id: String): Board? = catalog.board(id)

    private suspend fun refreshFromPrefs() {
        val favoriteIds = prefs.favoriteIds.first()
        val difficulty = prefs.playerDifficulty.first()
        _state.value = _state.value.copy(
            difficulty = difficulty,
            catalogStatus = catalog.status.value,
            allBoards = catalog.catalogBoards(difficulty).favoritesFirst(favoriteIds),
            myBoards = catalog.customBoards().favoritesFirst(favoriteIds),
            favoriteIds = favoriteIds,
        )
    }
}
