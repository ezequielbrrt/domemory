package com.ezequielbrrt.domemory.services.levels

import kotlin.math.roundToInt

/**
 * The endless-Levels difficulty curve (spec 7.1). Table-driven with linear
 * interpolation between anchors; the last anchor's value is held forever after.
 *
 * Deliberately not a formula — the curve is sub-linear and this keeps it retunable.
 *
 * `maxFailures` tracks roughly `pairs + 2` on purpose: even perfect recall costs
 * N/2–N mismatches on an N-pair board, so a budget at or below the pair count would
 * make levels unwinnable.
 */
object LevelCurve {

    private data class Anchor(val level: Int, val value: Int)

    private val pairAnchors = listOf(
        Anchor(1, 3), Anchor(5, 4), Anchor(10, 6), Anchor(25, 9), Anchor(50, 12),
    )
    private val secondAnchors = listOf(
        Anchor(1, 90), Anchor(5, 85), Anchor(10, 75),
        Anchor(25, 60), Anchor(50, 45), Anchor(80, 35),
    )
    private val failureAnchors = listOf(
        Anchor(1, 4), Anchor(5, 6), Anchor(10, 8), Anchor(25, 11), Anchor(50, 14),
    )

    fun pairs(level: Int): Int = interpolate(level, pairAnchors)

    fun seconds(level: Int): Int = interpolate(level, secondAnchors)

    fun maxFailures(level: Int): Int = interpolate(level, failureAnchors)

    /**
     * The pair cap, held forever past the last anchor. This is also the floor on a
     * season's emoji pool (spec 9.3): a season with fewer distinct emoji than this
     * cannot fill its own level-25+ boards. Derive that constant from here so
     * retuning the curve moves the floor with it.
     */
    val maxPairs: Int get() = pairAnchors.last().value

    /** The pie indicator appears from this level on, in Levels and Seasons (spec 3.4). */
    const val PIE_FROM_LEVEL = 25

    fun showsPie(level: Int): Boolean = level >= PIE_FROM_LEVEL

    private fun interpolate(level: Int, anchors: List<Anchor>): Int {
        val l = maxOf(1, level)
        val first = anchors.first()
        val last = anchors.last()
        if (l <= first.level) return first.value
        if (l >= last.level) return last.value

        val hiIndex = anchors.indexOfFirst { l <= it.level }
        val hi = anchors[hiIndex]
        val lo = anchors[hiIndex - 1]
        val t = (l - lo.level).toDouble() / (hi.level - lo.level).toDouble()
        return (lo.value + t * (hi.value - lo.value)).roundToInt()
    }
}
