package com.ezequielbrrt.domemory.data.remote

import org.json.JSONObject

/**
 * Turns the raw Firebase Realtime Database `/seasons` value — a `Map<String, Any?>` with
 * nested `Map`/`List` children, exactly what `DataSnapshot.value` hands back — into the
 * raw JSON text [com.ezequielbrrt.domemory.services.seasons.SeasonDecoder.decode] expects.
 *
 * Pure and Firebase-free on purpose, mirroring [BoardDecoder]: the shape-conversion logic
 * that a malformed or absent `/seasons` node would break is testable with a plain `Map`
 * fixture, with no live database connection needed.
 */
object SeasonPayloadJson {
    fun encode(value: Any?): String? {
        val map = value as? Map<*, *> ?: return null
        // org.json's Map constructor recursively wraps nested Map/List children into
        // JSONObject/JSONArray, so one call round-trips the whole snapshot.
        return runCatching { JSONObject(map).toString() }.getOrNull()
    }
}
