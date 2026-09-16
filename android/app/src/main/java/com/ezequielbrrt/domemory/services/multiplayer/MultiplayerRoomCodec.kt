package com.ezequielbrrt.domemory.services.multiplayer

/** Firebase's untyped map boundary. Field names intentionally match iOS's Codable keys. */
object MultiplayerRoomCodec {
    fun encode(room: MultiplayerRoom): Map<String, Any?> = mapOf(
        "id" to room.id, "code" to room.code, "status" to room.status.wire(), "createdAt" to room.createdAt,
        "updatedAt" to room.updatedAt, "hostId" to room.hostId, "guestId" to room.guestId,
        "players" to room.players.mapValues { (_, p) -> mapOf("id" to p.id, "name" to p.name, "connected" to p.connected, "lastSeenAt" to p.lastSeenAt, "score" to p.score, "isReady" to p.isReady) },
        "gameSource" to room.gameSource.wire(), "gameId" to room.gameId, "gameName" to room.gameName,
        "difficulty" to room.difficulty, "customGamePayload" to room.customGamePayload?.let { mapOf("title" to it.title, "category" to it.category, "items" to it.items, "itemType" to it.itemType, "isDoubleItem" to it.isDoubleItem) },
        "currentPlayerId" to room.currentPlayerId,
        "cards" to room.cards.map { mapOf("id" to it.id, "itemId" to it.itemId, "content" to it.content, "isFaceUp" to it.isFaceUp, "isMatched" to it.isMatched) },
        "selectedCardIds" to room.selectedCardIds, "winnerId" to room.winnerId,
        "disconnectStartedAt" to room.disconnectStartedAt, "disconnectPlayerId" to room.disconnectPlayerId,
    ).filterValues { it != null }

    @Suppress("UNCHECKED_CAST") fun decode(value: Any?): MultiplayerRoom? = runCatching {
        val m = value as? Map<String, Any?> ?: return null
        fun s(key: String) = m[key] as? String ?: error(key)
        fun n(key: String) = (m[key] as? Number)?.toLong() ?: error(key)
        fun bool(map: Map<String, Any?>, key: String, fallback: Boolean = false) = map[key] as? Boolean ?: fallback
        val players = (m["players"] as? Map<String, Any?>).orEmpty().mapValues { (id, raw) ->
            val p = raw as Map<String, Any?>
            MultiplayerPlayer(id = p["id"] as? String ?: id, name = p["name"] as? String ?: "Player", connected = bool(p, "connected", true), lastSeenAt = (p["lastSeenAt"] as? Number)?.toLong() ?: 0, score = (p["score"] as? Number)?.toInt() ?: 0, isReady = bool(p, "isReady", false))
        }
        val cards = (m["cards"] as? List<Any?>).orEmpty().map { raw ->
            val c = raw as Map<String, Any?>
            MultiplayerCard((c["id"] as Number).toInt(), (c["itemId"] as Number).toInt(), c["content"] as String, bool(c, "isFaceUp"), bool(c, "isMatched"))
        }
        val payload = (m["customGamePayload"] as? Map<String, Any?>)?.let { p -> MultiplayerCustomGamePayload(p["title"] as String, p["category"] as? String ?: "", (p["items"] as? List<Any?>).orEmpty().filterIsInstance<String>(), p["itemType"] as? String ?: "String", bool(p, "isDoubleItem", true)) }
        MultiplayerRoom(s("id"), s("code"), MultiplayerRoomStatus.valueOf(s("status").uppercase()), n("createdAt"), n("updatedAt"), s("hostId"), m["guestId"] as? String, players, MultiplayerGameSource.valueOf(s("gameSource").uppercase()), s("gameId"), s("gameName"), m["difficulty"] as? String, payload, m["currentPlayerId"] as? String, cards, (m["selectedCardIds"] as? List<Any?>).orEmpty().mapNotNull { (it as? Number)?.toInt() }, m["winnerId"] as? String, (m["disconnectStartedAt"] as? Number)?.toLong(), m["disconnectPlayerId"] as? String)
    }.getOrNull()

    private fun MultiplayerRoomStatus.wire() = name.lowercase()
    private fun MultiplayerGameSource.wire() = name.lowercase()
}
