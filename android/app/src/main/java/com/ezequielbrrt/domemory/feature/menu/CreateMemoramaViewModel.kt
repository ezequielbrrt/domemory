package com.ezequielbrrt.domemory.feature.menu

import androidx.lifecycle.ViewModel
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * A name, then 2+ emoji added one at a time (spec 13.3). [UserPreferences.addCustomMemorama]
 * already rejects a board with fewer than two items; [canSave] disables the submit button
 * before that ever happens, and [save] still surfaces a `false` return as [rejected]
 * rather than silently swallowing it, in case the two ever disagree.
 */
data class CreateMemoramaUiState(
    val name: String = "",
    val emojiInput: String = "",
    val items: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val rejected: Boolean = false,
) {
    val canSave: Boolean get() = items.size >= 2 && !isSaving
}

class CreateMemoramaViewModel(private val prefs: UserPreferences) : ViewModel() {

    private val _state = MutableStateFlow(CreateMemoramaUiState())
    val state: StateFlow<CreateMemoramaUiState> = _state.asStateFlow()

    fun updateName(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    fun updateEmojiInput(value: String) {
        _state.value = _state.value.copy(emojiInput = value)
    }

    /** Adds the current input as one item, "one at a time" per spec 13.3. */
    fun addEmoji() {
        val trimmed = _state.value.emojiInput.trim()
        if (trimmed.isEmpty()) return
        _state.value = _state.value.copy(
            items = _state.value.items + trimmed,
            emojiInput = "",
        )
    }

    fun removeEmoji(index: Int) {
        _state.value = _state.value.copy(
            items = _state.value.items.filterIndexed { i, _ -> i != index },
        )
    }

    /** Returns true on success, so the caller knows whether to navigate back. */
    suspend fun save(): Boolean {
        val current = _state.value
        if (!current.canSave) return false

        _state.value = current.copy(isSaving = true, rejected = false)
        val board = Board(
            id = Board.CUSTOM_ID_PREFIX + UUID.randomUUID().toString(),
            name = current.name.trim(),
            category = "custom",
            items = current.items,
        )
        val added = prefs.addCustomMemorama(board)
        _state.value = _state.value.copy(isSaving = false, rejected = !added)
        if (added) {
            // A custom board carries no difficulty of its own — falls back to the player's
            // setting, the same resolution every other analytics/gameplay read of a board's
            // difficulty already uses (see [Board.resolvedDifficulty]'s own doc).
            val difficulty = board.resolvedDifficulty(prefs.playerDifficulty.first())
            AnalyticsService.log(
                AnalyticsEvent.CustomMemoramaCreated(
                    gameId = board.id,
                    difficulty = difficulty.key,
                    cardsCount = board.items.size,
                ),
            )
        }
        return added
    }
}
