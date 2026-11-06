package com.freecyber.ion

/** Maps the live BLE state to the widget model; lives here because [WheelState] is Android-side. */
fun WidgetSnapshot.Companion.from(s: WheelState, trip: TripStats, reconnecting: Boolean, hasSaved: Boolean, now: Long): WidgetSnapshot {
    val t = s.telemetry
    val state = when {
        s.verifiedHt && s.phase == Phase.CONNECTED -> WidgetSnapshot.State.LIVE
        reconnecting -> WidgetSnapshot.State.RECONNECTING
        s.phase in listOf(Phase.CONNECTING, Phase.DISCOVERING, Phase.IDENTIFYING) -> WidgetSnapshot.State.CONNECTING
        s.phase == Phase.ERROR -> WidgetSnapshot.State.ERROR
        else -> WidgetSnapshot.State.OFFLINE
    }
    val live = state == WidgetSnapshot.State.LIVE && t != null
    return WidgetSnapshot(state,
        t?.battery?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { kotlin.math.round(it).toInt() },
        if (live) t?.speed else null,
        t?.rangeKm?.takeIf { it > 0 },
        trip.distanceKm.takeIf { trip.samples > 0 },
        t?.let { maxOf(it.mosTemp, it.motorTemp) }?.takeIf { it in -50..180 },
        live && t?.lowBeam == true, hasSaved, now)
}
