package com.freecyber.ion

/** One point of the live chart. [at] is monotonic milliseconds. */
data class Sample(val at: Long, val speed: Float, val power: Float, val battery: Float)

/** Fixed-size ring of recent telemetry, thinned to at most one point per [stepMs], for the ride chart.
 * Pure and allocation-light: [snapshot] copies only when something changed. */
class SampleBuffer(private val capacity: Int = 600, private val stepMs: Long = 1_000) {
    private val ring = arrayOfNulls<Sample>(capacity)
    private var head = 0
    private var size = 0
    private var lastAt = Long.MIN_VALUE
    var version = 0; private set

    /** Adds a point unless one was taken less than [stepMs] ago; returns true when the buffer changed. */
    fun add(t: V12Protocol.Telemetry, now: Long): Boolean {
        if (lastAt != Long.MIN_VALUE && now - lastAt < stepMs) return false
        if (!t.speed.isFinite()) return false
        lastAt = now
        ring[(head + size) % capacity] = Sample(now, kotlin.math.abs(t.speed).toFloat(), t.power.toFloat(),
            t.battery.takeIf { it.isFinite() }?.toFloat() ?: Float.NaN)
        if (size < capacity) size++ else head = (head + 1) % capacity
        version++
        return true
    }

    fun clear() { head = 0; size = 0; lastAt = Long.MIN_VALUE; ring.fill(null); version++ }

    fun snapshot(): List<Sample> = List(size) { ring[(head + it) % capacity]!! }
}
