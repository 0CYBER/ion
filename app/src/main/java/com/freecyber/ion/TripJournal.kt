package com.freecyber.ion

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ordered app-scoped disk writes, independent of the UI lifecycle. Each append closes its file:
 * a killed process loses at most the work still in the queue, never a whole buffered ride.
 * Telemetry is a few rows/sec; it is never thinned like the live chart or the map. */
class TripJournal(context: Context, scope: CoroutineScope) {
    private val directory = File(context.filesDir, "rides")
    private val queue = Channel<() -> Unit>(Channel.UNLIMITED)
    private val failure = MutableStateFlow(false)
    val failed = failure.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            for (write in queue) try { write() }
            catch (_: IOException) { failure.value = true }
            catch (_: SecurityException) { failure.value = true }
        }
    }

    private fun post(write: () -> Unit) { if (queue.trySend(write).isFailure) failure.value = true }
    private fun file(id: Long, extension: String) = File(directory, "$id.$extension")

    fun telemetry(id: Long, row: String) = post {
        directory.mkdirs()
        val target = file(id, "csv")
        target.appendText((if (!target.exists()) TripCsv.HEADER + "\n" else "") + row + "\n")
    }

    fun point(id: Long, point: TrackPoint) = post {
        directory.mkdirs()
        // Each line is independently decodable, so an interrupted final append cannot corrupt earlier fixes.
        file(id, "track").appendText(TrackCodec.encode(listOf(point)) + "\n")
    }

    fun delete(id: Long) = post {
        file(id, "csv").delete(); file(id, "track").delete()
    }

    suspend fun awaitWrites() {
        val done = CompletableDeferred<Unit>()
        queue.send { done.complete(Unit) }
        done.await()
    }

    fun hasTelemetry(id: Long) = file(id, "csv").isFile
    fun csv(id: Long): File = file(id, "csv")
    fun hasTrack(id: Long) = file(id, "track").isFile
    fun track(id: Long): List<TrackPoint> = file(id, "track").takeIf { it.isFile }
        ?.useLines { lines -> lines.flatMap { TrackCodec.decode(it).asSequence() }.toList() }.orEmpty()
}
