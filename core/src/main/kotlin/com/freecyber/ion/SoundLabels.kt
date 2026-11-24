package com.freecyber.ion

/**
 * The rider's own names for the wheel's sound slots, and the slots they hid.
 *
 * The wheel plays a sound by slot number (`0x51 [id, 0x01]`); what each slot sounds like depends on the sound pack
 * installed on the wheel, and the vendor app takes the names from InMotion's server together with the pack, so
 * nothing in the protocol or the app says what slot 7 is. Ion therefore lets the rider listen, name and hide slots
 * (duplicates, silent ones) on this phone. Slot 24 (`0x18`) is the horn WheelLog uses on the V12.
 */
data class SoundLabels(val names: Map<Int, String> = emptyMap(), val hidden: Set<Int> = emptySet()) {
    fun name(id: Int): String? = names[id]?.takeIf(String::isNotBlank)
    fun rename(id: Int, name: String) =
        copy(names = if (name.isBlank()) names - id else names + (id to name.trim().take(MAX_NAME)))
    fun hide(id: Int, hide: Boolean) = copy(hidden = if (hide) hidden + id else hidden - id)

    /** One line per slot that has a name or is hidden: `id;hidden;name` (the name may contain `;`). */
    fun encode(): String = (names.keys + hidden).sorted().joinToString("\n") { id ->
        "$id;${if (id in hidden) 1 else 0};${names[id].orEmpty().replace('\n', ' ')}"
    }

    companion object {
        const val HORN = 24
        const val MAX_NAME = 32

        fun decode(text: String?): SoundLabels {
            if (text.isNullOrBlank()) return SoundLabels()
            val names = HashMap<Int, String>(); val hidden = HashSet<Int>()
            text.lineSequence().forEach { line ->
                val parts = line.split(';', limit = 3)
                val id = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in V12Protocol.soundIds } ?: return@forEach
                if (parts.getOrNull(1) == "1") hidden += id
                parts.getOrNull(2)?.takeIf(String::isNotBlank)?.let { names[id] = it.take(MAX_NAME) }
            }
            return SoundLabels(names, hidden)
        }
    }
}
