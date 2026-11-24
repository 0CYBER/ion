package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Rebuilds the owner's capture of the vendor app saving "testclaude" (frame 1 all red, frame 2 all green, 100 ms). */
class LightEffectsTest {
    private val evidence = File("../docs/evidence/light-upload-frames.txt").readLines()
    private val writes = evidence.mapNotNull { it.substringAfter("values: ", "").takeIf(String::isNotEmpty) }
        .distinct().map(::hex)
    private val replies = evidence.mapNotNull { it.substringAfter("bytes:", "").takeIf(String::isNotEmpty) }.map(::hex)
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun frames(bytes: ByteArray) = V12Protocol.Decoder().feed(bytes)

    private val red = IntArray(LightEffects.LEDS) { 0xFF0000 }
    private val green = IntArray(LightEffects.LEDS) { 0x008000 }
    private val file = LightEffects.file(30321, listOf(red, green), 100)

    @Test fun fileMatchesTheVendorFileAndItsCrc() {
        assertEquals(440, file.size)
        assertEquals(0x6401cc77L, LightEffects.crc32(file))
        assertEquals(0x20, file[20 + 210 + 1].toInt())
        assertEquals(0xff, file[20].toInt() and 0xff)
    }

    @Test fun everyUploadWriteMatchesTheCapture() {
        val steps = LightEffects.upload(30321, file, maxWrite = 244)
        val captured = writes.filterNot { it.contentEquals(LightEffects.list().bytes) }
        assertEquals(captured.map { it.toHexString() }, steps.map { it.bytes.toHexString() })
        assertTrue(writes.any { it.contentEquals(LightEffects.list().bytes) })
    }

    @Test fun repliesConfirmTheirStepsAndNumberedAcksDecode() {
        val steps = LightEffects.upload(30321, file)
        val decoded = replies.flatMap(::frames).mapNotNull(LightEffects::reply)
        assertEquals(replies.size, decoded.size)
        steps.filter { it.channel != null }.forEach { step ->
            assertTrue(step.label, decoded.any { it.answers(step) && it.ok })
        }
        assertEquals(listOf(1, 2, 3), decoded.filter { it.property == 0x82 }.map { it.seq })
    }

    @Test fun listingGivesTheEffectIds() {
        val listing = replies.flatMap(::frames).mapNotNull(LightEffects::reply).firstNotNullOf(LightEffects::parseList)
        assertEquals(listOf(25032L, 25448L, 25473L, 30321L), listing.map { it.id })
        assertEquals(440L, listing.last().size)
    }

    @Test fun escapedBytesShrinkTheChunkToFitTheLink() {
        // The colour curve never yields 0xAA/0xA5, but an id can: those header bytes need escapes, so the first
        // 176-byte chunk (191 bytes unescaped) no longer fits a 191-byte write and must shrink.
        assertTrue((0..255).none { LightEffects.wheelLevel(it) == 0xAA || LightEffects.wheelLevel(it) == 0xA5 })
        val id = 0xAAA5AAA5L
        val f = LightEffects.file(id, listOf(red, green), 100)
        val steps = LightEffects.upload(id, f, maxWrite = 191)
        assertTrue(steps.all { it.bytes.size <= 191 })
        val chunks = steps.filter { it.label == "chunk" }
        assertTrue(V12Protocol.Decoder().feed(chunks.first().bytes).single().payload.size - 8 < LightEffects.CHUNK)
        assertEquals(chunks.indices.map { it + 1 }, chunks.map { it.seq })
        // Every chunk decodes back to the bytes it carries.
        val body = chunks.flatMap { s -> V12Protocol.Decoder().feed(s.bytes).single().payload.drop(8) }
        assertEquals(f.toList(), body)
    }

    @Test fun invalidEffectsAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { LightEffects.file(1, listOf(IntArray(10)), 100) }
        assertThrows(IllegalArgumentException::class.java) { LightEffects.file(1, listOf(red), 50) }
        assertThrows(IllegalArgumentException::class.java) { LightEffects.file(1, emptyList(), 100) }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
