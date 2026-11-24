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
    val maxPwm: Double = 0.0,
    val maxCurrent: Double = 0.0,
    val minVoltage: Double? = null,
    val measuredMs: Long = 0,
) {
    /** Mean pack draw over measured intervals, including stops; regeneration is reported separately. */
    val avgPower: Double? get() = if (measuredMs > 0) energyWh * 3_600_000 / measuredMs else null
    /** Average over time actually spent moving, so stops at traffic lights do not drag it down. */
    val avgMovingSpeed: Double get() = if (movingMs < 1000) 0.0 else distanceKm / (movingMs / 3_600_000.0)
    val batteryUsed: Double? get() = startBattery?.let { s -> lastBattery?.let { (s - it).coerceAtLeast(0.0) } }
    /** Wh per km; null until the distance is meaningful. */
    val consumption: Double? get() = if (distanceKm < 0.2) null else (energyWh - regenWh) / distanceKm
}

/**
 * Integrates telemetry into [TripStats]. Pure apart from the exposed StateFlow, so it can run in unit tests.
 *
 * Power-off tail: when the wheel is switched off it keeps talking for a second or two while its pack voltage
 * collapses, which used to put a bogus minimum voltage and a battery drop into the ride. A standing-still sample far
 * below the last accepted voltage is therefore held back ([Feed.Held]) instead of counted. The next normal sample
 * accepts the held ones (it was real) unless a link gap came first; a held tail that never gets a successor, because
 * the link dropped, simply never reaches the stats. A dip that lasts longer than [HOLD_MS] is accepted as real.
 */
class TripRecorder {
    companion object {
        const val MOVING_KMH = 1.0
        /** Gaps longer than this (lost link) are not integrated: no invented distance or energy. */
        const val MAX_GAP_MS = 5_000L
        /** A standing sample below this share of the last accepted voltage looks like the power-off collapse. */
        const val COLLAPSE_RATIO = 0.85
        const val HOLD_MS = 10_000L
    }

    /** What [feed] did with a sample, so the caller can hold or drop its own copy (the CSV row) the same way. */
    enum class Feed {
        /** Counted. Samples held before it were dropped (the wheel came back after a gap) or there were none. */
        COUNTED,
        /** Counted, after the samples held before it, which turned out to be real. */
        COUNTED_WITH_HELD,
        /** Looks like the power-off collapse: held back until the next sample decides. */
        HELD,
    }
    private val held = ArrayList<Pair<V12Protocol.Telemetry, Long>>()
    private var acceptedVoltage: Double? = null
    private val mutable = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = mutable.asStateFlow()
    private var lastAt: Long? = null
    private var lastTripKm: Double? = null
    /** Provisional speed distance since the last wheel-counter increment (the counter is quantised). */
    private var provisionalKm = 0.0

    fun reset() { mutable.value = TripStats(); lastAt = null; lastTripKm = null; provisionalKm = 0.0; held.clear(); acceptedVoltage = null }

    fun feed(t: V12Protocol.Telemetry, now: Long): Feed {
        val reference = acceptedVoltage
        val collapsing = reference != null && kotlin.math.abs(t.speed) < MOVING_KMH &&
            (!t.voltage.isFinite() || t.voltage < reference * COLLAPSE_RATIO)
        if (collapsing && (held.isEmpty() || now - held.first().second < HOLD_MS)) {
            held += t to now
            return Feed.HELD
        }
        val keepHeld = held.isNotEmpty() && (collapsing || now - held.last().second <= MAX_GAP_MS)
        if (keepHeld) held.forEach { (h, at) -> apply(h, at) }
        held.clear()
        apply(t, now)
        return if (keepHeld) Feed.COUNTED_WITH_HELD else Feed.COUNTED
    }

    private fun apply(t: V12Protocol.Telemetry, now: Long) {
        val s = mutable.value
        val dt = lastAt?.let { now - it }?.takeIf { it in 1..MAX_GAP_MS } ?: 0L
        // A frozen/quantised counter must not suppress speed distance. Reconcile its next increment
        // against provisional distance so we never add both the speed estimate and the counter delta.
        val counterDelta = lastTripKm?.let { t.tripKm - it }
        val speedDelta = kotlin.math.abs(t.speed) * dt / 3_600_000.0
        val distanceDelta = when {
            counterDelta != null && counterDelta > 0 && counterDelta < 0.5 ->
                // A counter resuming with a tiny increment after a long freeze is still stale;
                // it must not erase a kilometre already measured from speed. Allow two 10 m ticks
                // of quantisation error when reconciling an otherwise consistent counter.
                (if (provisionalKm <= counterDelta + 0.02) counterDelta - provisionalKm else speedDelta)
                    .also { provisionalKm = 0.0 }
            counterDelta == 0.0 -> speedDelta.also { provisionalKm += it }
            else -> speedDelta.also { provisionalKm = 0.0 }
        }
        val power = t.voltage * t.current
        val hours = dt / 3_600_000.0
        val battery = t.battery.takeIf { it.isFinite() && it in 0.0..100.0 }
        val temp = listOf(t.mosTemp, t.motorTemp).filter { it in -50..180 }.maxOrNull()
        mutable.value = s.copy(
            startedAt = s.startedAt ?: now,
            lastAt = now,
            samples = s.samples + 1,
            distanceKm = (s.distanceKm + distanceDelta).coerceAtLeast(0.0),
            maxSpeed = maxOf(s.maxSpeed, kotlin.math.abs(t.speed)),
            movingMs = s.movingMs + if (kotlin.math.abs(t.speed) >= MOVING_KMH) dt else 0L,
            energyWh = s.energyWh + if (power > 0) power * hours else 0.0,
            regenWh = s.regenWh + if (power < 0) -power * hours else 0.0,
            startBattery = s.startBattery ?: battery,
            lastBattery = battery ?: s.lastBattery,
            maxTemp = listOfNotNull(s.maxTemp, temp).maxOrNull(),
            maxPower = maxOf(s.maxPower, power),
            maxPwm = maxOf(s.maxPwm, kotlin.math.abs(t.pwm)),
            maxCurrent = maxOf(s.maxCurrent, t.current),
            minVoltage = listOfNotNull(s.minVoltage, t.voltage.takeIf { it.isFinite() && it > 0 }).minOrNull(),
            measuredMs = s.measuredMs + dt,
        )
        lastAt = now
        lastTripKm = t.tripKm
        if (t.voltage.isFinite() && t.voltage > 0) acceptedVoltage = t.voltage
    }
}
