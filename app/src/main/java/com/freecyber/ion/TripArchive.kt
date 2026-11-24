package com.freecyber.ion

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * "All rides" zip, which is also Ion's backup format.
 *
 * For people: `rides.csv` (one row per ride) and, per ride, the same telemetry CSV and GPX that the ride detail
 * shares on its own. For Ion: `ion/rides.txt` comes first (a format line, then one exact [TripSummary.encode] per
 * ride with the name of its CSV), and `ion/<startedAt>.track` holds each GPS track losslessly. Import reads the
 * manifest, then streams the CSVs straight to their place, so a large archive never sits in memory.
 */
internal object TripArchive {
    const val SUMMARY = "rides.csv"
    const val MANIFEST = "ion/rides.txt"
    private const val FORMAT = "ion-rides 1"
    private val TRACK = Regex("""ion/(\d+)\.track""")

    class NotABackup : Exception()

    /** File stem of a ride inside the archive, matching the single-ride export name. */
    fun stem(startedAt: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "Ion_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).apply { timeZone = zone }.format(Date(startedAt))

    fun write(out: OutputStream, trips: List<TripSummary>, csv: (Long) -> File?, track: (Long) -> List<TrackPoint>,
              zone: TimeZone = TimeZone.getDefault()) {
        // Two rides started in the same minute would share a name; the second gets a suffix.
        val used = mutableSetOf<String>()
        val names = trips.associate { trip ->
            var name = stem(trip.startedAt, zone); var n = 2
            while (!used.add(name)) name = stem(trip.startedAt, zone) + "_" + n++
            trip.startedAt to name
        }
        val csvFiles = trips.associate { it.startedAt to csv(it.startedAt)?.takeIf(File::isFile) }
        ZipOutputStream(out.buffered()).use { zip ->
            fun entry(name: String, time: Long? = null, body: () -> Unit) {
                zip.putNextEntry(ZipEntry(name).apply { if (time != null) this.time = time }); body(); zip.closeEntry()
            }
            entry(MANIFEST) {
                zip.write(buildString {
                    appendLine(FORMAT)
                    trips.forEach { appendLine(it.encode() + "\t" + (if (csvFiles[it.startedAt] != null) names.getValue(it.startedAt) + ".csv" else "")) }
                }.toByteArray())
            }
            entry(SUMMARY) { zip.write(TripSummary.csv(trips).toByteArray()) }
            trips.forEach { trip ->
                val name = names.getValue(trip.startedAt)
                csvFiles[trip.startedAt]?.let { file -> entry("$name.csv", trip.startedAt) { file.inputStream().use { it.copyTo(zip) } } }
                val points = track(trip.startedAt)
                if (points.size >= 2) {
                    entry("$name.gpx", trip.startedAt) { zip.write(TrackCodec.gpx(points, trip.startedAt, name).toByteArray()) }
                    entry("ion/${trip.startedAt}.track") { zip.write(TrackCodec.encode(points).toByteArray()) }
                }
            }
        }
    }

    /**
     * Restores the rides of an archive that are not already in [existing]: CSVs are copied to [csvFile], tracks
     * handed to [saveTrack]. Returns the new rides; throws [NotABackup] for any other zip (or not a zip at all).
     */
    fun read(input: InputStream, existing: Set<Long>, csvFile: (Long) -> File,
             saveTrack: (Long, List<TrackPoint>) -> Unit): List<TripSummary> {
        val rides = ArrayList<TripSummary>()
        val csvOwner = HashMap<String, Long>()
        ZipInputStream(input.buffered()).use { zip ->
            val first = zip.nextEntry
            if (first?.name != MANIFEST) throw NotABackup()
            val lines = zip.readBytes().decodeToString().lines()
            if (lines.firstOrNull()?.trim() != FORMAT) throw NotABackup()
            lines.drop(1).filter(String::isNotBlank).forEach { line ->
                val summary = TripSummary.decode(line.substringBefore('\t')) ?: return@forEach
                if (summary.startedAt in existing || rides.any { it.startedAt == summary.startedAt }) return@forEach
                rides += summary
                line.substringAfter('\t', "").takeIf(String::isNotBlank)?.let { csvOwner[it] = summary.startedAt }
            }
            val ids = rides.mapTo(HashSet()) { it.startedAt }
            generateSequence { zip.nextEntry }.forEach { e ->
                csvOwner[e.name]?.let { id ->
                    val target = csvFile(id)
                    target.parentFile?.mkdirs()
                    // Written beside the target and renamed, so a broken archive never leaves half a CSV behind.
                    val part = File(target.path + ".part")
                    part.outputStream().use { zip.copyTo(it) }
                    if (!part.renameTo(target)) { part.delete(); throw java.io.IOException("rename") }
                }
                TRACK.matchEntire(e.name)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in ids }?.let { id ->
                    val points = TrackCodec.decode(zip.readBytes().decodeToString())
                    if (points.size >= 2) saveTrack(id, points)
                }
            }
        }
        return rides
    }
}
