package com.freecyber.ion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session statistics derived from telemetry samples; one session spans one connection (survives auto-reconnects). */
data class TripStats(
    val startedAt: Long? = null,
    /** Monotonic time of the newest sample; with [startedAt] gives the session duration. */
    val lastAt: Long? = null,
    val samples: Int = 0,
    val distanceKm: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val movingMs: Long = 0,
    val energyWh: Double = 0.0,
    val regenWh: Double = 0.0,
    val startBattery: Double? = null,
    val lastBattery: Double? = null,
    val maxTemp: Int? = null,
    val maxPower: Double = 0.0,
) {
    /** Average over time actually spent moving, so stops at traffic lights do not drag it down. */
    val avgMovingSpeed: Double get() = if (movingMs < 1000) 0.0 else distanceKm / (movingMs / 3_600_000.0)
    val batteryUsed: Double? get() = startBattery?.let { s -> lastBattery?.let { (s - it).coerceAtLeast(0.0) } }
    /** Wh per km; null until the distance is meaningful. */
    val consumption: Double? get() = if (distanceKm < 0.2) null else (energyWh - regenWh) / distanceKm
}

/** Integrates telemetry into [TripStats]. Pure apart from the exposed StateFlow, so it can run in unit tests. */
class TripRecorder {
    companion object {
        const val MOVING_KMH = 1.0
        /** Gaps longer than this (lost link) are not integrated: no invented distance or energy. */
        const val MAX_GAP_MS = 5_000L
    }
    private val mutable = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = mutable.asStateFlow()
    private var lastAt: Long? = null
    private var lastTripKm: Double? = null

    fun reset() { mutable.value = TripStats(); lastAt = null; lastTripKm = null }

    fun feed(t: V12Protocol.Telemetry, now: Long) {
        val s = mutable.value
        val dt = lastAt?.let { now - it }?.takeIf { it in 1..MAX_GAP_MS } ?: 0L
        // Prefer the wheel's own trip counter (exact); fall back to speed integration if it resets or jumps.
        val counterDelta = lastTripKm?.let { t.tripKm - it }
        val distanceDelta = when {
            counterDelta != null && counterDelta >= 0 && counterDelta < 0.5 -> counterDelta
            dt > 0 -> kotlin.math.abs(t.speed) * dt / 3_600_000.0
            else -> 0.0
        }
        val power = t.voltage * t.current
        val hours = dt / 3_600_000.0
        val battery = t.battery.takeIf { it.isFinite() && it in 0.0..100.0 }
        val temp = listOf(t.mosTemp, t.motorTemp).filter { it in -50..180 }.maxOrNull()
        mutable.value = s.copy(
            startedAt = s.startedAt ?: now,
            lastAt = now,
            samples = s.samples + 1,
            distanceKm = s.distanceKm + distanceDelta,
            maxSpeed = maxOf(s.maxSpeed, kotlin.math.abs(t.speed)),
            movingMs = s.movingMs + if (kotlin.math.abs(t.speed) >= MOVING_KMH) dt else 0L,
            energyWh = s.energyWh + if (power > 0) power * hours else 0.0,
            regenWh = s.regenWh + if (power < 0) -power * hours else 0.0,
            startBattery = s.startBattery ?: battery,
            lastBattery = battery ?: s.lastBattery,
            maxTemp = listOfNotNull(s.maxTemp, temp).maxOrNull(),
            maxPower = maxOf(s.maxPower, power),
        )
        lastAt = now
        lastTripKm = t.tripKm
    }
}
