package com.freecyber.ion

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun followsTheUiLocaleAndHidesNonFiniteValues() {
        val before = IonFormat.locale
        try {
            IonFormat.locale = Locale.forLanguageTag("ru")
            assertEquals("23,4", 23.44.fmt(1))
            IonFormat.locale = Locale.US
            assertEquals("23.4", 23.44.fmt(1))
            assertEquals("—", Double.NaN.fmt(1))
            assertEquals("—", Double.POSITIVE_INFINITY.fmt(2))
        } finally { IonFormat.locale = before }
    }
}

class DurationFormatTest {
    @Test fun formatsHoursMinutesSeconds() {
        org.junit.Assert.assertEquals("1:02:03", V12Protocol.formatDuration(3723))
        org.junit.Assert.assertEquals("0:00:00", V12Protocol.formatDuration(0))
    }
    @Test fun negativeSpanIsZero() {
        org.junit.Assert.assertEquals("0:00:00", V12Protocol.formatDuration(-65))
    }
}
