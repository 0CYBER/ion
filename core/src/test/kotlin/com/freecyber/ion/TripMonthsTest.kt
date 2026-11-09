package com.freecyber.ion

import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class TripMonthsTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun trip(y: Int, mo: Int, d: Int, h: Int, km: Double) =
        TripSummary(ZonedDateTime.of(y, mo, d, h, 0, 0, 0, zone).toInstant().toEpochMilli(), 1, 1, km, 0.0, 0.0, 0.0, 0.0, null, null)

    @Test fun emptyLogHasNoMonths() = assertTrue(TripMonths.group(emptyList(), zone).isEmpty())

    @Test fun groupsNewestFirstAndSumsDistance() {
        val trips = listOf(trip(2026, 11, 9, 10, 5.0), trip(2026, 11, 1, 0, 2.5), trip(2026, 10, 31, 23, 1.0), trip(2025, 11, 20, 12, 4.0))
        val months = TripMonths.group(trips, zone)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2025, 11)), months.map { it.month })
        assertEquals(7.5, months[0].distanceKm, 1e-9)
        assertEquals(listOf(1, 1), months.drop(1).map { it.trips.size })
    }

    @Test fun monthFollowsTheLocalZoneNotUtc() {
        // 00:30 on 1 Nov in Warsaw is still October in UTC.
        val t = trip(2026, 11, 1, 0, 1.0).copy(startedAt = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, zone).toInstant().toEpochMilli())
        assertEquals(YearMonth.of(2026, 11), TripMonths.group(listOf(t), zone).single().month)
        assertEquals(YearMonth.of(2026, 10), TripMonths.group(listOf(t), ZoneId.of("UTC")).single().month)
    }
}
