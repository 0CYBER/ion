package com.freecyber.ion

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
    /** Owner's V12 HT, 2026-11-22: the vendor app's range estimate on, then off, Ion export after each switch. */
    @Test fun rangeEstimateBitAndRangeFieldMatchOwnerExports() {
        fun frame(command: Int, hex: String) = V12Protocol.Frame(0x14, command, hex.hex())
        val settingsOn = V12Protocol.settings(frame(0xA0, "20000000000000000070178A1381150000080710464605010F32646428AC0D2F2F800C0000000005700100"))!!
        val settingsOff = V12Protocol.settings(frame(0xA0, "20000000000000000070178A1381150000080710464605010F32646428AC0D2F2F800C0000000005300100"))!!
        assertTrue(settingsOn.rangeEstimate); assertFalse(settingsOff.rangeEstimate)
        assertEquals(settingsOn.copy(rangeEstimate = false), settingsOff)
        val on = V12Protocol.telemetry(frame(0x84, "D42601000000000000000000000000004EEE4EEE4200000010271C25C0127017800C000000000000CDC300D4D2D1B02B646400000000840000000000000000000000"))!!
        val off = V12Protocol.telemetry(frame(0x84, "D42602000000000000000000000000004EEE4EEE4100000010271C2550467017800C000000000000CDC300D4D2D1B021646400000000840000000000000000000000"))!!
        assertEquals(48.0, on.rangeKm, 0.0001); assertEquals(180.0, off.rangeKm, 0.0001)
        assertEquals(100.0, on.battery, 0.0001)
        val write = V12Protocol.Write(V12Protocol.Control.RANGE_ESTIMATE, 1)
        assertEquals(true, write.confirmed(frame(0xA0, "20000000000000000070178A1381150000080710464605010F32646428AC0D2F2F800C0000000005700100")))
        assertEquals(false, write.confirmed(frame(0xA0, "20000000000000000070178A1381150000080710464605010F32646428AC0D2F2F800C0000000005300100")))
    }

    @Test fun htIdentityIsSyntheticNotRealCapture() {
        val bytes = V12Protocol.frame(0x11, 0x82, byteArrayOf(1, 2, 7, 2, 1, 3, 0))
        val m = V12Protocol.model(V12Protocol.Decoder().feed(bytes).single())!!
        assertTrue(m.isHt)
        val hs = V12Protocol.model(V12Protocol.Decoder().feed("aaaa110882010207010103009c".hex()).single())!!
        assertFalse(hs.isHt)
        assertTrue(hs.isSupported)
        assertFalse(V12Protocol.Model(7, 4).isSupported)
    }

    /** WheelLog InmotionAdapterV2Test "decode with v12 pro full data" (GPL-3.0 fixture, not a user capture). */
    @Test fun v12ProIsSupportedAndDecodesWithTheV12Layout() {
        val frames = V12Protocol.Decoder().feed(("aaaa110882010207030101009c" +
            "aaaa144384b7261100000085ff5c00000000000000fcff0000eafe000076266d26803ee015581b000000000000c8c800c9b0c7b0b700000000000049000000000000000000000081").hex())
        val model = V12Protocol.model(frames[0])!!
        assertTrue(model.isSupported); assertFalse(model.isHt); assertEquals("V12 Pro", model.label)
        val t = V12Protocol.telemetry(frames[1])!!
        assertEquals(99.11, t.voltage, 0.0001); assertEquals(0.17, t.current, 0.0001)
        assertEquals(-2.78, t.roll, 0.0001); assertEquals(24, t.mosTemp); assertEquals(24, t.motorTemp)
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
