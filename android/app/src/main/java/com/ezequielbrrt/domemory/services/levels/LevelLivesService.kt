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

    suspend fun spendOnLoss(): Boolean {
        return prefs.trySpendLevelLife(DayKey.of(dayProvider.today()))
    }

    suspend fun refill(amount: Int = 1): Int {
        return prefs.refillLevelLives(DayKey.of(dayProvider.today()), amount)
    }

    companion object { const val MAX_LIVES = 4 }
}
