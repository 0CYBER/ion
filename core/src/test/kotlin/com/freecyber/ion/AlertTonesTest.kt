package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class AlertTonesTest {
    @Test fun beepsFollowTheVibrationRhythm() {
        AlertKind.entries.forEach { kind ->
            val pcm = AlertTones.pcm(kind)
            val ms = AlertHaptics.pattern(kind).sum()
            assertEquals(kind.name, (ms * AlertTones.SAMPLE_RATE / 1000).toInt(), pcm.size)
            // Count beeps as runs of sound separated by silence; equals the haptic pulses.
            var beeps = 0; var quiet = 0; var inBeep = false
            pcm.forEach { s ->
                if (kotlin.math.abs(s.toInt()) > 200) { if (!inBeep) beeps++; inBeep = true; quiet = 0 }
                else if (++quiet > 200) inBeep = false
            }
            assertEquals(kind.name, AlertHaptics.pattern(kind).size / 2, beeps)
        }
    }

    @Test fun dangerIsHigherThanGoodNewsAndBeepsStartSilent() {
        assertTrue(AlertTones.pitchHz(AlertKind.BATTERY_CRITICAL) > AlertTones.pitchHz(AlertKind.LINK_RESTORED))
        val pcm = AlertTones.pcm(AlertKind.SPEED_HIGH)
        assertEquals(0, pcm.first().toInt())
        assertTrue(pcm.maxOf { it.toInt() } < Short.MAX_VALUE)
    }
}
