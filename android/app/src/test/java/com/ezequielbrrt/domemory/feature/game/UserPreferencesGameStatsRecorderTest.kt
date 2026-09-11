package com.ezequielbrrt.domemory.feature.game

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Verifies the production recorder maps a finish to the typed preference counters. */
class UserPreferencesGameStatsRecorderTest {

    private fun newPrefs(): UserPreferences {
        val file = File.createTempFile("game_stats_recorder_test", ".preferences_pb")
        file.deleteOnExit()
        return UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun `a win records played and won while a loss records only played`() = runTest {
        val prefs = newPrefs()
        val recorder = UserPreferencesGameStatsRecorder(prefs)

        recorder.recordFinished(boardId = "won", didWin = true)
        recorder.recordFinished(boardId = "lost", didWin = false)

        assertEquals(1, prefs.boardPlayedCount("won").first())
        assertEquals(1, prefs.boardWonCount("won").first())
        assertEquals(1, prefs.boardPlayedCount("lost").first())
        assertEquals(0, prefs.boardWonCount("lost").first())
    }
}
