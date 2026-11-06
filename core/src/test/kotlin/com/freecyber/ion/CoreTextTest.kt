package com.freecyber.ion

import java.util.Locale
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class CoreTextTest {
    private fun <T> inLocale(tag: String, block: () -> T): T {
        val saved = IonFormat.locale
        IonFormat.locale = Locale.forLanguageTag(tag)
        try { return block() } finally { IonFormat.locale = saved }
    }

    @Test fun errorNamesFollowLanguage() {
        assertEquals("Перегрев мотора", inLocale("ru") { WheelErrors.label("err_motorTempState") })
        assertEquals("Motor overheat", inLocale("en") { WheelErrors.label("err_motorTempState") })
        assertEquals("Low battery · level 2", inLocale("en") { WheelErrors.label("err_lowBatteryState-2") })
        assertEquals("Низкий заряд · уровень 2", inLocale("ru") { WheelErrors.label("err_lowBatteryState-2") })
        // Unknown identifiers are never translated into an invented meaning.
        assertEquals("newThing", inLocale("en") { WheelErrors.label("err_newThingState") })
    }

    @Test fun commandLabelsFollowLanguage() {
        val lights = V12Protocol.Write(V12Protocol.Control.LIGHTS, 1, 0)
        assertEquals("Lights: low on, high off", inLocale("en") { lights.label })
        assertEquals("Свет: ближний вкл, дальний выкл", inLocale("ru") { lights.label })
        assertEquals("Pedal tilt: 1.5°", inLocale("en") { V12Protocol.Write(V12Protocol.Control.PEDAL_TILT, 15).label })
        assertEquals("Наклон педалей: 1,5°", inLocale("ru") { V12Protocol.Write(V12Protocol.Control.PEDAL_TILT, 15).label })
        assertEquals("Telemetry", inLocale("en") { V12Protocol.Read.TELEMETRY.label })
    }

    @Test fun noCyrillicLeaksIntoEnglishLabels() {
        val cyrillic = Regex("[\u0400-\u04FF]")
        inLocale("en") {
            V12Protocol.Control.entries.forEach { c ->
                val label = V12Protocol.Write(c, 1, 1).label
                assertFalse(label, cyrillic.containsMatchIn(label))
            }
            V12Protocol.Read.entries.forEach { assertFalse(it.label, cyrillic.containsMatchIn(it.label)) }
        }
    }
}
