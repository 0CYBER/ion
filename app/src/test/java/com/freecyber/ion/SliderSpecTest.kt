package com.freecyber.ion

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderSpecTest {
    @Test fun outOfRangeReadingIsClampedBeforeApply() {
        assertEquals(1, SliderSpec("standby", 0, 1..30) { "$it" }.value)
        assertEquals(100, SliderSpec("tilt", 140, -100..100) { "$it" }.value)
        assertEquals(-100, SliderSpec("tilt", -101, -100..100) { "$it" }.value)
    }

    @Test fun inRangeReadingIsKept() {
        assertEquals(12, SliderSpec("standby", 12, 1..30) { "$it" }.value)
    }
}
