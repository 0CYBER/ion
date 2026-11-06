package com.freecyber.ion

/** What the alert engine sees each tick; built from WheelState so the engine itself stays pure. */
data class AlertInput(
    val connected: Boolean,
    val linkLost: Boolean,
    val battery: Double?,
    val charging: Boolean,
    val mosTemp: Int?,
    val motorTemp: Int?,
    val speed: Double?,
    val errors: Set<String> = emptySet(),
)

enum class AlertKind { BATTERY_LOW, BATTERY_CRITICAL, TEMPERATURE_HIGH, SPEED_HIGH, LINK_LOST, LINK_RESTORED, WHEEL_ERROR }

/** One fired alert. [value] carries the triggering figure (percent, °C, km/h); [detail] the error names. */
data class Alert(val kind: AlertKind, val value: Int = 0, val detail: List<String> = emptyList())

/** Edge-triggered alerts with hysteresis: each condition fires once when crossed and re-arms only after the value
 * clearly recovers, so a value hovering at the threshold never spams notifications. */
class AlertEngine {
    companion object {
        const val BATTERY_REARM = 5
        const val CRITICAL_BATTERY = 10
        const val TEMPERATURE_REARM = 5
        const val SPEED_REARM = 3
        /** Minimum gap between two speed alerts even if the rider oscillates around the threshold. */
        const val SPEED_COOLDOWN_MS = 20_000L
    }
    private var batteryArmed = true
    private var criticalArmed = true
    private var temperatureArmed = true
    private var speedArmed = true
    private var lastSpeedAlertAt = Long.MIN_VALUE / 2
    private var wasConnected = false
    private var lostNotified = false
    private var knownErrors = emptySet<String>()

    fun reset() {
        batteryArmed = true; criticalArmed = true; temperatureArmed = true; speedArmed = true
        lastSpeedAlertAt = Long.MIN_VALUE / 2; wasConnected = false; lostNotified = false; knownErrors = emptySet()
    }

    fun evaluate(input: AlertInput, config: AlertConfig, now: Long): List<Alert> {
        val out = mutableListOf<Alert>()
        if (config.disconnect) {
            if (wasConnected && !input.connected && input.linkLost && !lostNotified) { out += Alert(AlertKind.LINK_LOST); lostNotified = true }
            if (input.connected && lostNotified) { out += Alert(AlertKind.LINK_RESTORED); lostNotified = false }
        } else lostNotified = false
        if (input.connected) wasConnected = true else if (!input.linkLost) wasConnected = false
        if (!input.connected) return out

        val battery = input.battery?.takeIf { it.isFinite() && it in 0.0..100.0 }
        if (battery != null) {
            val limit = config.batteryLowPercent
            if (input.charging) { batteryArmed = true; criticalArmed = true }
            else {
                if (limit > CRITICAL_BATTERY) {
                    if (criticalArmed && battery <= CRITICAL_BATTERY) { out += Alert(AlertKind.BATTERY_CRITICAL, battery.toInt()); criticalArmed = false; batteryArmed = false }
                    else if (battery >= CRITICAL_BATTERY + BATTERY_REARM) criticalArmed = true
                }
                if (limit > 0) {
                    if (batteryArmed && battery <= limit) { out += Alert(AlertKind.BATTERY_LOW, battery.toInt()); batteryArmed = false }
                    else if (battery >= limit + BATTERY_REARM) batteryArmed = true
                }
            }
        }

        val temperature = listOfNotNull(input.mosTemp, input.motorTemp).filter { it in -50..180 }.maxOrNull()
        if (temperature != null && config.temperatureHighC > 0) {
            if (temperatureArmed && temperature >= config.temperatureHighC) { out += Alert(AlertKind.TEMPERATURE_HIGH, temperature); temperatureArmed = false }
            else if (temperature <= config.temperatureHighC - TEMPERATURE_REARM) temperatureArmed = true
        }

        val speed = input.speed?.takeIf { it.isFinite() }?.let { kotlin.math.abs(it) }
        if (speed != null && config.speedHighKmh > 0) {
            if (speedArmed && speed >= config.speedHighKmh && now - lastSpeedAlertAt >= SPEED_COOLDOWN_MS) {
                out += Alert(AlertKind.SPEED_HIGH, speed.toInt()); speedArmed = false; lastSpeedAlertAt = now
            } else if (speed <= config.speedHighKmh - SPEED_REARM) speedArmed = true
        }

        if (config.wheelErrors) {
            val fresh = input.errors - knownErrors
            if (fresh.isNotEmpty()) out += Alert(AlertKind.WHEEL_ERROR, fresh.size, fresh.sorted())
        }
        knownErrors = input.errors
        return out
    }
}
