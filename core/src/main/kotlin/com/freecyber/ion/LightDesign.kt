package com.freecyber.ion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * A personal light effect made in Ion: either a template with two colours or frames painted LED by LED ([custom]),
 * plus a frame interval, turned into explicit frames for [LightEffects.file]. Templates follow [LedLayout]: the
 * gradient runs top to bottom, chase and rainbow go round the ring, halves split left from right.
 */
data class LightDesign(
    /** 1..[SLOTS]: the design's place in Ion. */
    val slot: Int,
    val name: String,
    val template: Template = Template.SOLID,
    val color1: Int = 0xFF0000,
    val color2: Int = 0x0000FF,
    val intervalMs: Int = 100,
    /** Id of the copy last uploaded to the wheel. Every upload takes a fresh id: the wheel refuses to rename a new
     * file onto an existing `<id>.rgb`, which left a half-written `.tmp` effect behind when an id was reused. */
    val wheelId: Long? = null,
    /** Frames painted in the editor, [LightEffects.LEDS] colours each; set exactly when [template] is CUSTOM. */
    val custom: List<List<Int>>? = null,
) {
    enum class Template { SOLID, BLINK, BREATHE, GRADIENT, CHASE, RAINBOW, HALVES, CUSTOM;
        /** Templates the editor offers as a starting point. */
        companion object { val generators get() = entries - CUSTOM }
    }

    fun frames(): List<IntArray> {
        val n = LightEffects.LEDS
        return when (template) {
            Template.CUSTOM -> custom.orEmpty().map { it.toIntArray() }.ifEmpty { listOf(IntArray(n)) }
            Template.SOLID -> listOf(IntArray(n) { color1 })
            Template.BLINK -> listOf(IntArray(n) { color1 }, IntArray(n) { color2 })
            Template.BREATHE -> List(BREATHE_FRAMES) { f ->
                // 10 % → 100 % → 10 % over one cycle.
                val k = 0.1 + 0.9 * (1 - cos(2 * PI * f / BREATHE_FRAMES)) / 2
                IntArray(n) { scale(color1, k) }
            }
            Template.GRADIENT -> listOf(IntArray(n) { mix(color1, color2, LedLayout.height(it)) })
            Template.CHASE -> List(CHASE_FRAMES) { f ->
                IntArray(n) { i -> if ((LedLayout.phase(i) - f.toDouble() / CHASE_FRAMES + 1) % 1 < CHASE_WIDTH) color1 else color2 }
            }
            Template.RAINBOW -> List(RAINBOW_FRAMES) { f -> IntArray(n) { i -> hue((LedLayout.phase(i) + f.toDouble() / RAINBOW_FRAMES) % 1.0) } }
            Template.HALVES -> listOf(IntArray(n) { if (LedLayout.isLeft(it)) color1 else color2 }, IntArray(n) { if (LedLayout.isLeft(it)) color2 else color1 })
        }
    }

    fun file(id: Long): ByteArray = LightEffects.file(id, frames(), intervalMs)

    /** The same effect as painted frames, for the editor. */
    fun painted(): LightDesign = copy(template = Template.CUSTOM, custom = frames().map { it.toList() })

    /**
     * `slot;template;color1;color2;interval;wheelId;name[;frames]` (the first build wrote no wheelId field); painted
     * frames are 6 hex digits per LED, frames separated by commas.
     */
    fun encode(): String = (listOf(slot, template.name, "%06X".format(color1), "%06X".format(color2), intervalMs,
        wheelId?.toString().orEmpty(), name.replace('\n', ' ').replace(';', ',')) +
        listOfNotNull(custom?.joinToString(",") { f -> f.joinToString("") { "%06X".format(it) } })).joinToString(";")

    companion object {
        const val SLOTS = 9
        const val MAX_NAME = 24
        const val BREATHE_FRAMES = 16
        const val CHASE_FRAMES = 14
        /** Share of the circle the chase lights up. */
        const val CHASE_WIDTH = 0.1
        const val RAINBOW_FRAMES = 14
        /** Swatches offered in the editor. */
        val PALETTE = listOf(0xFF0000, 0xFF6A00, 0xFFC800, 0x7CFF00, 0x00FF00, 0x00FFA0, 0x00E5FF,
            0x0070FF, 0x0000FF, 0x8A00FF, 0xFF00D4, 0xFFFFFF, 0x000000)

        fun decode(line: String): LightDesign? {
            val parts = line.split(';')
            if (parts.size < 6) return null
            // Names never contain ';', so 6 fields is the first build's layout and 7 the one with the wheel id.
            val p = if (parts.size >= 7) parts.take(5) + parts[6] else parts
            val custom = parts.getOrNull(7)?.split(',')?.map { f ->
                if (f.length != LightEffects.LEDS * 6) return null
                f.chunked(6).map { it.toIntOrNull(16) ?: return null }
            }?.takeIf { it.size in 1..LightEffects.MAX_FRAMES }
            val wheelId = if (parts.size >= 7) parts[5].toLongOrNull() else null
            val slot = p[0].toIntOrNull()?.takeIf { it in 1..SLOTS } ?: return null
            val template = Template.entries.firstOrNull { it.name == p[1] } ?: return null
            val c1 = p[2].toIntOrNull(16)?.takeIf { it in 0..0xFFFFFF } ?: return null
            val c2 = p[3].toIntOrNull(16)?.takeIf { it in 0..0xFFFFFF } ?: return null
            val interval = p[4].toIntOrNull()?.takeIf { it in LightEffects.INTERVAL_MS } ?: return null
            if ((template == Template.CUSTOM) != (custom != null)) return null
            return LightDesign(slot, p[5].take(MAX_NAME), template, c1, c2, interval, wheelId, custom)
        }
        fun decodeAll(text: String?): List<LightDesign> =
            text?.lineSequence()?.mapNotNull(::decode)?.distinctBy { it.slot }?.sortedBy { it.slot }?.toList().orEmpty()
        fun encodeAll(list: List<LightDesign>) = list.joinToString("\n") { it.encode() }
        fun freeSlot(list: List<LightDesign>): Int? = (1..SLOTS).firstOrNull { s -> list.none { it.slot == s } }
        /** Whether a stored effect was uploaded by Ion (the vendor app's ids come from InMotion's server). */
        fun isIonId(id: Long) = id > LightEffects.ION_ID_BASE

        /** A fresh id above every Ion id the wheel or this phone knows of, so an upload never lands on an existing file. */
        fun nextId(stored: List<LightEffects.Stored>, designs: List<LightDesign>): Long =
            maxOf(LightEffects.ION_ID_BASE, (stored.map { it.id } + designs.mapNotNull { it.wheelId }).filter(::isIonId).maxOrNull() ?: 0L) + 1

        fun scale(rgb: Int, k: Double): Int = rgbOf((rgb shr 16 and 0xff) * k, (rgb shr 8 and 0xff) * k, (rgb and 0xff) * k)
        fun mix(a: Int, b: Int, t: Double): Int {
            fun ch(shift: Int) = (a shr shift and 0xff) * (1 - t) + (b shr shift and 0xff) * t
            return rgbOf(ch(16), ch(8), ch(0))
        }
        fun hue(h: Double): Int {
            val x = h * 6; val i = x.toInt() % 6; val f = x - x.toInt()
            val (r, g, b) = when (i) { 0 -> Triple(1.0, f, 0.0); 1 -> Triple(1 - f, 1.0, 0.0); 2 -> Triple(0.0, 1.0, f)
                3 -> Triple(0.0, 1 - f, 1.0); 4 -> Triple(f, 0.0, 1.0); else -> Triple(1.0, 0.0, 1 - f) }
            return rgbOf(r * 255, g * 255, b * 255)
        }
        private fun rgbOf(r: Double, g: Double, b: Double) =
            (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
    }
}
