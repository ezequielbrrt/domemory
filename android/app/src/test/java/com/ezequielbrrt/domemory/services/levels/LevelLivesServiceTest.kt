package com.ezequielbrrt.domemory.services.levels

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.time.DayProvider
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class LevelLivesServiceTest {
    @Test fun `lives reset lazily, only losses spend them, and refills cap at four`() = runTest {
        val file = File.createTempFile("lives", ".preferences_pb").also { it.deleteOnExit() }
        var day = LocalDate.of(2026, 9, 11)
        val service = LevelLivesService(UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file })), DayProvider { day })
        assertEquals(4, service.remaining())
        repeat(4) { assertTrue(service.spendOnLoss()) }; assertFalse(service.spendOnLoss())
        assertEquals(1, service.refill()); assertEquals(4, service.refill(99))
        day = day.plusDays(1); assertEquals(4, service.remaining())
    }
}
