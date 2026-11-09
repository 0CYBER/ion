package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class AlertHapticsTest {
    @Test fun everyKindHasAValidWaveform() {
        AlertKind.entries.forEach { kind ->
            val p = AlertHaptics.pattern(kind)
            assertTrue("$kind: needs at least one pulse", p.size >= 2)
            assertEquals("$kind: starts with a delay slot", 0L, p[0])
            assertTrue("$kind: no negative timing", p.all { it >= 0 })
            assertTrue("$kind: stays under two seconds", p.sum() < 2_000)
        }
    }

    @Test fun dangerFeelsDifferentFromInformation() {
        val pulses = { k: AlertKind -> AlertHaptics.pattern(k).size / 2 }
        assertEquals(3, pulses(AlertKind.BATTERY_CRITICAL))
        assertEquals(2, pulses(AlertKind.SPEED_HIGH))
        assertEquals(1, pulses(AlertKind.LINK_RESTORED))
        assertTrue(AlertHaptics.pattern(AlertKind.LINK_LOST)[1] > AlertHaptics.pattern(AlertKind.LINK_RESTORED)[1])
    }

    @Test fun strongestPicksTheMostUrgentRhythm() {
        assertNull(AlertHaptics.strongest(emptyList()))
        val fired = listOf(Alert(AlertKind.SPEED_HIGH, 50), Alert(AlertKind.TEMPERATURE_HIGH, 80), Alert(AlertKind.LINK_RESTORED))
        assertEquals(AlertKind.TEMPERATURE_HIGH, AlertHaptics.strongest(fired)?.kind)
        assertEquals(AlertKind.SPEED_HIGH, AlertHaptics.strongest(listOf(Alert(AlertKind.LINK_RESTORED), Alert(AlertKind.SPEED_HIGH)))?.kind)
    }

    @Test fun vibrationIsOptIn() = assertFalse(AlertConfig().vibrate)

    @Test fun urgentAlertsSpeakFirstAndOnlyOneInterrupts() {
        val order = AlertHaptics.voiceOrder(listOf(Alert(AlertKind.LINK_RESTORED), Alert(AlertKind.SPEED_HIGH, 50), Alert(AlertKind.BATTERY_CRITICAL, 9)))
        assertEquals(listOf(AlertKind.SPEED_HIGH, AlertKind.BATTERY_CRITICAL, AlertKind.LINK_RESTORED), order.map { it.first.kind })
        assertEquals(listOf(true, false, false), order.map { it.second })
        assertEquals(listOf(false), AlertHaptics.voiceOrder(listOf(Alert(AlertKind.BATTERY_LOW, 20))).map { it.second })
        assertTrue(AlertHaptics.voiceOrder(emptyList()).isEmpty())
    }
}
