package com.ezequielbrrt.domemory.data.remote

import com.ezequielbrrt.domemory.core.model.Board

/**
 * Turns the raw `/data` value into boards (spec 13.1). Pure, so it can be tested
 * against the real payload without a device or a Firebase connection.
 *
 * Tolerant by design, because catalog failure has to be silent and non-fatal:
 *  - the node is an **array**, but Realtime Database hands back a `Map` keyed by index
 *    when the array is sparse, so both shapes are accepted;
 *  - `null` holes in the array are dropped;
 *  - a numeric `id` arriving as a `Long` is accepted;
 *  - an entry with no id, or with fewer than two items, is dropped rather than
 *    failing the whole catalog;
 *  - anything else at all returns an empty list.
 */
object BoardDecoder {

    fun decodeCatalog(raw: Any?): List<Board> {
        val entries: Collection<*> = when (raw) {
            is List<*> -> raw
            is Map<*, *> -> raw.values
            else -> return emptyList()
        }
        return entries.mapNotNull { decodeBoard(it) }
    }

    fun decodeBoard(raw: Any?): Board? {
        val map = raw as? Map<*, *> ?: return null
        val id = map.string("id") ?: return null
        val items = (map["items"] as? List<*>)?.mapNotNull { it.asString() }.orEmpty()
        // A board with fewer than two items cannot produce a pair.
        if (items.size < 2) return null

        return Board(
            id = id,
            name = map.string("name").orEmpty(),
            category = map.string("category").orEmpty(),
            description = map.string("description").orEmpty(),
            difficulty = map.string("difficulty"),
            publishedDate = map.string("publishedDate"),
            items = items,
            itemType = map.string("itemType") ?: "String",
            isDoubleItem = map["isDoubleItem"] as? Boolean ?: true,
        )
    }

    private fun Map<*, *>.string(key: String): String? = this[key].asString()

    private fun Any?.asString(): String? = when (this) {
        null -> null
        is String -> takeIf { it.isNotBlank() }
        is Boolean -> toString()
        else -> toString().takeIf { it.isNotBlank() }
    }
}
