package com.freecyber.ion

/** One recorded telemetry sample of a finished ride; [t] is ms since the session start, the same clock as [TrackPoint.t]. */
data class Moment(
    val t: Long,
    val speed: Double,
    val voltage: Double?,
    val current: Double?,
    val power: Double?,
    val pwm: Double?,
    val battery: Double?,
    val mosTemp: Int?,
    val motorTemp: Int?,
    val distanceKm: Double?,
)

/**
 * The ride explorer's data: the telemetry CSV ([TripCsv]) read back into [Moment]s, thinned for drawing, and
 * time lookups that tie the chart cursor, the map marker and the "at this moment" figures together.
 */
object TripTimeline {
    const val MAX_POINTS = 1200

    /** Reads a [TripCsv] file's lines (header first). Columns are found by name, so older files with fewer columns work. */
    fun parse(lines: Sequence<String>, maxPoints: Int = MAX_POINTS): List<Moment> {
        val it = lines.iterator()
        if (!it.hasNext()) return emptyList()
        val header = it.next().split(',').withIndex().associate { (i, name) -> name.trim() to i }
        val t = header["elapsed_ms"] ?: return emptyList()
        val speed = header["speed_kmh"] ?: return emptyList()
        fun col(name: String) = header[name]
        val v = col("voltage_v"); val a = col("current_a"); val w = col("power_w"); val pwm = col("pwm_pct")
        val bat = col("battery_pct"); val mos = col("mos_temp_c"); val motor = col("motor_temp_c")
        val dist = col("session_distance_km")
        val out = ArrayList<Moment>()
        while (it.hasNext()) {
            val f = it.next().split(',')
            fun d(i: Int?) = i?.let(f::getOrNull)?.toDoubleOrNull()?.takeIf(Double::isFinite)
            fun n(i: Int?) = i?.let(f::getOrNull)?.toIntOrNull()
            val at = f.getOrNull(t)?.toLongOrNull() ?: continue
            out += Moment(at, d(speed) ?: continue, d(v), d(a), d(w), d(pwm), d(bat), n(mos), n(motor), d(dist))
        }
        return thin(out, maxPoints)
    }

    /** Keeps at most [maxPoints]: per time bucket the fastest sample, so speed peaks survive the thinning. */
    fun thin(moments: List<Moment>, maxPoints: Int = MAX_POINTS): List<Moment> {
        if (moments.size <= maxPoints || maxPoints < 2) return moments
        val first = moments.first().t; val span = (moments.last().t - first).coerceAtLeast(1)
        val buckets = LinkedHashMap<Int, Moment>()
        moments.forEach { m ->
            val b = ((m.t - first) * (maxPoints - 1) / span).toInt()
            val kept = buckets[b]
            if (kept == null || kotlin.math.abs(m.speed) > kotlin.math.abs(kept.speed)) buckets[b] = m
        }
        return buckets.values.toList()
    }

    /** Index of the element whose time is closest to [t]; -1 for an empty list. */
    fun <T> nearest(items: List<T>, t: Long, time: (T) -> Long): Int {
        if (items.isEmpty()) return -1
        var lo = 0; var hi = items.lastIndex
        while (lo < hi) { val mid = (lo + hi) / 2; if (time(items[mid]) < t) lo = mid + 1 else hi = mid }
        return if (lo > 0 && t - time(items[lo - 1]) <= time(items[lo]) - t) lo - 1 else lo
    }

    /** Length of the ride on the shared clock: the later of the last sample and the last fix. */
    fun duration(moments: List<Moment>, track: List<TrackPoint>): Long =
        maxOf(moments.lastOrNull()?.t ?: 0L, track.lastOrNull()?.t ?: 0L)
}
