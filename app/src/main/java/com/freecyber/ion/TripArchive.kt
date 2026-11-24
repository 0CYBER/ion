package com.freecyber.ion

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * "All rides" export: one zip with a summary table (one row per ride) and, per ride, the same telemetry CSV and GPX
 * that the ride detail shares on its own. Rides recorded before telemetry logging only appear in the summary.
 */
internal object TripArchive {
    const val SUMMARY = "rides.csv"

    /** File stem of a ride inside the archive, matching the single-ride export name. */
    fun stem(startedAt: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "Ion_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).apply { timeZone = zone }.format(Date(startedAt))

    fun write(out: OutputStream, trips: List<TripSummary>, csv: (Long) -> File?, track: (Long) -> List<TrackPoint>,
              zone: TimeZone = TimeZone.getDefault()) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(SUMMARY)); zip.write(TripSummary.csv(trips).toByteArray()); zip.closeEntry()
            val used = mutableSetOf<String>()
            trips.forEach { trip ->
                // Two rides started in the same minute would share a name; the second gets a suffix.
                var name = stem(trip.startedAt, zone); var n = 2
                while (!used.add(name)) name = stem(trip.startedAt, zone) + "_" + n++
                csv(trip.startedAt)?.takeIf { it.isFile }?.let { file ->
                    zip.putNextEntry(ZipEntry("$name.csv").apply { time = trip.startedAt }); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                val points = track(trip.startedAt)
                if (points.size >= 2) {
                    zip.putNextEntry(ZipEntry("$name.gpx").apply { time = trip.startedAt })
                    zip.write(TrackCodec.gpx(points, trip.startedAt, name).toByteArray()); zip.closeEntry()
                }
            }
        }
    }
}
