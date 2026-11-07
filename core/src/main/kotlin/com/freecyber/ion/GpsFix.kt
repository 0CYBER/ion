package com.freecyber.ion

/** Reject stale fixes, invalid coordinates, duplicate/out-of-order timestamps and impossible jumps. */
object GpsFix {
    fun valid(lat: Double, lon: Double, accuracy: Double, elapsed: Long, now: Long, start: Long): Boolean =
        lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0 &&
            accuracy.isFinite() && accuracy in 0.0..50.0 && elapsed >= start && now - elapsed in 0..10_000

    fun follows(previous: TrackPoint?, next: TrackPoint): Boolean {
        if (previous == null) return true
        val dt = next.t - previous.t
        return dt > 0 && Track.metres(previous, next) <= 70.0 * dt / 1000 + 20
    }
}
