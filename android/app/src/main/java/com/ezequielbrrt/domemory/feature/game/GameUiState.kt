package com.ezequielbrrt.domemory.feature.game

import com.ezequielbrrt.domemory.core.model.Card
import com.ezequielbrrt.domemory.core.model.Difficulty

enum class LoseReason { OUT_OF_TIME, TOO_MANY_MISTAKES }

sealed interface GameOutcome {
    data object Won : GameOutcome
    data class Lost(val reason: LoseReason) : GameOutcome
}

data class GameUiState(
    val boardName: String = "",
    val cards: List<Card> = emptyList(),
    /** Ids of matched cards that have finished their hide delay and left the board. */
    val hiddenCardIds: Set<Int> = emptySet(),
    val columns: Int = 1,
    val totalTime: Double = 0.0,
    val timeRemaining: Double = 0.0,
    val failedTries: Int = 0,
    val maxFailures: Int? = null,
    val matchedPairs: Int = 0,
    val totalPairs: Int = 0,
    val isPaused: Boolean = false,
    val isFrozen: Boolean = false,
    val showsPie: Boolean = false,
    /** The difficulty reported to analytics and shown on the win screen (spec 4). */
    val recordedDifficulty: Difficulty = Difficulty.MEDIUM,
    val outcome: GameOutcome? = null,
) {
    val isFinished: Boolean get() = outcome != null
    val timeFraction: Float
        get() = if (totalTime <= 0.0) 0f else (timeRemaining / totalTime).toFloat()
    val mistakesAreCritical: Boolean
        get() = maxFailures != null && maxFailures - failedTries <= 2
}
