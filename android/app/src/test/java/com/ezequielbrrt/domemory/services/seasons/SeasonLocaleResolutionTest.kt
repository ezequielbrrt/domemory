package com.ezequielbrrt.domemory.services.seasons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Pins [SeasonLocaleResolver] against real `java.util.Locale.toLanguageTag()` output
 * rather than assumed identifiers (spec 9.5's closing note).
 *
 * The lookup algorithm itself (progressively shorter prefixes, always ending in `en`) is
 * unchanged from iOS. What differs is the identifier the platform actually reports: iOS
 * canonicalizes away script subtags and does not report UN M.49 numeric regions
 * consistently, which is *why* `es-419` and `zh-Hans` needed a shipped-bug workaround
 * there (catalog authors had to publish extra fallback keys). Android's
 * `Locale.toLanguageTag()` reports `es-419`, `pt-BR` and, notably, `zh-Hans-CN` (keeping
 * the script) — so the exact same catalog resolves correctly on Android for cases that
 * needed the iOS workaround, without this port needing to reproduce that workaround.
 *
 * The catalog fixture below matches `firebase/scripts/seasons.json`: it publishes `en`,
 * `es`, `pt-BR`, `zh-Hans` and `zh-CN`, but deliberately *not* a bare `es-419` key, to prove
 * the shortening chain — not an exact-identifier match — is what makes resolution work.
 */
class SeasonLocaleResolutionTest {
    private val strings = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "pt-br" to "Portuguese (Brazil)",
        "zh-hans" to "Chinese (Simplified)",
        "zh-cn" to "Chinese (Mainland, no script)",
    )

    @Test fun `candidates shorten progressively and always end in en`() {
        assertEquals(listOf("es-419", "es", "en"), SeasonLocaleResolver.candidates("es-419"))
        assertEquals(listOf("zh-hans-cn", "zh-hans", "zh", "en"), SeasonLocaleResolver.candidates("zh-Hans-CN"))
        assertEquals(listOf("en"), SeasonLocaleResolver.candidates("en"))
    }

    @Test fun `es-419 built via Locale-Builder reports es-419 and resolves through the shortened es key`() {
        val tag = Locale.Builder().setLanguage("es").setRegion("419").build().toLanguageTag()
        assertEquals("es-419", tag)
        assertEquals("Spanish", SeasonLocaleResolver.resolve(strings, tag))
    }

    @Test fun `a Mexican Spanish device also resolves through the shortened es key`() {
        val tag = Locale.Builder().setLanguage("es").setRegion("MX").build().toLanguageTag()
        assertEquals("es-MX", tag)
        assertEquals("Spanish", SeasonLocaleResolver.resolve(strings, tag))
    }

    @Test fun `Brazilian Portuguese reports pt-BR directly`() {
        val tag = Locale.Builder().setLanguage("pt").setRegion("BR").build().toLanguageTag()
        assertEquals("pt-BR", tag)
        assertEquals("Portuguese (Brazil)", SeasonLocaleResolver.resolve(strings, tag))
    }

    @Test fun `Simplified Chinese for Mainland China reports the script subtag and resolves without needing a zh-CN fallback`() {
        // Android's device-locale APIs (Locale.forLanguageTag / LocaleList) report a script
        // subtag to disambiguate Simplified/Traditional Chinese, unlike iOS's
        // Locale.identifier for the same device setting, which canonicalizes it away.
        val tag = Locale.Builder().setLanguage("zh").setScript("Hans").setRegion("CN").build().toLanguageTag()
        assertEquals("zh-Hans-CN", tag)
        assertEquals("Chinese (Simplified)", SeasonLocaleResolver.resolve(strings, tag))
    }

    @Test fun `Traditional Chinese for Taiwan has no zh-Hant key and falls through to the catalog's own en entry`() {
        val tag = Locale.Builder().setLanguage("zh").setScript("Hant").setRegion("TW").build().toLanguageTag()
        assertEquals("zh-Hant-TW", tag)
        assertEquals("English", SeasonLocaleResolver.resolve(strings, tag))
    }

    @Test fun `a locale with no matching key at all falls through to en`() {
        val tag = Locale.Builder().setLanguage("de").setRegion("DE").build().toLanguageTag()
        assertEquals("English", SeasonLocaleResolver.resolve(strings, tag))
    }

    @Test fun `no hit including en returns null so the caller applies its own fallback`() {
        assertNull(SeasonLocaleResolver.resolve(emptyMap(), "fr-FR"))
    }

    @Test fun `author keys with underscores normalize the same as hyphenated Firebase keys`() {
        val underscored = mapOf("pt_BR" to "Portuguese (Brazil)")
        val tag = Locale.Builder().setLanguage("pt").setRegion("BR").build().toLanguageTag()
        assertEquals("Portuguese (Brazil)", SeasonLocaleResolver.resolve(underscored, tag))
    }

    @Test fun `resolveText pairs title and subtitle and defaults a missing subtitle to empty`() {
        val season = SeasonDecoder.decode(
            """{"s":{"enabled":true,"levelCount":1,"emojiPool":["1","2","3","4","5","6","7","8","9","10","11","12"],"strings":{"en":{"title":"Season"}}}}""",
        ).single()
        assertEquals("Season" to "", SeasonLocaleResolver.resolveText(season, "en-US"))
    }

    @Test fun `resolveText returns null when no candidate hits at all`() {
        val season = SeasonDecoder.decode(
            """{"s":{"enabled":true,"levelCount":1,"emojiPool":["1","2","3","4","5","6","7","8","9","10","11","12"]}}""",
        ).single()
        assertNull(SeasonLocaleResolver.resolveText(season, "fr-FR"))
    }
}
