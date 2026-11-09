package com.freecyber.ion

import java.util.Locale

/** A finished ride, frozen from [TripStats]. [startedAt] is wall-clock time so it survives reboots. */
data class TripSummary(
    val startedAt: Long,
    val durationMs: Long,
    val movingMs: Long,
    val distanceKm: Double,
    val maxSpeed: Double,
    val avgSpeed: Double,
    val energyWh: Double,
    val regenWh: Double,
    val batteryUsed: Double?,
    val maxTemp: Int?,
    val maxPower: Double? = null,
    val avgPower: Double? = null,
    val maxPwm: Double? = null,
    val maxCurrent: Double? = null,
    val minVoltage: Double? = null,
) {
    val consumption: Double? get() = if (distanceKm < 0.2) null else (energyWh - regenWh) / distanceKm

    fun encode(): String = listOf(startedAt, durationMs, movingMs, distanceKm, maxSpeed, avgSpeed, energyWh, regenWh,
        batteryUsed ?: "", maxTemp ?: "", maxPower ?: "", avgPower ?: "", maxPwm ?: "", maxCurrent ?: "", minVoltage ?: "").joinToString(";")

    companion object {
        /** Rides shorter than this are connection blips, not trips worth keeping. */
        const val MIN_DISTANCE_KM = 0.05

        fun decode(line: String): TripSummary? {
            val p = line.split(';')
            if (p.size < 10) return null
            return TripSummary(
                p[0].toLongOrNull() ?: return null, p[1].toLongOrNull() ?: return null, p[2].toLongOrNull() ?: return null,
                p[3].toDoubleOrNull() ?: return null, p[4].toDoubleOrNull() ?: return null, p[5].toDoubleOrNull() ?: return null,
                p[6].toDoubleOrNull() ?: return null, p[7].toDoubleOrNull() ?: return null, p[8].toDoubleOrNull(), p[9].toIntOrNull(),
                p.getOrNull(10)?.toDoubleOrNull(), p.getOrNull(11)?.toDoubleOrNull(), p.getOrNull(12)?.toDoubleOrNull(),
                p.getOrNull(13)?.toDoubleOrNull(), p.getOrNull(14)?.toDoubleOrNull())
        }

        /** Converts a live session; [wallNow]/[elapsedNow] translate the monotonic start into wall time. */
        fun from(s: TripStats, wallNow: Long, elapsedNow: Long): TripSummary? {
            val start = s.startedAt ?: return null
            if (s.distanceKm < MIN_DISTANCE_KM) return null
            val end = s.lastAt ?: elapsedNow
            return TripSummary(wallNow - (elapsedNow - start), (end - start).coerceAtLeast(0), s.movingMs, s.distanceKm,
                s.maxSpeed, s.avgMovingSpeed, s.energyWh, s.regenWh, s.batteryUsed, s.maxTemp,
                s.maxPower, s.avgPower, s.maxPwm, s.maxCurrent, s.minVoltage)
        }

        const val CSV_HEADER = "started_at_ms,duration_s,moving_s,distance_km,max_kmh,avg_moving_kmh,energy_wh,regen_wh,battery_used_pct,max_temp_c,max_power_w,avg_power_w,max_pwm_pct,max_current_a,min_voltage_v,started_at_utc"

        fun csv(trips: List<TripSummary>): String = buildString {
            appendLine(CSV_HEADER)
            trips.forEach { t ->
                appendLine(listOf(t.startedAt.toString(), (t.durationMs / 1000).toString(), (t.movingMs / 1000).toString(),
                    "%.3f".format(Locale.US, t.distanceKm), "%.1f".format(Locale.US, t.maxSpeed), "%.1f".format(Locale.US, t.avgSpeed),
                    "%.1f".format(Locale.US, t.energyWh), "%.1f".format(Locale.US, t.regenWh),
                    t.batteryUsed?.let { "%.1f".format(Locale.US, it) }.orEmpty(), t.maxTemp?.toString().orEmpty(),
                    t.maxPower.dec(0), t.avgPower.dec(0), t.maxPwm.dec(1), t.maxCurrent.dec(1), t.minVoltage.dec(2),
                    java.time.Instant.ofEpochMilli(t.startedAt).toString()).joinToString(","))
            }
        }
    }
}

private fun Double?.dec(places: Int): String =
    this?.takeIf { it.isFinite() }?.let { "%.${places}f".format(Locale.US, it) }.orEmpty()

/** Aggregate over all stored rides, shown above the list. */
data class TripTotals(val count: Int, val distanceKm: Double, val movingMs: Long, val maxSpeed: Double, val energyWh: Double) {
    companion object {
        fun of(trips: List<TripSummary>) = TripTotals(trips.size, trips.sumOf { it.distanceKm }, trips.sumOf { it.movingMs },
            trips.maxOfOrNull { it.maxSpeed } ?: 0.0, trips.sumOf { it.energyWh - it.regenWh })
    }
}
