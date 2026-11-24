package com.freecyber.ion

import java.util.zip.CRC32

/**
 * V12 personal ("DIY") light effects: the frame file, its upload to the wheel and the effect list.
 *
 * Everything here was read off the vendor app (INMOTION 8.1.34) saving a two-frame effect on the owner's V12 HT
 * (`docs/evidence/light-upload-frames.txt`); the tests rebuild those exact bytes.
 *
 * File `0:/light/diy/<id>.rgb`, little-endian: u32 format (1), u32 effect id, u32 frame count, u32 data length,
 * u16 frame interval in 10 ms, u16 "related to speed" (the app sends 20), then per frame [LEDS] × R,G,B.
 *
 * Upload, one request and one reply at a time, as the app does it: a capability ping (channel 2), a storage query
 * (channel 5), then on channel 4 open `<id>.tmp` (0x81), chunks of up to [CHUNK] bytes (0x82, numbered frames with
 * flag 0x40), close with size and CRC-32 (0x83), finish (0x84), rename to `.rgb` (0x87, addressed to 0), and finally
 * select the effect (control 0x60, `0x2d` + id). The effect list is a directory listing (0x08) of `0:/light/diy`;
 * a file is deleted with 0x86 + path (addressed to 0), as the app does it (`docs/evidence/light-delete-frames.txt`).
 */
object LightEffects {
    const val LEDS = 70
    const val MAX_FRAMES = 60
    const val CHUNK = 176
    const val FORMAT = 1
    const val SPEED_LINK = 20
    val INTERVAL_MS = 100..1000
    /** Ids of effects made in Ion; the app's own come from InMotion's server (25032, 30321 …). */
    const val ION_ID_BASE = 900_000L
    private const val DIR = "0:/light/diy"

    /**
     * Colour as stored for the wheel. The app keeps what the rider picked (0xFF008000 for its green) but wrote
     * 00 20 00: a cubic curve (128 → 32, 255 → 255), presumably so LED brightness follows the picker. Only the
     * points 0, 128 and 255 are confirmed.
     */
    fun wheelLevel(c: Int): Int = Math.round(255.0 * Math.pow(c.coerceIn(0, 255) / 255.0, 3.0)).toInt()

    /** The file for [frames], each [LEDS] colours as 0xRRGGBB (what the rider picked). */
    fun file(id: Long, frames: List<IntArray>, intervalMs: Int, speedLink: Int = SPEED_LINK): ByteArray {
        require(id in 1..0xffff_ffffL && frames.size in 1..MAX_FRAMES && frames.all { it.size == LEDS })
        require(intervalMs in INTERVAL_MS && speedLink in 0..0xffff)
        val data = ByteArray(frames.size * LEDS * 3)
        var i = 0
        frames.forEach { frame -> frame.forEach { rgb ->
            data[i++] = wheelLevel(rgb shr 16 and 0xff).toByte()
            data[i++] = wheelLevel(rgb shr 8 and 0xff).toByte()
            data[i++] = wheelLevel(rgb and 0xff).toByte()
        } }
        return le32(FORMAT.toLong()) + le32(id) + le32(frames.size.toLong()) + le32(data.size.toLong()) +
            le16(intervalMs / 10) + le16(speedLink) + data
    }

    fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    /** A request on the vendor's addressed bus: source 2 (the phone), [target], [channel], then [body]. */
    fun message(flags: Int, target: Int, channel: Int, body: ByteArray, seq: Int? = null): ByteArray {
        val content = byteArrayOf(0x02, target.toByte(), channel.toByte()) + body
        require(content.size <= 255 && (seq == null) == (flags and 0x40 == 0))
        val raw = listOf(flags) + listOfNotNull(seq) + listOf(content.size) + content.map { it.toInt() and 0xff }
        val out = mutableListOf(0xaa.toByte(), 0xaa.toByte())
        raw.forEach { b -> if (b == 0xaa || b == 0xa5) out += 0xa5.toByte(); out += b.toByte() }
        out += raw.fold(0) { a, b -> a xor b }.toByte()
        return out.toByteArray()
    }

    /** One upload step: the bytes to write and the reply that confirms it (null: the app does not wait for one). */
    data class Step(val label: String, val bytes: ByteArray, val channel: Int?, val property: Int? = null, val seq: Int? = null)

