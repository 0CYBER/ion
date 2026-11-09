package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class ReadingAgeTest {
    @Test fun missingOrNegativeAgeHasNoLabel() {
        assertNull(ReadingAge.of(null))
        assertNull(ReadingAge.of(-1))
    }

    @Test fun picksTheCoarsestNaturalUnit() {
        assertEquals(ReadingAge.Seconds(0), ReadingAge.of(999))
        assertEquals(ReadingAge.Seconds(59), ReadingAge.of(59_999))
        assertEquals(ReadingAge.Minutes(1), ReadingAge.of(60_000))
        assertEquals(ReadingAge.Minutes(59), ReadingAge.of(3_599_999))
        assertEquals(ReadingAge.Hours(1), ReadingAge.of(3_600_000))
        assertEquals(ReadingAge.Hours(26), ReadingAge.of(26 * 3_600_000L + 5))
    }
}
