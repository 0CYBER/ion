package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class TripTimelineTest {
    private fun t(speed: Double, v: Double = 90.0) =
        V12Protocol.Telemetry(v, 5.0, speed, 70.0, 40, 45, 0.0, 0.0, false, false, false, tripKm = 1.0)

    @Test fun readsTheRecordedCsvBack() {
        val rows = listOf(TripCsv.HEADER, TripCsv.row(0, 0, t(0.0), 0.0, null), TripCsv.row(0, 500, t(12.5, 88.0), 0.01, null), "garbage")
        val m = TripTimeline.parse(rows.asSequence())
        assertEquals(2, m.size)
        assertEquals(500L, m[1].t); assertEquals(12.5, m[1].speed, 1e-9); assertEquals(88.0, m[1].voltage!!, 1e-9)
        assertEquals(70.0, m[1].battery!!, 1e-9); assertEquals(45, m[1].motorTemp); assertEquals(0.01, m[1].distanceKm!!, 1e-9)
    }

    @Test fun olderFilesWithFewerColumnsStillLoad() {
        val m = TripTimeline.parse(sequenceOf("time_utc,elapsed_ms,speed_kmh", "x,100,7.5"))
        assertEquals(listOf(Moment(100, 7.5, null, null, null, null, null, null, null, null)), m)
    }

    @Test fun thinningKeepsSpeedPeaksAndTheBound() {
        val many = List(10_000) { i -> Moment(i * 200L, if (i == 4321) 55.0 else 10.0, null, null, null, null, null, null, null, null) }
        val thin = TripTimeline.thin(many, 500)
        assertTrue(thin.size <= 500)
        assertEquals(55.0, thin.maxOf { it.speed }, 1e-9)
    }

    @Test fun nearestFindsTheClosestTime() {
        val times = listOf(0L, 1000, 2000, 5000)
        assertEquals(0, TripTimeline.nearest(times, -50) { it })
        assertEquals(1, TripTimeline.nearest(times, 1400) { it })
        assertEquals(2, TripTimeline.nearest(times, 2600) { it })
        assertEquals(3, TripTimeline.nearest(times, 9000) { it })
        assertEquals(-1, TripTimeline.nearest(emptyList<Long>(), 1) { it })
    }
}
