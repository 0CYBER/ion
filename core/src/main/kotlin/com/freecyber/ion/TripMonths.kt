package com.freecyber.ion

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One calendar month of the ride log, with its distance shown in the section header. */
data class TripMonth(val month: YearMonth, val trips: List<TripSummary>) {
    val distanceKm: Double get() = trips.sumOf { it.distanceKm }
}

object TripMonths {
    /** Splits [trips] into consecutive months in their existing order (the log is newest first); a ride belongs to the month it started in. */
    fun group(trips: List<TripSummary>, zone: ZoneId): List<TripMonth> {
        val out = ArrayList<TripMonth>()
        var current: YearMonth? = null
        var bucket = ArrayList<TripSummary>()
        for (t in trips) {
            val m = YearMonth.from(Instant.ofEpochMilli(t.startedAt).atZone(zone))
            if (m != current) {
                current?.let { out += TripMonth(it, bucket) }
                current = m; bucket = ArrayList()
            }
            bucket += t
        }
        current?.let { out += TripMonth(it, bucket) }
        return out
    }
}
