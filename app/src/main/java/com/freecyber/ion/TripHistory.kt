package com.freecyber.ion

import androidx.core.content.edit
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val consumption: Double? get() = if (distanceKm < 0.2) null else (energyWh - regenWh) / distanceKm

    fun encode(): String = listOf(startedAt, durationMs, movingMs, distanceKm, maxSpeed, avgSpeed, energyWh, regenWh,
        batteryUsed ?: "", maxTemp ?: "").joinToString(";")

    companion object {
        /** Rides shorter than this are connection blips, not trips worth keeping. */
        const val MIN_DISTANCE_KM = 0.05

        fun decode(line: String): TripSummary? {
            val p = line.split(';')
            if (p.size < 10) return null
            return TripSummary(
                p[0].toLongOrNull() ?: return null, p[1].toLongOrNull() ?: return null, p[2].toLongOrNull() ?: return null,
                p[3].toDoubleOrNull() ?: return null, p[4].toDoubleOrNull() ?: return null, p[5].toDoubleOrNull() ?: return null,
                p[6].toDoubleOrNull() ?: return null, p[7].toDoubleOrNull() ?: return null, p[8].toDoubleOrNull(), p[9].toIntOrNull())
        }

        /** Converts a live session; [wallNow]/[elapsedNow] translate the monotonic start into wall time. */
        fun from(s: TripStats, wallNow: Long, elapsedNow: Long): TripSummary? {
            val start = s.startedAt ?: return null
            if (s.distanceKm < MIN_DISTANCE_KM) return null
            val end = s.lastAt ?: elapsedNow
            return TripSummary(wallNow - (elapsedNow - start), (end - start).coerceAtLeast(0), s.movingMs, s.distanceKm,
                s.maxSpeed, s.avgMovingSpeed, s.energyWh, s.regenWh, s.batteryUsed, s.maxTemp)
        }

        const val CSV_HEADER = "started_at_ms,duration_s,moving_s,distance_km,max_kmh,avg_moving_kmh,energy_wh,regen_wh,battery_used_pct,max_temp_c"

        fun csv(trips: List<TripSummary>): String = buildString {
            appendLine(CSV_HEADER)
            trips.forEach { t ->
                appendLine(listOf(t.startedAt.toString(), (t.durationMs / 1000).toString(), (t.movingMs / 1000).toString(),
                    "%.3f".format(Locale.US, t.distanceKm), "%.1f".format(Locale.US, t.maxSpeed), "%.1f".format(Locale.US, t.avgSpeed),
                    "%.1f".format(Locale.US, t.energyWh), "%.1f".format(Locale.US, t.regenWh),
                    t.batteryUsed?.let { "%.1f".format(Locale.US, it) }.orEmpty(), t.maxTemp?.toString().orEmpty()).joinToString(","))
            }
        }
    }
}

/** Aggregate over all stored rides, shown above the list. */
data class TripTotals(val count: Int, val distanceKm: Double, val movingMs: Long, val maxSpeed: Double, val energyWh: Double) {
    companion object {
        fun of(trips: List<TripSummary>) = TripTotals(trips.size, trips.sumOf { it.distanceKm }, trips.sumOf { it.movingMs },
            trips.maxOfOrNull { it.maxSpeed } ?: 0.0, trips.sumOf { it.energyWh - it.regenWh })
    }
}

/** Newest-first ride log in its own preferences file; bounded so it never grows without limit. */
class TripHistory(context: Context) {
    companion object { const val LIMIT = 200 }
    private val prefs = context.getSharedPreferences("ion_trips", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val trips: StateFlow<List<TripSummary>> = state.asStateFlow()

    private fun load(): List<TripSummary> = prefs.getString("trips", null)?.lineSequence()
        ?.mapNotNull { if (it.isBlank()) null else TripSummary.decode(it) }?.toList() ?: emptyList()

    fun add(trip: TripSummary) = save((listOf(trip) + state.value).take(LIMIT))

    /** Checkpoint of the running ride, so a killed process or crash does not lose it. */
    fun checkpoint(trip: TripSummary?) = prefs.edit { if (trip == null) remove("pending") else putString("pending", trip.encode()) }

    /** A checkpoint found at process start belongs to a ride that never finished cleanly: archive it. */
    fun recoverPending() {
        val pending = prefs.getString("pending", null)?.let(TripSummary::decode)
        checkpoint(null)
        if (pending != null && state.value.none { it.startedAt == pending.startedAt }) add(pending)
    }
    fun delete(trip: TripSummary) = save(state.value - trip)
    fun clear() = save(emptyList())

    private fun save(list: List<TripSummary>) {
        state.value = list
        prefs.edit { putString("trips", list.joinToString("\n") { it.encode() }) }
    }
}
