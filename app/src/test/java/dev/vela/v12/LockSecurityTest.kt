package dev.vela.v12

import org.junit.Assert.*
import org.junit.Test

class LockSecurityTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    @Test fun requestMatchesObservedFormat() {
        assertArrayEquals(hex("AA AA 13 04 02 00 00 02 17"), LockSecurity.keyRequest())
    }
    @Test fun tokenComesOnlyFromExactResponseAndRoundTripsEscapes() {
        val token = ByteArray(16) { it.toByte() }.apply { this[3]=0xaa.toByte(); this[7]=0xa5.toByte() }
        val response = V12Protocol.frame(0x12, 0x02, byteArrayOf(0x80.toByte(), 2) + token)
        val decoder = V12Protocol.Decoder()
        assertTrue(decoder.feed(response.copyOfRange(0, 7)).isEmpty())
        val parsed = decoder.feed(response.copyOfRange(7,response.size)).single()
        val extracted = LockSecurity.keyFrom(parsed)!!
        assertArrayEquals(token, extracted)
        val outgoing = V12Protocol.Decoder().feed(LockSecurity.verifyRequest(extracted)).single()
        assertEquals(0x13, outgoing.flags); assertEquals(2, outgoing.commandByte)
        assertArrayEquals(byteArrayOf(0,0,0x82.toByte())+token, outgoing.payload)
        assertNull(LockSecurity.keyFrom(V12Protocol.Frame(0x12, 2, byteArrayOf(0x80.toByte(),2))))
        assertNull(LockSecurity.keyFrom(V12Protocol.Frame(0x13, 2, parsed.payload)))
        assertThrows(IllegalArgumentException::class.java) { LockSecurity.verifyRequest(ByteArray(15)) }
    }
    @Test fun verifyRequiresExactObservedResponseNotMerelyAck() {
        assertTrue(LockSecurity.isVerifyReply(V12Protocol.Frame(0x12,2,hex("80 82 01"))))
        assertFalse(LockSecurity.isVerifyReply(V12Protocol.Frame(0x12,2,hex("80 82 00"))))
        assertFalse(LockSecurity.isVerifyReply(V12Protocol.Frame(0x12,2,hex("80 82 01 00"))))
        assertFalse(LockSecurity.isVerifyReply(V12Protocol.Frame(0x16,0x21,hex("02 E0 31 00"))))
    }
}
