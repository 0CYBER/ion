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

    @Test fun vibrationIsOptIn() = assertFalse(AlertConfig().vibrate)
}