    /**
     * Every write of an upload. Chunks start at [CHUNK] bytes and shrink when escaping would make the frame longer
     * than [maxWrite] (the link's MTU − 3); offsets are explicit, so the wheel does not need equal chunks.
     */
    fun upload(id: Long, file: ByteArray, maxWrite: Int = 244): List<Step> {
        val tmp = path(id, true); val rgb = path(id, false)
        val steps = mutableListOf(
            Step("ping", message(0x16, 0x21, 0x02, byteArrayOf()), 0x82),
            Step("storage", message(0x13, 0x00, 0x05, byteArrayOf(0x01, 0x00)), 0x85, 0x01),
            Step("open", message(0x13, 0x21, 0x04, byteArrayOf(0x81.toByte(), 0x02) + tmp.ascii() + 0), 0x84, 0x81),
        )
        var offset = 0; var seq = 1
        while (offset < file.size) {
            var length = minOf(CHUNK, file.size - offset)
            fun chunk(n: Int) = message(0x53, 0x21, 0x04,
                byteArrayOf(0x82.toByte(), 0x00) + le32(offset.toLong()) + file.copyOfRange(offset, offset + n), seq and 0xff)
            var bytes = chunk(length)
            while (bytes.size > maxWrite && length > 8) { length -= 8; bytes = chunk(length) }
            require(bytes.size <= maxWrite) { "MTU too small" }
            steps += Step("chunk", bytes, 0x84, 0x82, seq and 0xff)
            offset += length; seq++
        }
        val size = le32(file.size.toLong())
        steps += Step("close", message(0x13, 0x21, 0x04, byteArrayOf(0x83.toByte(), 0x00) + size + size + le32(crc32(file))), 0x84, 0x83)
        steps += Step("finish", message(0x13, 0x21, 0x04, byteArrayOf(0x84.toByte(), 0x00)), null)
        steps += Step("rename", message(0x13, 0x00, 0x04, byteArrayOf(0x87.toByte()) + "$tmp>$rgb".ascii()), 0x84, 0x87)
        steps += select(id)
        return steps
    }

    fun path(id: Long, temporary: Boolean) = "$DIR/$id." + if (temporary) "tmp" else "rgb"

    /** Deletes an effect file; the app sends exactly this for its own "delete". The file system ignores case. */
    fun delete(id: Long, temporary: Boolean = false) =
        Step("delete", message(0x13, 0x00, 0x04, byteArrayOf(0x86.toByte()) + path(id, temporary).ascii() + 0), 0x84, 0x86)

    /** Makes a stored effect the active one (the app sends this right after an upload). */
    fun select(id: Long) = Step("select", message(0x16, 0x21, 0x60, byteArrayOf(0x2d) + le32(id)), 0xe0, 0x2d)

    /** Lists the `.rgb` files in the effect folder. */
    fun list() = Step("list", message(0x13, 0x00, 0x04, byteArrayOf(0x08, 0x00, 0x00) + "1|1|$DIR|.rgb".ascii()), 0x84, 0x08)

    /** A reply on the addressed bus: [channel] has the 0x80 reply bit; [data] starts with the status byte (0 = ok). */
    data class Reply(val channel: Int, val property: Int, val data: ByteArray, val seq: Int?) {
        val ok get() = data.firstOrNull()?.toInt() == 0
        fun answers(step: Step) = channel == step.channel && (step.property == null || property == step.property) &&
            (step.seq == null || seq == step.seq)
    }

    /**
     * Flags 0x13/0x16/0x53 carry the source (0x21) before the target; 0x12 comes from address 0 and omits it.
     * Only frames addressed to the phone (2) count.
     */
    fun reply(f: V12Protocol.Frame): Reply? {
        val p = f.payload.map { it.toInt() and 0xff }
        return when (f.flags) {
            0x12 -> if (f.commandByte != 0x02 || p.size < 2) null else Reply(p[0], p[1], f.payload.copyOfRange(2, f.payload.size), f.seq)
            0x13, 0x16, 0x53 -> if (f.commandByte != 0x21 || p.size < 3 || p[0] != 0x02) null
                else Reply(p[1], p[2], f.payload.copyOfRange(3, f.payload.size), f.seq)
            else -> null
        }
    }

    /** An effect file on the wheel. [temporary]: a `.tmp` left by an upload that never finished; the wheel offers
     * it as an effect that does nothing, the vendor app hides it. */
    data class Stored(val id: Long, val size: Long, val temporary: Boolean = false)

    /** Parses the directory listing reply: status, 4 bytes, folder name, count, then name/size/checksum entries. */
    fun parseList(r: Reply): List<Stored>? {
        if (r.channel != 0x84 || r.property != 0x08 || !r.ok) return null
        val d = r.data.map { it.toInt() and 0xff }
        var at = 5
        val dirLength = d.getOrNull(at) ?: return null
        at += 1 + dirLength
        val count = d.getOrNull(at++) ?: return null
        val out = ArrayList<Stored>()
        repeat(count) {
            val n = d.getOrNull(at++) ?: return out
            if (at + n + 8 > d.size) return out
            val name = String(ByteArray(n) { d[at + it].toByte() }, Charsets.US_ASCII).trimEnd('\u0000')
            at += n
            val size = (0..3).sumOf { (d[at + it].toLong()) shl (8 * it) }
            at += 8
            val ext = name.substringAfterLast('.', "").uppercase()
            if (ext == "RGB" || ext == "TMP") name.substringBefore('.').toLongOrNull()?.let { out += Stored(it, size, ext == "TMP") }
        }
        return out
    }

    private fun String.ascii() = toByteArray(Charsets.US_ASCII)
    private operator fun ByteArray.plus(b: Int) = this + byteArrayOf(b.toByte())
    private fun le32(v: Long) = ByteArray(4) { (v shr (8 * it)).toByte() }
    private fun le16(v: Int) = ByteArray(2) { (v shr (8 * it)).toByte() }
}
