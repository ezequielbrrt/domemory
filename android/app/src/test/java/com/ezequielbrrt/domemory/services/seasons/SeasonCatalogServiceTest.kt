package com.ezequielbrrt.domemory.services.seasons

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SeasonCatalogServiceTest {
    private val catalog = """{"spooky":{"enabled":true,"startDate":"2026-09-01","endDate":"2026-11-01","levelCount":1,"emojiPool":["1","2","3","4","5","6","7","8","9","10","11","12"]}}"""
    @Test fun `cache loads before network and a failed refresh preserves it`() = runTest {
        val file = File.createTempFile("seasons", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        prefs.setSeasonCatalogJson(catalog)
        val service = SeasonCatalogService(prefs) { null }
        assertEquals(listOf("spooky"), service.loadCached().map { it.id })
        assertEquals(listOf("spooky"), service.refresh().map { it.id })
    }
}
