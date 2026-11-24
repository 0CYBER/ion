package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class LightDesignTest {
    @Test fun everyTemplateBuildsAValidFile() {
        LightDesign.Template.entries.forEach { t ->
            val d = LightDesign(1, "x", t, 0x00FF00, 0x0000FF, 300)
            val frames = d.frames()
            assertTrue(t.name, frames.size in 1..LightEffects.MAX_FRAMES && frames.all { it.size == LightEffects.LEDS })
            assertEquals(t.name, 20 + frames.size * LightEffects.LEDS * 3, d.file().size)
        }
    }

    @Test fun blinkIsTheOwnersTestEffect() {
        val d = LightDesign(1, "testclaude", LightDesign.Template.BLINK, 0xFF0000, 0x008000, 100)
        val vendor = LightEffects.file(30321, listOf(IntArray(70) { 0xFF0000 }, IntArray(70) { 0x008000 }), 100)
        // Same frames and interval; only the id field (bytes 4..7) differs.
        assertArrayEquals(vendor.copyOfRange(8, vendor.size), d.file().copyOfRange(8, vendor.size))
        assertEquals(LightEffects.ION_ID_BASE + 1, d.id)
    }

    @Test fun chaseMovesAndGradientEndsAtBothColours() {
        val chase = LightDesign(2, "c", LightDesign.Template.CHASE, 0xFFFFFF, 0).frames()
        assertEquals(LightDesign.CHASE_LENGTH, chase[0].count { it == 0xFFFFFF })
        assertNotEquals(chase[0].indexOfFirst { it == 0xFFFFFF }, chase[1].indexOfFirst { it == 0xFFFFFF })
        val g = LightDesign(2, "g", LightDesign.Template.GRADIENT, 0xFF0000, 0x0000FF).frames().single()
        assertEquals(0xFF0000, g.first()); assertEquals(0x0000FF, g.last())
    }

    @Test fun designsRoundTripAndSlotsMapToIds() {
        val list = listOf(LightDesign(3, "Ночь; синий", LightDesign.Template.BREATHE, 0x0070FF, 0, 500), LightDesign(1, "a"))
        val back = LightDesign.decodeAll(LightDesign.encodeAll(list))
        assertEquals(listOf(1, 3), back.map { it.slot })
        assertEquals("Ночь, синий", back[1].name)
        assertEquals(2, LightDesign.freeSlot(back))
        assertEquals(3, LightDesign.slotOf(LightEffects.ION_ID_BASE + 3))
        assertNull(LightDesign.slotOf(30321))
        assertTrue(LightDesign.decodeAll("0;SOLID;FF0000;000000;100;bad\n1;NOPE;FF0000;000000;100;bad").isEmpty())
    }
}
