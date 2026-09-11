package com.ezequielbrrt.domemory.services.seasons

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeasonCatalogServiceTest {
    private val catalog = """{"spooky":{"enabled":true,"startDate":"2026-09-01","endDate":"2026-11-01","levelCount":1,"emojiPool":["1","2","3","4","5","6","7","8","9","10","11","12"]}}"""

    private fun newService(remote: SeasonCatalogSource): SeasonCatalogService {
        val file = File.createTempFile("seasons", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        return SeasonCatalogService(prefs, remote)
    }

    @Test fun `cache loads before network and a failed refresh preserves it`() = runTest {
        val file = File.createTempFile("seasons", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        prefs.setSeasonCatalogJson(catalog)
        val service = SeasonCatalogService(prefs) { null }
        assertEquals(listOf("spooky"), service.loadCached().map { it.id })
        assertEquals(listOf("spooky"), service.refresh().map { it.id })
    }

    @Test fun `a non-empty remote payload corrects and persists over the cache`() = runTest {
        val file = File.createTempFile("seasons", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        val newCatalog = """{"winter":{"enabled":true,"startDate":"2026-12-01","endDate":"2026-12-31","levelCount":1,"emojiPool":["1","2","3","4","5","6","7","8","9","10","11","12"]}}"""
        val service = SeasonCatalogService(prefs) { newCatalog }
        prefs.setSeasonCatalogJson(catalog)
        assertEquals(listOf("spooky"), service.loadCached().map { it.id })
        assertEquals(listOf("winter"), service.refresh().map { it.id })
        // The correction is persisted, not just held in memory.
        assertEquals(newCatalog, prefs.seasonCatalogJson.first())
    }

    @Test fun `a malformed remote payload decodes empty and leaves the cache in place`() = runTest {
        val file = File.createTempFile("seasons", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
        val service = SeasonCatalogService(prefs) { "not json" }
        prefs.setSeasonCatalogJson(catalog)
        service.loadCached()
        assertEquals(listOf("spooky"), service.refresh().map { it.id })
        assertEquals(catalog, prefs.seasonCatalogJson.first())
    }

    @Test fun `activeSeason reflects the priority and id tie-break and re-evaluates without a network round trip`() = runTest {
        val raw = """{"z":{"enabled":true,"startDate":"2026-09-01","endDate":"2026-09-10","priority":1,"levelCount":1,"emojiPool":["1","2","3","4","5","6","7","8","9","10","11","12"]},"a":{"enabled":true,"startDate":"2026-09-01","endDate":"2026-09-10","priority":1,"levelCount":1,"emojiPool":["1","2","3","4","5","6","7","8","9","10","11","12"]}}"""
        val service = newService(remote = SeasonCatalogSource { raw })
        service.refresh(today = "20260901")
        assertEquals("a", service.activeSeason.value?.id)
        service.refreshActive(today = "20260911")
        assertNull(service.activeSeason.value)
    }

    @Test fun `the real seasons fixture decodes and validates end to end`() = runTest {
        val raw = checkNotNull(javaClass.classLoader?.getResourceAsStream("seasons.json"))
            .bufferedReader().use { it.readText() }
        val service = newService(remote = SeasonCatalogSource { raw })
        val seasons = service.refresh(today = "20260915")
        val spooky = seasons.single { it.id == "spooky-2026" }
        assertEquals(30, spooky.levelCount)
        assertEquals("🎃", spooky.icon)
        assertEquals("#FF6B1A", spooky.accentColor)
        assertTrue(spooky.backgroundImageURL!!.startsWith("https://"))
        assertTrue(spooky.backgroundImageURLDark!!.startsWith("https://"))
        assertTrue(spooky.cardImageURL!!.startsWith("https://"))
        assertEquals(12, spooky.emojiPool.size)
        assertTrue(spooky.isActive("20260915"))
        assertEquals(spooky.id, service.activeSeason.value?.id)
    }
}
