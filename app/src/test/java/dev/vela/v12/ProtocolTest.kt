package dev.vela.v12

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ProtocolTest {
    private fun String.hex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun fixtures(): List<ByteArray> = javaClass.getResourceAsStream("/v12-fixtures.txt")!!.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.hex() }.toList()
    }
    @Test fun publishedFixturesAndEverySplitBoundary() {
        val fixtures = fixtures(); assertEquals(25, fixtures.size)
        fixtures.forEach { bytes ->
            for (cut in 0..bytes.size) {
                val d = V12Protocol.Decoder()
                val frames = d.feed(bytes.copyOfRange(0, cut)) + d.feed(bytes.copyOfRange(cut, bytes.size))
                assertEquals("split $cut/${bytes.size}", 1, frames.size)
                assertEquals(0, d.rejected)
            }
        }
    }
    @Test fun bytewiseConcatenatedFrames() {
        val d = V12Protocol.Decoder()
        val frames = fixtures().flatMap { bytes -> bytes.flatMap { d.feed(byteArrayOf(it)) } }
        assertEquals(25, frames.size)
    }
    @Test fun telemetryMatchesPublishedHsFixtureNotUserWheel() {
        val bytes = "aaaa144384cd26090000000e00040000000000000000000000eafb000062009d2450463b1b581b000000000000cdce00ced1d0b03d2828000000004900000000000000000000008c".hex()
        val t = V12Protocol.telemetry(V12Protocol.Decoder().feed(bytes).single())!!
        assertEquals(99.33, t.voltage, 0.0001); assertEquals(0.09, t.current, 0.0001)
        assertEquals(0.0, t.speed, 0.0001); assertEquals(-10.46, t.roll, 0.0001)
        assertEquals(29, t.mosTemp); assertEquals(30, t.motorTemp)
    }
    @Test fun htIdentityIsSyntheticNotRealCapture() {
        val bytes = V12Protocol.frame(0x11, 0x82, byteArrayOf(1, 2, 7, 2, 1, 3, 0))
        val m = V12Protocol.model(V12Protocol.Decoder().feed(bytes).single())!!
        assertTrue(m.isHt)
        assertFalse(V12Protocol.model(V12Protocol.Decoder().feed("aaaa110882010207010103009c".hex()).single())!!.isHt)
    }
    @Test fun readOnlyRequestsMatchReferenceAndExposeNoControlEnum() {
        assertArrayEquals("aaaa1102020110".hex(), V12Protocol.request(V12Protocol.Read.MODEL))
        assertArrayEquals("aaaa14010411".hex(), V12Protocol.request(V12Protocol.Read.TELEMETRY))
        assertArrayEquals("aaaa1402202016".hex(), V12Protocol.request(V12Protocol.Read.SETTINGS))
        assertTrue(V12Protocol.Read.entries.none { it.command == 0x60 })
    }
    @Test fun corruptChecksumIsRejectedAndNextFrameRecovers() {
        val corrupted = V12Protocol.request(V12Protocol.Read.MODEL).clone()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        val d = V12Protocol.Decoder()
        assertEquals(1, d.feed(corrupted + V12Protocol.request(V12Protocol.Read.TELEMETRY)).size)
        assertTrue(d.rejected > 0)
    }
    @Test fun escapingAndSpecialChecksumSyntheticCoverage() {
        for (target in listOf(0xa5, 0xaa)) {
            val bytes = V12Protocol.frame(0x14, 0x84, byteArrayOf((0x14 xor 2 xor 0x84 xor target).toByte()))
            assertEquals(target, bytes.last().toInt() and 255)
            assertEquals(2, V12Protocol.Decoder().feed(bytes + V12Protocol.request(V12Protocol.Read.MODEL)).size)
        }
        val random = Random(7)
        repeat(100) {
            val payload = random.nextBytes(random.nextInt(0, 255))
            val d = V12Protocol.Decoder()
            val frames = V12Protocol.frame(0x14, 0x84, payload).flatMap { d.feed(byteArrayOf(it)) }
            assertArrayEquals(payload, frames.single().payload)
        }
    }
    @Test fun shortWrongAndRequestLayoutsDoNotBecomeTelemetry() {
        val request = V12Protocol.Decoder().feed(V12Protocol.request(V12Protocol.Read.TELEMETRY)).single()
        assertNull(V12Protocol.telemetry(request))
        assertNull(V12Protocol.telemetry(V12Protocol.Frame(0x14, 0x84, byteArrayOf(1, 2))))
        assertNull(V12Protocol.settings(request)); assertNull(V12Protocol.model(request))
    }
    @Test fun parserMemoryIsBounded() {
        val d = V12Protocol.Decoder(); assertTrue(d.feed(ByteArray(5000) { 0xaa.toByte() }).isEmpty())
        assertTrue(d.rejected > 0)
        assertEquals(1, d.feed(V12Protocol.request(V12Protocol.Read.MODEL)).size)
    }
}
