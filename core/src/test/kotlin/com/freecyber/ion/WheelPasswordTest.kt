package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

/** Frames copied from the owner's capture of the INMOTION app (docs/evidence/password-frames.txt). */
class WheelPasswordTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun parse(s: String) = V12Protocol.Decoder().feed(hex(s)).single()

    @Test fun requestsMatchTheCapture() {
        assertArrayEquals(hex("aaaa160302230b3f"), WheelPassword.readType())
        assertArrayEquals(hex("aaaa160402234b0179"), WheelPassword.setType(WheelPassword.PIN))
        assertArrayEquals(hex("aaaa160402234b0078"), WheelPassword.setType(WheelPassword.OFF))
        assertArrayEquals(hex("aaaa160902234a01043132333475"), WheelPassword.setPin("1234"))
        assertArrayEquals(hex("aaaa160b02234a010631333537393078"), WheelPassword.setPin("135790"))
    }

    @Test fun repliesFromTheCapture() {
        assertEquals(1, WheelPassword.reply(parse("aaaa160423028b01b9"))!!.type)
        assertEquals(0, WheelPassword.reply(parse("aaaa160423028b00b8"))!!.type)
        val type = WheelPassword.reply(parse("aaaa16042302cb00f8"))!!
        assertTrue(type.typeSet); assertNull(type.type); assertFalse(type.pinSet)
        assertTrue(WheelPassword.reply(parse("aaaa16042302ca00f9"))!!.pinSet)
        // Other units' replies are not the password's.
        assertNull(WheelPassword.reply(parse("aaaa16052102e03400e4")))
        assertNull(WheelPassword.reply(V12Protocol.Frame(0x16, 0x23, hex("028c41")))!!.type)
    }

    @Test fun onlyFourOrSixDigits() {
        assertTrue(WheelPassword.valid("0000")); assertTrue(WheelPassword.valid("123456"))
        listOf("", "123", "12345", "1234567", "12a4", "١٢٣٤").forEach { assertFalse(it, WheelPassword.valid(it)) }
        assertThrows(IllegalArgumentException::class.java) { WheelPassword.setPin("12345") }
        assertThrows(IllegalArgumentException::class.java) { WheelPassword.setType(2) }
    }
}
