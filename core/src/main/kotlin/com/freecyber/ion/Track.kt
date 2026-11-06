package com.freecyber.ion

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS fix of a ride track. [t] is the trip-relative elapsed time in ms; speed is m/s. */
data class TrackPoint(val t: Long, val lat: Double, val lon: Double, val alt: Double? = null, val speed: Double? = null)

/** Pure track geometry shared by the recorder, the detail screen and tests. */
object Track {
    /** Storage cap per ride: ~40 minutes at 1 Hz; longer rides are decimated, not truncated. */
    const val MAX_POINTS = 2400
    private const val M_PER_DEG_LAT = 111_320.0

    /** Great-circle distance in metres between two fixes. */
    fun metres(a: TrackPoint, b: TrackPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * 6_371_000 * sqrt(h)
    }

    /** Total path length in km. */
    fun distanceKm(points: List<TrackPoint>): Double {
        var sum = 0.0
        for (i in 1 until points.size) sum += metres(points[i - 1], points[i])
        return sum / 1000.0
    }

    /** Cumulative distance in km at every point (same size as [points], first is 0). */
    fun cumulativeKm(points: List<TrackPoint>): List<Double> {
        val out = ArrayList<Double>(points.size)
        var sum = 0.0
        points.forEachIndexed { i, p ->
            if (i > 0) sum += metres(points[i - 1], p)
            out += sum / 1000.0
        }
        return out
    }

    /** Decimates to at most [max] points by keeping every n-th fix plus both endpoints. */
    fun thin(points: List<TrackPoint>, max: Int = MAX_POINTS): List<TrackPoint> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return List(max) { i -> points[(i * step).roundToInt()] }
    }
}

/** Compact delta codec for storing a track in one preference string. The first point is absolute;
 * the rest are deltas (lat/lon quantised to 1e-5 deg ≈ 1.1 m, time to ms, altitude to m, speed to 0.1 m/s). */
object TrackCodec {
    fun encode(points: List<TrackPoint>): String = buildString {
        var pl = 0; var po = 0; var pt = 0L
        points.forEachIndexed { i, p ->
            if (i > 0) append(';')
            val lat = (p.lat * 1e5).roundToInt(); val lon = (p.lon * 1e5).roundToInt()
            append(p.t - pt).append(',').append(lat - pl).append(',').append(lon - po)
            append(',').append(p.alt?.roundToInt()?.toString() ?: "").append(',').append(p.speed?.let { (it * 10).roundToInt() }?.toString() ?: "")
            pl = lat; po = lon; pt = p.t
        }
    }

    fun decode(text: String): List<TrackPoint> {
        val out = ArrayList<TrackPoint>()
        var lat = 0; var lon = 0; var t = 0L
        text.split(';').forEach { raw ->
            val f = raw.split(',')
            val dt = f.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val dLat = f.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val dLon = f.getOrNull(2)?.toIntOrNull() ?: return@forEach
            t += dt; lat += dLat; lon += dLon
            val alt = f.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val speed = f.getOrNull(4)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            out += TrackPoint(t, lat / 1e5, lon / 1e5, alt?.toDouble(), speed?.let { it / 10.0 })
        }
        return out
    }
}
