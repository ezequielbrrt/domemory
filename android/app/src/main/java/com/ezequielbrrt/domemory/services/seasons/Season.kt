package com.ezequielbrrt.domemory.services.seasons

import com.ezequielbrrt.domemory.core.time.DayKey
import com.ezequielbrrt.domemory.services.levels.LevelCurve
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class Season(val id: String, val enabled: Boolean, val startDay: String?, val endDay: String?, val priority: Int, val levelCount: Int, val icon: String, val emojiPool: List<String>, val title: Map<String, String>, val subtitle: Map<String, String>) {
    fun isActive(day: String) = enabled && startDay != null && endDay != null && day in startDay..endDay
}

object SeasonDecoder {
    fun decode(raw: String): List<Season> = runCatching {
        val root = JSONObject(raw)
        root.keys().asSequence().mapNotNull { id -> root.optJSONObject(id)?.let { decodeOne(id, it) } }.toList()
    }.getOrDefault(emptyList())

    fun active(seasons: List<Season>, day: String): Season? = seasons.filter { it.isActive(day) }.sortedWith(compareByDescending<Season> { it.priority }.thenBy { it.id }).firstOrNull()

    private fun decodeOne(id: String, json: JSONObject): Season? {
        val count = json.optInt("levelCount", 0); if (count < 1) return null
        val pool = json.optJSONArray("emojiPool").strings().map(String::trim).filter(String::isNotEmpty).distinct(); if (pool.size < LevelCurve.maxPairs) return null
        val start = day(json.optString("startDate")); val end = day(json.optString("endDate"))
        val strings = json.optJSONObject("strings")
        val titles = mutableMapOf<String, String>(); val subtitles = mutableMapOf<String, String>()
        strings?.keys()?.forEach { key -> strings.optJSONObject(key)?.let { value -> titles[key.normalize()] = value.optString("title"); subtitles[key.normalize()] = value.optString("subtitle") } }
        return Season(id, json.optBoolean("enabled", false), start, end, json.optInt("priority", 0), count, json.optString("icon").ifBlank { "✨" }, pool, titles, subtitles)
    }
    private fun day(value: String): String? = DayKey.parse(value)
    private fun String.normalize() = replace('_', '-').lowercase()
    private fun JSONArray?.strings(): List<String> = this?.let { (0 until it.length()).mapNotNull { i -> it.optString(i).takeIf(String::isNotBlank) } }.orEmpty()
}
