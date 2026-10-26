package com.ezequielbrrt.domemory.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** The hosted policy only serves bare language codes; regional paths 404. */
class PrivacyPolicyUrlTest {
    private fun url(lang: String) = "https://zoratek.dev/domemory/$lang/privacy-policy/"

    @Test
    fun everyAppLanguageMapsToItsOwnPage() {
        listOf("en", "es", "pt", "fr", "de", "it", "ja", "ko", "hi", "zh").forEach {
            assertEquals(url(it), PrivacyPolicyUrl.forLanguage(it))
        }
    }

    @Test
    fun regionalTagsCollapseToTheBareLanguage() {
        assertEquals(url("es"), PrivacyPolicyUrl.forLanguage("es-419"))
        assertEquals(url("es"), PrivacyPolicyUrl.forLanguage("es-MX"))
        assertEquals(url("pt"), PrivacyPolicyUrl.forLanguage("pt-BR"))
        assertEquals(url("zh"), PrivacyPolicyUrl.forLanguage("zh-Hans"))
        assertEquals(url("zh"), PrivacyPolicyUrl.forLanguage("zh_CN"))
        assertEquals(url("fr"), PrivacyPolicyUrl.forLanguage("FR"))
    }

    @Test
    fun otherLanguagesFallBackToEnglish() {
        assertEquals(url("en"), PrivacyPolicyUrl.forLanguage("ru"))
        assertEquals(url("en"), PrivacyPolicyUrl.forLanguage(""))
        assertEquals(url("en"), PrivacyPolicyUrl.forLanguage(null))
    }
}
