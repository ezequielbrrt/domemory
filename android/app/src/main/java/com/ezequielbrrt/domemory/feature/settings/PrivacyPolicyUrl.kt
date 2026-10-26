package com.ezequielbrrt.domemory.feature.settings

/**
 * The hosted privacy policy, one page per app language. Paths take the bare language code:
 * regional variants (`/es-mx/`, `/pt-br/`, `/zh-hans/`) return 404, so `es-419`, `pt-BR` and
 * `zh-Hans` all collapse to their language. Anything outside the ten app languages gets English.
 */
object PrivacyPolicyUrl {
    val LANGUAGES = setOf("en", "es", "pt", "fr", "de", "it", "ja", "ko", "hi", "zh")

    /** [language] is an ISO 639 code or a full BCP 47 tag; only its language part is read. */
    fun forLanguage(language: String?): String {
        val code = language.orEmpty().substringBefore('-').substringBefore('_').lowercase()
        val path = if (code in LANGUAGES) code else "en"
        return "https://zoratek.dev/domemory/$path/privacy-policy/"
    }
}
