package com.freecyber.ion

/** A row for every received telemetry sample, never the UI's decimated chart buffer.
 * Units are explicit. Missing/stale GPS stays empty rather than inventing coordinates. */
object TripCsv {
    const val HEADER = "time_utc,elapsed_ms,speed_kmh,voltage_v,current_a,power_w,pwm_pct,battery_pct,mos_temp_c,motor_temp_c,pitch_deg,roll_deg,wheel_trip_km,session_distance_km,range_km,battery_power_w,motor_power_w,charging,motor_active,low_beam,high_beam,firmware_update,latitude,longitude,altitude_m,gps_speed_ms,gps_elapsed_ms"

    fun row(startWallMs: Long, elapsedMs: Long, t: V12Protocol.Telemetry, distanceKm: Double, gps: TrackPoint?): String {
        val fix = gps?.takeIf { elapsedMs - it.t in 0..10_000 }
        return listOf(java.time.Instant.ofEpochMilli(startWallMs + elapsedMs), elapsedMs,
            t.speed, t.voltage, t.current, t.power, t.pwm, t.battery, t.mosTemp, t.motorTemp,
            t.pitch, t.roll, t.tripKm, distanceKm, t.rangeKm, t.batteryPower, t.motorPower,
            t.charging, t.motorActive, t.lowBeam, t.highBeam, t.firmwareUpdate,
            fix?.lat, fix?.lon, fix?.alt, fix?.speed, fix?.t).joinToString(",") { it?.toString().orEmpty() }
    }
}
