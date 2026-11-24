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
        assertEquals(setOf(TripArchive.MANIFEST, TripArchive.SUMMARY, "Ion_2026-09-21_1413.csv", "Ion_2026-09-22_1413.gpx", "ion/${second}.track"), files.keys)
        assertEquals(3, files.getValue(TripArchive.SUMMARY).trim().lines().size)
        assertTrue(files.getValue("Ion_2026-09-21_1413.csv").startsWith(TripCsv.HEADER))
        assertTrue(files.getValue("Ion_2026-09-22_1413.gpx").contains("<trkpt"))
    }

    @Test fun ridesInTheSameMinuteGetDistinctNames() {
        val at = 1_790_000_000_000
        val csv = File.createTempFile("ride", ".csv").apply { writeText("x"); deleteOnExit() }
        val out = ByteArrayOutputStream()
        TripArchive.write(out, listOf(trip(at + 20_000), trip(at)), csv = { csv }, track = { emptyList() }, zone = utc)
        assertEquals(setOf(TripArchive.MANIFEST, TripArchive.SUMMARY, "Ion_2026-09-21_1413.csv", "Ion_2026-09-21_1413_2.csv"), entries(out.toByteArray()).keys)
    }

    @Test fun exportedArchiveRestoresRidesCsvAndTracksExactly() {
        val first = 1_790_000_000_000; val second = first + 86_400_000
        val rides = listOf(trip(second).copy(maxPower = 2400.0, minVoltage = 88.1), trip(first))
        val source = File.createTempFile("ride", ".csv").apply { writeText(TripCsv.HEADER + "\nrow1\nrow2\n"); deleteOnExit() }
        val track = listOf(TrackPoint(0, 42.87, 74.59, 760.0, 5.5), TrackPoint(1000, 42.88, 74.6, 761.0, 6.0))
        val out = ByteArrayOutputStream()
        TripArchive.write(out, rides, csv = { if (it == second) source else null }, track = { if (it == second) track else emptyList() })

        val dir = kotlin.io.path.createTempDirectory().toFile().apply { deleteOnExit() }
        val tracks = HashMap<Long, List<TrackPoint>>()
        val restored = TripArchive.read(ByteArrayInputStream(out.toByteArray()), existing = setOf(first),
            csvFile = { File(dir, "$it.csv") }, saveTrack = { id, p -> tracks[id] = p })
        assertEquals(listOf(rides[0]), restored)
        assertEquals(source.readText(), File(dir, "$second.csv").readText())
        assertEquals(track, tracks[second])
        assertFalse(File(dir, "$first.csv").exists())
    }

    @Test fun anyOtherZipIsRejected() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { it.putNextEntry(java.util.zip.ZipEntry("photo.jpg")); it.write(1); it.closeEntry() }
        assertThrows(TripArchive.NotABackup::class.java) {
            TripArchive.read(ByteArrayInputStream(out.toByteArray()), emptySet(), { File("x") }, { _, _ -> })
        }
        assertThrows(TripArchive.NotABackup::class.java) {
            TripArchive.read(ByteArrayInputStream("not a zip".toByteArray()), emptySet(), { File("x") }, { _, _ -> })
        }
    }
}
