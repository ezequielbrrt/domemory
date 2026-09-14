package com.ezequielbrrt.domemory.services.multiplayer

import kotlin.random.Random

object RoomCode {
    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun normalize(raw: String): String? = raw.trim().uppercase().filter(Char::isLetterOrDigit)
        .takeIf { it.length == LENGTH && it.all { it in ALPHABET } }

    fun generate(random: Random = Random.Default): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
