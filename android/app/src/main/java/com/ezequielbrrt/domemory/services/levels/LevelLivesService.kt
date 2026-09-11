package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first

/** Shared endless/season daily-life budget. Reset is lazy on the first access each day. */
class LevelLivesService(private val prefs: UserPreferences, private val dayProvider: DayProvider) {
    suspend fun remaining(): Int {
        return prefs.levelLivesFor(DayKey.of(dayProvider.today()))
    }

    /**
     * Gate for starting a level attempt (spec 7.4): at 0 lives, tapping a level tile must
     * be refused rather than starting a game the player has no budget for.
     */
    suspend fun hasLivesRemaining(): Boolean = remaining() > 0

    suspend fun spendOnLoss(): Boolean {
        return prefs.trySpendLevelLife(DayKey.of(dayProvider.today()))
    }

    suspend fun refill(amount: Int = 1): Int {
        return prefs.refillLevelLives(DayKey.of(dayProvider.today()), amount)
    }

    companion object { const val MAX_LIVES = 4 }
}
