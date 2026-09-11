package com.ezequielbrrt.domemory.feature.game

import com.ezequielbrrt.domemory.data.prefs.UserPreferences

/**
 * Production [GameStatsRecorder]: the two calls spec 13.2 actually asks for. A loss
 * records "played" without "won" — only the second call is conditioned on the outcome.
 */
class UserPreferencesGameStatsRecorder(private val prefs: UserPreferences) : GameStatsRecorder {
    override suspend fun recordFinished(boardId: String, didWin: Boolean) {
        prefs.recordBoardPlayed(boardId)
        if (didWin) prefs.recordBoardWon(boardId)
    }
}
