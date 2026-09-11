package com.ezequielbrrt.domemory.core.rng

import kotlin.random.Random

/**
 * SplitMix64 seeded by an FNV-1a 64-bit hash of a seed string (spec 6.1). Any device
 * produces the same sequence for the same seed, which is what makes the Daily
 * Challenge, endless Levels and Season levels generate their boards locally instead of
 * downloading them.
 *
 * Parity note (decision D1 in ANDROID_PLAN.md): the RNG matches iOS, but the *shuffle*
 * does not attempt to reproduce Swift's `shuffle(using:)` bit-for-bit, so an Android
 * daily board is deterministic and identical across Android devices without being
 * guaranteed equal to the iOS board for the same day. If that decision is reversed,
 * this class is the only thing that has to change.
 */
class SeededGenerator(seed: String) : Random() {

    private var state: ULong = fnv1a64(seed)

    fun nextULong(): ULong {
        state += 0x9E3779B97F4A7C15UL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9UL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBUL
        return z xor (z shr 31)
    }

    override fun nextBits(bitCount: Int): Int =
        if (bitCount == 0) 0 else (nextULong() shr (64 - bitCount)).toInt()

    override fun nextInt(): Int = nextULong().toLong().toInt()

    /** Fisher-Yates from the end, matching the shape of Swift's shuffle. */
    fun <T> shuffled(source: List<T>): List<T> {
        val working = source.toMutableList()
        for (i in working.lastIndex downTo 1) {
            val j = nextIntBelow(i + 1)
            val tmp = working[i]
            working[i] = working[j]
            working[j] = tmp
        }
        return working
    }

    private fun nextIntBelow(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return (nextULong() % bound.toULong()).toInt()
    }

    companion object {
        private const val FNV_OFFSET_BASIS = 14695981039346656037UL
        private const val FNV_PRIME = 1099511628211UL

        fun fnv1a64(value: String): ULong {
            var hash = FNV_OFFSET_BASIS
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                hash = hash xor byte.toUByte().toULong()
                hash *= FNV_PRIME
            }
            return hash
        }
    }
}
