package com.ezequielbrrt.domemory.services.seasons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spec 9.3 validation table, one test per rule. Fails **closed** on structure
 * (the season disappears entirely); falls **back** on decoration (the season survives
 * with a null/defaulted field). A season failing one rule must never cost a valid sibling
 * — every test here decodes a `bad`/`bare` season alongside a `good` one.
 */
class SeasonTest {
    private val pool = (1..12).joinToString(",") { "\"e$it\"" }
    private fun season(body: String): Season =
        SeasonDecoder.decode("""{"s":{$body}}""").single()

    // -- enabled: absent => false -------------------------------------------------------

    @Test fun `enabled absent decodes as false`() {
        val s = season(""""levelCount":1,"emojiPool":[$pool]""")
        assertFalse(s.enabled)
    }

    @Test fun `enabled explicit true is preserved`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool]""")
        assertTrue(s.enabled)
    }

    // -- levelCount: absent or < 1 => reject ---------------------------------------------

    @Test fun `levelCount under 1 rejects the season while a valid sibling survives`() {
        val seasons = SeasonDecoder.decode(
            """{"bad":{"enabled":true,"levelCount":0,"emojiPool":[$pool]},"good":{"enabled":true,"levelCount":1,"emojiPool":[$pool]}}""",
        )
        assertEquals(listOf("good"), seasons.map { it.id })
    }

    @Test fun `levelCount absent rejects the season`() {
        val seasons = SeasonDecoder.decode("""{"bad":{"enabled":true,"emojiPool":[$pool]}}""")
        assertTrue(seasons.isEmpty())
    }

    // -- emojiPool: dedup preserving order, drop empties, fewer than 12 distinct => reject --

    @Test fun `emoji pool is deduplicated preserving order and empties are dropped`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":["a","","b","a","c","d","e","f","g","h","i","j","k","l"]""")
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"), s.emojiPool)
    }

    @Test fun `fewer than twelve distinct emoji rejects the season`() {
        val seasons = SeasonDecoder.decode(
            """{"bad":{"enabled":true,"levelCount":1,"emojiPool":["a","b","a"]}}""",
        )
        assertTrue(seasons.isEmpty())
    }

    @Test fun `exactly twelve distinct emoji is accepted`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool]""")
        assertEquals(12, s.emojiPool.size)
    }

    // -- startDate/endDate: strict YYYY-MM-DD, unparseable => never active ----------------

    @Test fun `unparseable dates decode to null and the season is never active`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"startDate":"09-01-2026","endDate":"nope"""")
        assertNull(s.startDay)
        assertNull(s.endDay)
        assertFalse(s.isActive("20260901"))
    }

    @Test fun `strict YYYY-MM-DD dates decode and the activation window is inclusive`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"startDate":"2026-09-01","endDate":"2026-09-30"""")
        assertTrue(s.isActive("20260901"))
        assertTrue(s.isActive("20260930"))
        assertFalse(s.isActive("20260831"))
        assertFalse(s.isActive("20261001"))
    }

    // -- priority: absent => 0 -------------------------------------------------------------

    @Test fun `priority absent defaults to zero`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool]""")
        assertEquals(0, s.priority)
    }

    // -- icon: absent or blank => the sparkle fallback --------------------------------------

    @Test fun `icon absent falls back to the sparkle glyph`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool]""")
        assertEquals("✨", s.icon)
    }

    @Test fun `blank icon falls back to the sparkle glyph`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"icon":"   """")
        assertEquals("✨", s.icon)
    }

    @Test fun `a real icon is preserved`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"icon":"🎃"""")
        assertEquals("🎃", s.icon)
    }

    // -- accentColor: not 6-digit hex (with or without #) => null; 8-digit unsupported ------

    @Test fun `accent color with hash is normalized`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"accentColor":"#ff6b1a"""")
        assertEquals("#FF6B1A", s.accentColor)
    }

    @Test fun `accent color without hash is accepted`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"accentColor":"FF6B1A"""")
        assertEquals("#FF6B1A", s.accentColor)
    }

    @Test fun `eight digit accent color is not supported`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"accentColor":"#FF6B1AFF"""")
        assertNull(s.accentColor)
    }

    @Test fun `malformed accent color falls back to null`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"accentColor":"not-a-color"""")
        assertNull(s.accentColor)
    }

    @Test fun `absent accent color decodes to null`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool]""")
        assertNull(s.accentColor)
    }

    // -- artwork URLs: absolute https with a non-empty host; else null ---------------------

    @Test fun `https artwork url with a host is accepted`() {
        val s = season(
            """"enabled":true,"levelCount":1,"emojiPool":[$pool],"backgroundImageURL":"https://example.com/a.png","backgroundImageURLDark":"https://example.com/b.png","cardImageURL":"https://example.com/c.png"""",
        )
        assertEquals("https://example.com/a.png", s.backgroundImageURL)
        assertEquals("https://example.com/b.png", s.backgroundImageURLDark)
        assertEquals("https://example.com/c.png", s.cardImageURL)
    }

    @Test fun `cleartext http artwork url falls back to null`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"backgroundImageURL":"http://example.com/a.png"""")
        assertNull(s.backgroundImageURL)
    }

    @Test fun `blank artwork url falls back to null`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"cardImageURL":"   """")
        assertNull(s.cardImageURL)
    }

    @Test fun `hostless artwork url falls back to null`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"cardImageURL":"https:///a.png"""")
        assertNull(s.cardImageURL)
    }

    @Test fun `absent artwork urls decode to null`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool]""")
        assertNull(s.backgroundImageURL)
        assertNull(s.backgroundImageURLDark)
        assertNull(s.cardImageURL)
    }

    // -- strings: absent => {}; a missing subtitle => "" ------------------------------------

    @Test fun `strings absent decodes to empty maps`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool]""")
        assertTrue(s.title.isEmpty())
        assertTrue(s.subtitle.isEmpty())
    }

    @Test fun `a locale missing its subtitle reads back as empty string`() {
        val s = season(""""enabled":true,"levelCount":1,"emojiPool":[$pool],"strings":{"en":{"title":"Spooky"}}""")
        assertEquals("Spooky", s.title["en"])
        assertEquals("", s.subtitle["en"])
    }

    // -- one malformed season must never cost the player a second, valid one ---------------

    @Test fun `a malformed sibling never costs a valid season`() {
        val seasons = SeasonDecoder.decode(
            """{"bad":{"enabled":true,"levelCount":1,"emojiPool":["only","two"]},"good":{"enabled":true,"levelCount":1,"emojiPool":[$pool]}}""",
        )
        assertEquals(listOf("good"), seasons.map { it.id })
    }
}
