package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class LightDesignTest {
    @Test fun everyTemplateBuildsAValidFile() {
        LightDesign.Template.entries.forEach { t ->
            val d = LightDesign(1, "x", t, 0x00FF00, 0x0000FF, 300)
            val frames = d.frames()
            assertTrue(t.name, frames.size in 1..LightEffects.MAX_FRAMES && frames.all { it.size == LightEffects.LEDS })
            assertEquals(t.name, 20 + frames.size * LightEffects.LEDS * 3, d.file(900_001).size)
        }
    }

    @Test fun blinkIsTheOwnersTestEffect() {
        val d = LightDesign(1, "testclaude", LightDesign.Template.BLINK, 0xFF0000, 0x008000, 100)
        val vendor = LightEffects.file(30321, listOf(IntArray(70) { 0xFF0000 }, IntArray(70) { 0x008000 }), 100)
        // Same frames and interval; only the id field (bytes 4..7) differs.
        assertArrayEquals(vendor.copyOfRange(8, vendor.size), d.file(900_001).copyOfRange(8, vendor.size))
    }

    @Test fun chaseMovesAndGradientEndsAtBothColours() {
        val chase = LightDesign(2, "c", LightDesign.Template.CHASE, 0xFFFFFF, 0).frames()
        assertEquals(LightDesign.CHASE_LENGTH, chase[0].count { it == 0xFFFFFF })
        assertNotEquals(chase[0].indexOfFirst { it == 0xFFFFFF }, chase[1].indexOfFirst { it == 0xFFFFFF })
        val g = LightDesign(2, "g", LightDesign.Template.GRADIENT, 0xFF0000, 0x0000FF).frames().single()
        assertEquals(0xFF0000, g.first()); assertEquals(0x0000FF, g.last())
    }

    @Test fun designsRoundTripAndSlotsMapToIds() {
        val list = listOf(LightDesign(3, "Ночь; синий", LightDesign.Template.BREATHE, 0x0070FF, 0, 500, 900_007), LightDesign(1, "a"))
        val back = LightDesign.decodeAll(LightDesign.encodeAll(list))
        assertEquals(listOf(1, 3), back.map { it.slot })
        assertEquals("Ночь, синий", back[1].name)
        assertEquals(900_007L, back[1].wheelId)
        assertNull(back[0].wheelId)
        assertEquals(2, LightDesign.freeSlot(back))
        // The owner's first build stored designs without a wheel id.
        assertEquals(LightDesign(1, "x", LightDesign.Template.BLINK, 0xFF0000, 0x0000FF, 500), LightDesign.decode("1;BLINK;FF0000;0000FF;500;x"))
        assertTrue(LightDesign.decodeAll("0;SOLID;FF0000;000000;100;bad\n1;NOPE;FF0000;000000;100;bad").isEmpty())
    }

    @Test fun everyUploadGetsAnIdNoFileOnTheWheelHas() {
        // The owner's wheel: vendor effects, Ion's first upload and the half-written copy of the reused id.
        val stored = listOf(LightEffects.Stored(25032, 230), LightEffects.Stored(900_001, 440))
        assertEquals(900_002L, LightDesign.nextId(stored, emptyList()))
        assertEquals(900_006L, LightDesign.nextId(stored, listOf(LightDesign(1, "a", wheelId = 900_005))))
        assertEquals(900_001L, LightDesign.nextId(listOf(LightEffects.Stored(30321, 440)), emptyList()))
        assertFalse(LightDesign.isIonId(30321))
    }
}
