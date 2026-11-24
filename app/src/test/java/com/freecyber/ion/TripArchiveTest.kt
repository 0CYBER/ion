package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.TimeZone
import java.util.zip.ZipInputStream

class TripArchiveTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun trip(at: Long) = TripSummary(at, 600_000, 500_000, 3.2, 31.0, 22.0, 60.0, 4.0, 5.0, 40)

    private fun entries(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { put(it.name, zip.readBytes().decodeToString()) }
        }
    }

    @Test fun zipHasSummaryAndEachRidesCsvAndGpx() {
        val first = 1_790_000_000_000; val second = first + 86_400_000
        val csv = File.createTempFile("ride", ".csv").apply { writeText(TripCsv.HEADER + "\nrow\n"); deleteOnExit() }
        val track = listOf(TrackPoint(0, 42.87, 74.59), TrackPoint(1000, 42.88, 74.60))
        val out = ByteArrayOutputStream()
        TripArchive.write(out, listOf(second, first).map(::trip), csv = { if (it == first) csv else null },
            track = { if (it == second) track else emptyList() }, zone = utc)
        val files = entries(out.toByteArray())
        assertEquals(setOf(TripArchive.SUMMARY, "Ion_2026-09-21_1413.csv", "Ion_2026-09-22_1413.gpx"), files.keys)
        assertEquals(3, files.getValue(TripArchive.SUMMARY).trim().lines().size)
        assertTrue(files.getValue("Ion_2026-09-21_1413.csv").startsWith(TripCsv.HEADER))
        assertTrue(files.getValue("Ion_2026-09-22_1413.gpx").contains("<trkpt"))
    }

    @Test fun ridesInTheSameMinuteGetDistinctNames() {
        val at = 1_790_000_000_000
        val csv = File.createTempFile("ride", ".csv").apply { writeText("x"); deleteOnExit() }
        val out = ByteArrayOutputStream()
        TripArchive.write(out, listOf(trip(at + 20_000), trip(at)), csv = { csv }, track = { emptyList() }, zone = utc)
        assertEquals(setOf(TripArchive.SUMMARY, "Ion_2026-09-21_1413.csv", "Ion_2026-09-21_1413_2.csv"), entries(out.toByteArray()).keys)
    }
}
