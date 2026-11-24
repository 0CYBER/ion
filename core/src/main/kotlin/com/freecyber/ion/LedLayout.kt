package com.freecyber.ion

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where the 70 side LEDs sit, drawn as the vendor editor draws the V12: a bar of [BAR] LEDs on each side and a ring
 * of 2 × [HALF_RING] open at the top and bottom (measured off the INMOTION 8.1.34 editor).
 *
 * Coordinates are in units of the picture's height: x in 0..[WIDTH], y in 0..1.
 *
 * [ORDER]: left bar, left half of the ring, right half, right bar, each from the top down; the owner confirmed it
 * on the wheel with an effect painted in Ion.
 */
object LedLayout {
    enum class Segment { LEFT_BAR, LEFT_RING, RIGHT_RING, RIGHT_BAR }
    data class Spot(val x: Float, val y: Float, val segment: Segment)

    const val BAR = 12
    const val HALF_RING = 23
    const val WIDTH = 0.918f
    /** LED radius in the vendor picture. */
    const val DOT = 0.019f
    const val CX = 0.459f
    const val CY = 0.695f
    const val R = 0.278f
    /** The ring's top gap ends 10.7° from the top, its bottom gap 17° from the bottom (per side). */
    const val TOP_GAP = 10.7
    const val BOTTOM_GAP = 17.0

    /** Segments in LED-number order, each listed from the top down. */
    val ORDER = listOf(Segment.LEFT_BAR, Segment.LEFT_RING, Segment.RIGHT_RING, Segment.RIGHT_BAR)

    private fun segment(s: Segment): List<Spot> = when (s) {
        Segment.LEFT_BAR, Segment.RIGHT_BAR -> List(BAR) { i ->
            Spot(if (s == Segment.LEFT_BAR) 0.076f else 0.841f, 0.027f + i * 0.038f, s)
        }
        Segment.LEFT_RING, Segment.RIGHT_RING -> List(HALF_RING) { i ->
            // Degrees from the top, down the side.
            val a = (TOP_GAP + i * (180 - TOP_GAP - BOTTOM_GAP) / (HALF_RING - 1)) * PI / 180
            val dx = (R * sin(a)).toFloat()
            Spot(if (s == Segment.LEFT_RING) CX - dx else CX + dx, (CY - R * cos(a)).toFloat(), s)
        }
    }

    /** Spot of each LED, by LED number. */
    val spots: List<Spot> = ORDER.flatMap(::segment).also { check(it.size == LightEffects.LEDS) }

    /** 0..1 clockwise from the top around the ring's centre; templates run along this. */
    fun phase(i: Int): Double {
        val s = spots[i]
        val a = atan2((s.x - CX).toDouble(), (CY - s.y).toDouble())
        return ((a / (2 * PI)) + 1) % 1
    }

    /** 0 at the topmost LED, 1 at the lowest. */
    fun height(i: Int): Double {
        val ys = spots.map { it.y }
        return ((spots[i].y - ys.min()) / (ys.max() - ys.min())).toDouble()
    }

    fun isLeft(i: Int) = spots[i].x < CX

    /** The LED under a point of the picture, if one is within [reach] (in picture heights). */
    fun at(x: Float, y: Float, reach: Float = DOT * 1.8f): Int? =
        spots.indices.minByOrNull { hypot(spots[it].x - x, spots[it].y - y) }?.takeIf { hypot(spots[it].x - x, spots[it].y - y) <= reach }
}
