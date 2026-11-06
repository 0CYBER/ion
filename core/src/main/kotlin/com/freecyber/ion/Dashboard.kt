package com.freecyber.ion

/** Every building block the ride dashboard can show. Order here is the catalogue order in the editor.
 * [defaultWide] is the initial size; [canBeHalf] false keeps hero-sized tiles full width. */
enum class DashTile(val defaultVisible: Boolean, val defaultWide: Boolean, val canBeHalf: Boolean = true) {
    SPEED(true, true, canBeHalf = false),
    BATTERY(true, false),
    RANGE(true, false),
    QUICK_LIGHT(true, false),
    QUICK_MUTE(true, false),
    TRIP_SESSION(true, true, canBeHalf = false),
    TRIP(true, false),
    ODOMETER(true, false),
    VOLTAGE(true, false),
    CURRENT(true, false),
    POWER(false, false),
    MOTOR_TEMP(true, false),
    MOS_TEMP(true, false),
    PWM(false, false),
    TILT(false, false),
    QUICK_HIGH_BEAM(false, false),
    PLAY_SOUND(false, false),
    MODE(false, true, canBeHalf = true),
    CHART(true, true, canBeHalf = false),
}

data class TileSlot(val tile: DashTile, val visible: Boolean, val wide: Boolean) {
    val effectiveWide get() = wide || !tile.canBeHalf
}

/** User-ordered list of dashboard slots. Encoding is a compact, forward-compatible string:
 * unknown ids are dropped and tiles added in later versions are appended with their defaults. */
data class DashboardLayout(val slots: List<TileSlot>) {
    companion object {
        val DEFAULT = DashboardLayout(DashTile.entries.map { TileSlot(it, it.defaultVisible, it.defaultWide) })
        fun decode(text: String?): DashboardLayout {
            if (text.isNullOrBlank()) return DEFAULT
            val seen = LinkedHashMap<DashTile, TileSlot>()
            text.split(',').forEach { part ->
                val bits = part.split(':')
                val tile = DashTile.entries.firstOrNull { it.name == bits.getOrNull(0) } ?: return@forEach
                if (tile in seen) return@forEach
                seen[tile] = TileSlot(tile, bits.getOrNull(1) != "0", bits.getOrNull(2)?.let { it == "1" } ?: tile.defaultWide)
            }
            if (seen.isEmpty()) return DEFAULT
            DashTile.entries.filter { it !in seen }.forEach { seen[it] = TileSlot(it, it.defaultVisible, it.defaultWide) }
            return DashboardLayout(seen.values.toList())
        }
    }
    fun encode(): String = slots.joinToString(",") { "${it.tile.name}:${if (it.visible) 1 else 0}:${if (it.wide) 1 else 0}" }
    val visible get() = slots.filter { it.visible }
    fun toggle(tile: DashTile) = copy(slots = slots.map { if (it.tile == tile) it.copy(visible = !it.visible) else it })
    fun resize(tile: DashTile) = copy(slots = slots.map { if (it.tile == tile && it.tile.canBeHalf) it.copy(wide = !it.wide) else it })
    /** Moves a tile by [delta] positions inside the full list, clamped to the bounds. */
    fun move(tile: DashTile, delta: Int): DashboardLayout {
        val from = slots.indexOfFirst { it.tile == tile }
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, slots.lastIndex)
        if (to == from) return this
        val list = slots.toMutableList(); val item = list.removeAt(from); list.add(to, item)
        return copy(slots = list)
    }
    /** Moves a tile among visible tiles only, so "up" in the editor never lands behind a hidden tile. */
    fun moveVisible(tile: DashTile, delta: Int): DashboardLayout {
        val vis = visible
        val index = vis.indexOfFirst { it.tile == tile }
        val target = vis.getOrNull(index + delta) ?: return this
        val from = slots.indexOfFirst { it.tile == tile }
        val to = slots.indexOfFirst { it.tile == target.tile }
        return move(tile, to - from)
    }
    /** Packs visible slots into rows: wide tiles alone, half tiles in pairs; a lone half fills its row. */
    fun rows(): List<List<TileSlot>> {
        val rows = mutableListOf<List<TileSlot>>()
        var pending: TileSlot? = null
        for (slot in visible) {
            if (slot.effectiveWide) {
                pending?.let { rows += listOf(it); pending = null }
                rows += listOf(slot)
            } else {
                val p = pending
                if (p == null) pending = slot else { rows += listOf(p, slot); pending = null }
            }
        }
        pending?.let { rows += listOf(it) }
        return rows
    }
}
