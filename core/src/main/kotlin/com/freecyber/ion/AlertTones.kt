package com.freecyber.ion

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Beep alerts: the same rhythm as [AlertHaptics] (three long = danger, two short = slow down, one long = plan
 * ahead, one short = good news), so the sound and the vibration of an alert match. Pitch adds the severity:
 * high for danger, mid for warnings, a low soft tone for good news.
 */
object AlertTones {
    const val SAMPLE_RATE = 22_050
    private const val FADE_MS = 6

    fun pitchHz(kind: AlertKind): Double = when (kind) {
        AlertKind.BATTERY_CRITICAL, AlertKind.TEMPERATURE_HIGH, AlertKind.WHEEL_ERROR -> 1760.0
        AlertKind.SPEED_HIGH -> 1480.0
        AlertKind.BATTERY_LOW, AlertKind.LINK_LOST -> 1175.0
        AlertKind.LINK_RESTORED, AlertKind.CHARGE_TARGET -> 880.0
    }

    /** 16-bit mono PCM for one alert: beeps on the "on" segments of the haptic pattern, silence in between. */
    fun pcm(kind: AlertKind, sampleRate: Int = SAMPLE_RATE, volume: Double = 0.8): ShortArray {
        val pattern = AlertHaptics.pattern(kind)
        val total = pattern.sumOf { it }.toInt() * sampleRate / 1000
        val out = ShortArray(total)
        val hz = pitchHz(kind)
        val fade = FADE_MS * sampleRate / 1000
        var at = 0
        pattern.forEachIndexed { i, ms ->
            val n = (ms * sampleRate / 1000).toInt()
            if (i % 2 == 1) for (k in 0 until n) {
                // Short linear fades at both ends: without them each beep starts and stops with a click.
                val envelope = min(1.0, min(k, n - 1 - k).toDouble() / fade)
                val sample = sin(2 * PI * hz * k / sampleRate) * envelope * volume
                if (at + k < total) out[at + k] = (sample * Short.MAX_VALUE).roundToInt().toShort()
            }
            at += n
        }
        return out
    }
}
