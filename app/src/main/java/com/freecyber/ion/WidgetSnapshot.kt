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

        fun from(s: WheelState, trip: TripStats, reconnecting: Boolean, hasSaved: Boolean, now: Long): WidgetSnapshot {
            val t = s.telemetry
            val state = when {
                s.verifiedHt && s.phase == Phase.CONNECTED -> State.LIVE
                reconnecting -> State.RECONNECTING
                s.phase in listOf(Phase.CONNECTING, Phase.DISCOVERING, Phase.IDENTIFYING) -> State.CONNECTING
                s.phase == Phase.ERROR -> State.ERROR
                else -> State.OFFLINE
            }
            val live = state == State.LIVE && t != null
            return WidgetSnapshot(state,
                t?.battery?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { kotlin.math.round(it).toInt() },
                if (live) t?.speed else null,
                t?.rangeKm?.takeIf { it > 0 },
                trip.distanceKm.takeIf { trip.samples > 0 },
                t?.let { maxOf(it.mosTemp, it.motorTemp) }?.takeIf { it in -50..180 },
                live && t?.lowBeam == true, hasSaved, now)
        }
    }
}
