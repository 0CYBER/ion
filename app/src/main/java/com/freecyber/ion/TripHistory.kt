package com.freecyber.ion

import androidx.core.content.edit
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Newest-first ride log in its own preferences file; bounded so it never grows without limit.
 * GPS tracks live in the same file under their own keys and are pruned along with the rides. */
class TripHistory(context: Context, private val journal: TripJournal? = null) {
    companion object { const val LIMIT = 200; private const val TRACK_LIMIT = 30; private const val TRACK_PREFIX = "track_" }
    private val prefs = context.getSharedPreferences("ion_trips", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val trips: StateFlow<List<TripSummary>> = state.asStateFlow()

    private fun load(): List<TripSummary> = prefs.getString("trips", null)?.lineSequence()
        ?.mapNotNull { if (it.isBlank()) null else TripSummary.decode(it) }?.toList() ?: emptyList()

    fun add(trip: TripSummary) {
        val all = listOf(trip) + state.value.filterNot { it.startedAt == trip.startedAt }
        all.drop(LIMIT).forEach { deleteTrack(it.startedAt); journal?.delete(it.startedAt) }
        save(all.take(LIMIT))
    }

    /** Checkpoint of the running ride, so a killed process or crash does not lose it. */
    fun checkpoint(trip: TripSummary?) = prefs.edit { if (trip == null) remove("pending") else putString("pending", trip.encode()) }

    /** A checkpoint found at process start belongs to a ride that never finished cleanly: archive it. */
    fun recoverPending() {
        val pending = prefs.getString("pending", null)?.let(TripSummary::decode)
        if (pending != null && state.value.none { it.startedAt == pending.startedAt }) add(pending)
        checkpoint(null)
    }
    fun delete(trip: TripSummary) { deleteTrack(trip.startedAt); journal?.delete(trip.startedAt); save(state.value - trip) }
    fun clear() { state.value.forEach { journal?.delete(it.startedAt) }; save(emptyList()); trackKeys().forEach { deleteTrack(it) } }

    private fun save(list: List<TripSummary>) {
        state.value = list
        prefs.edit { putString("trips", list.joinToString("\n") { it.encode() }) }
    }

    /** Stores the GPS track of a finished ride; rides without a fix pair are skipped. */
    fun saveTrack(startedAt: Long, points: List<TrackPoint>) {
        if (points.size < 2) return
        prefs.edit { putString(TRACK_PREFIX + startedAt, TrackCodec.encode(Track.thin(points))) }
        trackKeys().sorted().dropLast(TRACK_LIMIT).forEach { deleteTrack(it) }
    }

    fun track(startedAt: Long): List<TrackPoint> = if (journal?.hasTrack(startedAt) == true) journal.track(startedAt)
        else prefs.getString(TRACK_PREFIX + startedAt, null)?.let(TrackCodec::decode) ?: emptyList()

    fun hasTelemetry(startedAt: Long): Boolean = journal?.hasTelemetry(startedAt) == true

    private fun deleteTrack(startedAt: Long) = prefs.edit { remove(TRACK_PREFIX + startedAt) }
    private fun trackKeys(): List<Long> = prefs.all.keys
        .filter { it.startsWith(TRACK_PREFIX) }.mapNotNull { it.removePrefix(TRACK_PREFIX).toLongOrNull() }
}
