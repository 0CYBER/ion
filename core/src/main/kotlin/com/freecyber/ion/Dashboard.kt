package com.freecyber.ion

/** Every building block the ride dashboard can show. Order here is the catalogue order in the editor.
 * [defaultWide] is the initial size; [canBeHalf] false keeps hero-sized tiles full width.
 * Tiles may carry a [Param] that the rider chooses in the editor (e.g. which horn sound to play). */
enum class DashTile(val defaultVisible: Boolean, val defaultWide: Boolean, val canBeHalf: Boolean = true,
                    val param: IntRange? = null, val defaultParam: Int? = null) {
    SPEED(true, true),
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
    PLAY_SOUND(false, false, param = 1..8, defaultParam = 4),
    QUICK_AUTO_LIGHT(false, false),
    QUICK_SOUND_WAVE(false, false),
    MODE(false, true),
    CHART(true, true, canBeHalf = false),
    TOGGLE(false, false),
    MAX_SPEED(false, false),
    AVG_SPEED(false, false),
    ENERGY(false, false),
    CONSUMPTION(false, false),
}

/** Only switches with a readable wheel state belong here; no optimistic/blind toggles. */
enum class DashToggle(val control: V12Protocol.Control) {
    LOW_BEAM(V12Protocol.Control.LIGHTS), HIGH_BEAM(V12Protocol.Control.LIGHTS),
    AUTO_LIGHT(V12Protocol.Control.AUTO_LIGHT), MUTE(V12Protocol.Control.MUTE),
    SOUND_WAVE(V12Protocol.Control.SOUND_WAVE), OFF_ROAD(V12Protocol.Control.CLASSIC),
    ADVANCED(V12Protocol.Control.FANCIER), SPLIT(V12Protocol.Control.SPLIT_MODE),
    HANDLE(V12Protocol.Control.HANDLE_BUTTON), TRANSPORT(V12Protocol.Control.TRANSPORT),
    LOCK(V12Protocol.Control.LOCK);

    fun value(t: V12Protocol.Telemetry?, s: V12Protocol.Settings?): Boolean? = when(this) {
        LOW_BEAM -> t?.lowBeam; HIGH_BEAM -> t?.highBeam; AUTO_LIGHT -> s?.autoLight
        MUTE -> s?.muted; SOUND_WAVE -> s?.soundWave; OFF_ROAD -> s?.classicMode
        ADVANCED -> s?.fancier; SPLIT -> s?.splitMode; HANDLE -> s?.handleEnabled
        TRANSPORT -> s?.transport; LOCK -> s?.locked
    }
    fun write(enabled: Boolean, t: V12Protocol.Telemetry?): V12Protocol.Write = when(this) {
        LOW_BEAM -> V12Protocol.Write(control, if(enabled) 1 else 0, if(t?.highBeam==true) 1 else 0)
        HIGH_BEAM -> V12Protocol.Write(control, if(t?.lowBeam==true) 1 else 0, if(enabled) 1 else 0)
        else -> V12Protocol.Write(control, if(enabled) 1 else 0)
    }
}

data class TileSlot(val tile: DashTile, val visible: Boolean, val wide: Boolean, val param: Int? = null,
    val id: String = tile.name, val toggle: DashToggle = DashToggle.LOW_BEAM) {
    val effectiveWide get() = wide || !tile.canBeHalf
    // Retain old ids and positions when loading a pre-v2 layout.
    val toggleTarget: DashToggle? get() = when(tile) {
        DashTile.TOGGLE -> toggle
        DashTile.QUICK_LIGHT -> DashToggle.LOW_BEAM
        DashTile.QUICK_HIGH_BEAM -> DashToggle.HIGH_BEAM
        DashTile.QUICK_MUTE -> DashToggle.MUTE
        DashTile.QUICK_AUTO_LIGHT -> DashToggle.AUTO_LIGHT
        DashTile.QUICK_SOUND_WAVE -> DashToggle.SOUND_WAVE
        else -> null
    }
}

