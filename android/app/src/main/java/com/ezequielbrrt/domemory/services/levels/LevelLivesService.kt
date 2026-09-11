package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first

/** Shared endless/season daily-life budget. Reset is lazy on the first access each day. */
class LevelLivesService(private val prefs: UserPreferences, private val dayProvider: DayProvider) {
    suspend fun remaining(): Int {
        val today = DayKey.of(dayProvider.today())
        if (prefs.levelsLivesLastResetDay.first() != today) {
            prefs.setLevelsLivesRemaining(MAX_LIVES)
            prefs.setLevelsLivesLastResetDay(today)
        }
        return prefs.levelsLivesRemaining.first()
    }

    suspend fun spendOnLoss(): Boolean {
        val lives = remaining()
        if (lives == 0) return false
        prefs.setLevelsLivesRemaining(lives - 1)
        return true
    }

    suspend fun refill(amount: Int = 1): Int {
        val updated = (remaining() + amount).coerceAtMost(MAX_LIVES)
        prefs.setLevelsLivesRemaining(updated)
        return updated
    }

    companion object { const val MAX_LIVES = 4 }
}
