package com.freecyber.ion

/** What the home-screen widget shows. Persisted as a flat string so widgets render after a process restart
 * without touching BLE; pure so encoding is unit-tested. */
data class WidgetSnapshot(
    val state: State = State.OFFLINE,
    val battery: Int? = null,
    val speed: Double? = null,
    val rangeKm: Double? = null,
    val tripKm: Double? = null,
    val temperature: Int? = null,
    val lowBeam: Boolean = false,
    val hasSavedWheel: Boolean = false,
    val updatedAt: Long = 0,
) {
    enum class State { OFFLINE, CONNECTING, LIVE, RECONNECTING, ERROR }

    fun encode(): String = listOf(state.name, battery?.toString().orEmpty(), speed?.toString().orEmpty(),
        rangeKm?.toString().orEmpty(), tripKm?.toString().orEmpty(), temperature?.toString().orEmpty(),
        if (lowBeam) "1" else "0", if (hasSavedWheel) "1" else "0", updatedAt.toString()).joinToString(";")

    companion object {
        fun decode(text: String?): WidgetSnapshot {
            val p = text?.split(';') ?: return WidgetSnapshot()
            if (p.size < 9) return WidgetSnapshot()
            return WidgetSnapshot(
                State.entries.firstOrNull { it.name == p[0] } ?: State.OFFLINE,
                p[1].toIntOrNull(), p[2].toDoubleOrNull(), p[3].toDoubleOrNull(), p[4].toDoubleOrNull(),
                p[5].toIntOrNull(), p[6] == "1", p[7] == "1", p[8].toLongOrNull() ?: 0)
        }
    }
}