/** User-ordered list of dashboard slots. Encoding is a compact, forward-compatible string:
 * unknown ids are dropped and tiles added in later versions are appended with their defaults. */
data class DashboardLayout(val slots: List<TileSlot>) {
    companion object {
        val DEFAULT = DashboardLayout(DashTile.entries.map { TileSlot(it, it.defaultVisible, it.defaultWide, it.defaultParam) })
        fun decode(text: String?): DashboardLayout {
            if (text.isNullOrBlank()) return DEFAULT
            val v2 = text.startsWith("v2|")
            val seen = LinkedHashMap<String, TileSlot>()
            text.removePrefix("v2|").split(',').forEach { part ->
                val bits = part.split(':')
                val tile = DashTile.entries.firstOrNull { it.name == bits.getOrNull(0) } ?: return@forEach
                val id = if(v2) bits.getOrNull(4)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: tile.name else tile.name
                if (id in seen) return@forEach
                val param = bits.getOrNull(3)?.toIntOrNull()
                    ?.takeIf { tile.param != null && it in tile.param } ?: tile.defaultParam
                val toggle = DashToggle.entries.firstOrNull { it.name == bits.getOrNull(5) } ?: DashToggle.LOW_BEAM
                seen[id] = TileSlot(tile, bits.getOrNull(1) != "0", bits.getOrNull(2)?.let { it == "1" } ?: tile.defaultWide, param, id, toggle)
            }
            if(v2) return DashboardLayout(seen.values.toList())
            if (seen.isEmpty()) return DEFAULT
            DashTile.entries.filter { it.name !in seen }.forEach { seen[it.name] = TileSlot(it, it.defaultVisible, it.defaultWide, it.defaultParam) }
            return DashboardLayout(seen.values.toList())
        }
    }
    fun encode(): String = "v2|" + slots.joinToString(",") {
        "${it.tile.name}:${if (it.visible) 1 else 0}:${if (it.wide) 1 else 0}:${it.param ?: ""}:${it.id}:${it.toggle.name}"
    }
    val visible get() = slots.filter { it.visible }
    fun add(tile: DashTile): DashboardLayout {
        val hidden = slots.firstOrNull { it.tile == tile && !it.visible }
        val slot = hidden?.copy(visible = true) ?: TileSlot(tile, true, tile.defaultWide, tile.defaultParam,
            id = generateSequence(1) { it + 1 }.map { "${tile.name}_$it" }.first { id -> slots.none { it.id == id } })
        return copy(slots = slots.filterNot { it.id == slot.id } + slot)
    }
    fun remove(id: String) = copy(slots = slots.filterNot { it.id == id })
    fun resize(id: String) = copy(slots = slots.map { if(it.id == id && it.tile.canBeHalf) it.copy(wide = !it.wide) else it })
    fun setParam(id: String, param: Int) = copy(slots = slots.map {
        if(it.id == id && it.tile.param?.contains(param) == true) it.copy(param = param) else it
    })
    fun setToggle(id: String, target: DashToggle) = copy(slots = slots.map {
        if(it.id == id) it.copy(tile = DashTile.TOGGLE, toggle = target, param = null) else it
    })
    fun moveVisible(id: String, delta: Int): DashboardLayout {
        val vis = visible
        val index = vis.indexOfFirst { it.id == id }
        if(index < 0) return this
        val target = vis.getOrNull(index + delta) ?: return this
        val list = slots.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = list.indexOfFirst { it.id == target.id }
        list.add(to, list.removeAt(from))
        return copy(slots = list)
    }
    fun toggle(tile: DashTile) = copy(slots = slots.map { if (it.tile == tile) it.copy(visible = !it.visible) else it })
    fun resize(tile: DashTile) = copy(slots = slots.map { if (it.tile == tile && it.tile.canBeHalf) it.copy(wide = !it.wide) else it })
    fun setParam(tile: DashTile, param: Int) = copy(slots = slots.map {
        if (it.tile == tile && tile.param != null && param in tile.param) it.copy(param = param) else it
    })
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
