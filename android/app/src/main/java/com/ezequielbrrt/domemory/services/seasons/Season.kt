package com.ezequielbrrt.domemory.services.seasons

import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.services.levels.LevelCurve
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * One seasonal level progression decoded from a child of `/seasons` (spec 9.2).
 *
 * Validation fails **closed** on structure (a season missing [enabled], a
 * `levelCount` under 1, or an emoji pool with fewer than [LevelCurve.maxPairs]
 * distinct entries is rejected outright — see [SeasonDecoder.decode]) and falls
 * **back** on decoration: [accentColor] and the artwork URLs are validated at
 * decode time and simply resolve to `null` when malformed, so a typo in the
 * console degrades the surface to a flat colour instead of costing the player
 * the whole season (spec 9.3).
 */
data class Season(
    val id: String,
    val enabled: Boolean,
    val startDay: String?,
    val endDay: String?,
    val priority: Int,
    val levelCount: Int,
    val icon: String,
    /** Validated `#RRGGBB` (8-digit forms are not supported), or null to fall back to brand primary. */
    val accentColor: String?,
    /** Validated absolute `https` URL, or null to fall back to a flat colour. */
    val backgroundImageURL: String?,
    /** Dark-appearance replacement for [backgroundImageURL]; null means one image serves both. */
    val backgroundImageURLDark: String?,
    /** Artwork for the menu card; null falls back to [accentColor]. */
    val cardImageURL: String?,
    val emojiPool: List<String>,
    /** Locale key (normalized `_`→`-`, lowercased) to title. */
    val title: Map<String, String>,
    /** Locale key to subtitle; a locale present in [title] but missing a subtitle reads as `""`. */
    val subtitle: Map<String, String>,
) {
    fun isActive(day: String) = enabled && startDay != null && endDay != null && day in startDay..endDay

    /** All non-null artwork URLs, for prefetching (spec 9.7): card, then background light, then dark. */
    fun artworkUrls(): List<String> = listOfNotNull(cardImageURL, backgroundImageURL, backgroundImageURLDark)

    /**
     * Whole days from [today] (a `YYYYMMDD` day key) to the season's last day, inclusive —
     * 0 means the season ends today. Null when [endDay] failed to parse.
     */
    fun daysRemaining(today: String): Int? {
        val end = endDay ?: return null
        return runCatching {
            val endDate = LocalDate.parse(end, DAY_KEY_FORMAT)
            val todayDate = LocalDate.parse(today, DAY_KEY_FORMAT)
            ChronoUnit.DAYS.between(todayDate, endDate).toInt().coerceAtLeast(0)
        }.getOrNull()
    }

    private companion object {
        val DAY_KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

object SeasonDecoder {
    fun decode(raw: String): List<Season> = runCatching {
        val root = JSONObject(raw)
        root.keys().asSequence().mapNotNull { id -> root.optJSONObject(id)?.let { decodeOne(id, it) } }.toList()
    }.getOrDefault(emptyList())

    /** Highest `priority` among active seasons; ties break on the smallest id (spec 9.4). */
    fun active(seasons: List<Season>, day: String): Season? = seasons.filter { it.isActive(day) }
        .sortedWith(compareByDescending<Season> { it.priority }.thenBy { it.id })
        .firstOrNull()

    private fun decodeOne(id: String, json: JSONObject): Season? {
        val count = json.optInt("levelCount", 0); if (count < 1) return null
        // Order-preserving dedup: the seeded shuffle depends on pool order (root CLAUDE.md).
        val pool = json.optJSONArray("emojiPool").strings().map(String::trim).filter(String::isNotEmpty).distinct()
        if (pool.size < LevelCurve.maxPairs) return null
        val start = day(json.optString("startDate")); val end = day(json.optString("endDate"))
        val strings = json.optJSONObject("strings")
        val titles = mutableMapOf<String, String>(); val subtitles = mutableMapOf<String, String>()
        strings?.keys()?.forEach { key ->
            strings.optJSONObject(key)?.let { value ->
                titles[key.normalize()] = value.optString("title")
                subtitles[key.normalize()] = value.optString("subtitle")
            }
        }
        return Season(
            id = id,
            enabled = json.optBoolean("enabled", false),
            startDay = start,
            endDay = end,
            priority = json.optInt("priority", 0),
            levelCount = count,
            icon = json.optString("icon").ifBlank { "✨" },
            // optString("...") reads back "" when the key is absent, and every parser below
            // treats a blank string as "not provided" — so absent and blank are, correctly,
            // indistinguishable here.
            accentColor = parseAccentColor(json.optString("accentColor")),
            backgroundImageURL = parseArtworkUrl(json.optString("backgroundImageURL")),
            backgroundImageURLDark = parseArtworkUrl(json.optString("backgroundImageURLDark")),
            cardImageURL = parseArtworkUrl(json.optString("cardImageURL")),
            emojiPool = pool,
            title = titles,
            subtitle = subtitles,
        )
    }

    private fun day(value: String): String? = DayKey.parse(value)

    private fun String.normalize() = replace('_', '-').lowercase()

    private fun JSONArray?.strings(): List<String> =
        this?.let { (0 until it.length()).mapNotNull { i -> it.optString(i).takeIf(String::isNotBlank) } }.orEmpty()

    /**
     * `#RRGGBB` (with or without the leading `#`) to a normalized `#RRGGBB` string.
     * Null for anything else, including the 8-digit form, which this payload does not define.
     */
    fun parseAccentColor(raw: String?): String? {
        var value = raw?.trim() ?: return null
        if (value.isEmpty()) return null
        if (value.startsWith("#")) value = value.substring(1)
        if (value.length != 6 || value.any { !it.isHexDigit() }) return null
        return "#" + value.uppercase()
    }

    /**
     * `https` URL with a non-empty host, or null for anything else (blank, malformed, or
     * cleartext `http` — ATS/cleartext-blocking parity, spec 9.3).
     */
    fun parseArtworkUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.host.isNullOrEmpty()) return null
        return trimmed
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

/**
 * Resolves a season's `strings` map for a device locale (spec 9.5).
 *
 * The lookup is built from progressively **shorter** prefixes of the locale identifier,
 * always ending in `en` — `es-419` → `["es-419", "es", "en"]`, `zh-Hans-CN` →
 * `["zh-hans-cn", "zh-hans", "zh", "en"]`. First hit wins; no hit returns null so the
 * caller can apply its own app-side fallback (spec: "app-side fallback title").
 *
 * This was a real shipped iOS bug: the lookup only ever *shortened* the identifier, so a
 * key was only reachable from the exact identifier that produced it — `es-419` was
 * unreachable from `es_MX`, and `zh-Hans` was unreachable from any mainland device
 * because iOS canonicalizes `zh_Hans_CN` to `zh_CN`, dropping the script. The fix lived in
 * the *catalog* (publish both `zh-Hans` and `zh-CN`, both `es-419` and `es`) rather than in
 * the algorithm, which this port keeps as-is.
 *
 * **This does not translate directly to Android.** `Locale.toLanguageTag()` reports
 * different identifiers than iOS's `Locale.identifier` — notably `zh-Hans-CN` (Android
 * keeps the script subtag; iOS drops it), `es-419` and `pt-BR`. See
 * `SeasonLocaleResolutionTest` for the pinned, real `Locale.toLanguageTag()` outputs this
 * assumes.
 */
object SeasonLocaleResolver {
    /** Author-written keys are matched case-insensitively, `_` and `-` both accepted. */
    fun normalizeKey(key: String): String = key.replace('_', '-').lowercase()

    /** The ordered lookup candidates for a locale identifier, always ending in `en`. */
    fun candidates(languageTag: String): List<String> {
        val components = normalizeKey(languageTag).split('-').filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        var index = components.size
        while (index > 0) {
            result += components.subList(0, index).joinToString("-")
            index--
        }
        if ("en" !in result) result += "en"
        return result
    }

    /**
     * First candidate present in [values], else null. [values]' own keys are normalized
     * defensively — `Season`'s decoder already normalizes `strings` keys at decode time, but
     * this stays tolerant of a caller (or test) passing author-written keys as-is, matching
     * "author-written keys are matched case-insensitively" above.
     */
    fun resolve(values: Map<String, String>, languageTag: String): String? {
        val normalized = values.mapKeys { (key, _) -> normalizeKey(key) }
        for (candidate in candidates(languageTag)) {
            normalized[candidate]?.let { return it }
        }
        return null
    }

    /** Title/subtitle pair for [languageTag], or null when no candidate (including `en`) hits. */
    fun resolveText(season: Season, languageTag: String): Pair<String, String>? {
        val title = resolve(season.title, languageTag) ?: return null
        val subtitle = resolve(season.subtitle, languageTag) ?: ""
        return title to subtitle
    }
}
