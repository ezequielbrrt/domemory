package com.ezequielbrrt.domemory.data.remote

import com.ezequielbrrt.domemory.services.seasons.SeasonDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [FirebaseSeasonCatalogSource] cannot be unit tested directly (it needs a live
 * `FirebaseDatabase`/`FirebaseAuth`, mirroring why there is no `FirebaseBoardCatalogSourceTest`
 * either), but the shape conversion it depends on — `DataSnapshot.value`'s
 * `Map<String, Any?>` with nested `Map`/`List` children, turned into the JSON text
 * `SeasonDecoder.decode` expects — is pure and is exactly what would silently break the
 * "a season published to Firebase appears... with no app change" exit criterion.
 */
class SeasonPayloadJsonTest {
    @Test fun `a nested map-of-maps round-trips into decodable JSON`() {
        val snapshotValue: Map<String, Any?> = mapOf(
            "spooky" to mapOf(
                "enabled" to true,
                "levelCount" to 2L,
                "priority" to 10L,
                "emojiPool" to listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"),
                "strings" to mapOf(
                    "en" to mapOf("title" to "Spooky Season", "subtitle" to "2 levels"),
                ),
            ),
        )

        val json = SeasonPayloadJson.encode(snapshotValue)
        val seasons = SeasonDecoder.decode(checkNotNull(json))

        assertEquals(listOf("spooky"), seasons.map { it.id })
        val season = seasons.single()
        assertEquals(2, season.levelCount)
        assertEquals(10, season.priority)
        assertEquals("Spooky Season", season.title["en"])
        assertEquals("2 levels", season.subtitle["en"])
    }

    @Test fun `a non-map value (an absent or unexpected node) encodes to null`() {
        assertNull(SeasonPayloadJson.encode(null))
        assertNull(SeasonPayloadJson.encode(listOf("not", "a", "map")))
        assertNull(SeasonPayloadJson.encode("a bare string"))
    }

    @Test fun `an empty node encodes to an empty but valid object`() {
        val json = checkNotNull(SeasonPayloadJson.encode(emptyMap<String, Any?>()))
        assertEquals(emptyList<String>(), SeasonDecoder.decode(json).map { it.id })
    }
}
