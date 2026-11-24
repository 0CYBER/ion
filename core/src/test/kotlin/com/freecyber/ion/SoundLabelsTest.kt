package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class SoundLabelsTest {
    @Test fun namesAndHiddenSlotsRoundTrip() {
        val labels = SoundLabels().rename(3, "  Приветствие ").rename(7, "a;b").hide(9, true).hide(3, true)
        assertEquals("Приветствие", labels.name(3))
        assertEquals(labels, SoundLabels.decode(labels.encode()))
        assertEquals(setOf(3, 9), SoundLabels.decode(labels.encode()).hidden)
    }

    @Test fun blankNameClearsItAndUnknownSlotsAreDropped() {
        assertNull(SoundLabels().rename(3, "x").rename(3, " ").name(3))
        assertEquals(SoundLabels(), SoundLabels.decode("99;1;Ghost\ngarbage"))
        assertTrue(SoundLabels.HORN in V12Protocol.soundIds)
    }
}
