package com.freecyber.ion

/** Vibration patterns for alerts, felt through a pocket while riding. Timings alternate off/on in milliseconds
 * (the Android waveform convention, starting with an initial delay). Severity is carried by rhythm, not by
 * strength: critical states are long and repeated, informational ones are short. */
object AlertHaptics {
    private const val SHORT = 120L
    private const val LONG = 450L
    private const val GAP = 140L

    fun pattern(kind: AlertKind): LongArray = when (kind) {
        // Danger to the ride: three long pulses.
        AlertKind.BATTERY_CRITICAL, AlertKind.TEMPERATURE_HIGH, AlertKind.WHEEL_ERROR ->
            longArrayOf(0, LONG, GAP, LONG, GAP, LONG)
        // Slow down: two quick taps.
        AlertKind.SPEED_HIGH -> longArrayOf(0, SHORT, GAP, SHORT)
        // Plan ahead: one long pulse.
        AlertKind.BATTERY_LOW, AlertKind.LINK_LOST -> longArrayOf(0, LONG)
        // Good news: one short tap.
        AlertKind.LINK_RESTORED, AlertKind.CHARGE_TARGET -> longArrayOf(0, SHORT)
    }
}
