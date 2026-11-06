package com.freecyber.ion

import androidx.core.content.edit
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Newest-first ride log in its own preferences file; bounded so it never grows without limit. */
class TripHistory(context: Context) {
    companion object { const val LIMIT = 200 }
    private val prefs = context.getSharedPreferences("ion_trips", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val trips: StateFlow<List<TripSummary>> = state.asStateFlow()

    private fun load(): List<TripSummary> = prefs.getString("trips", null)?.lineSequence()
        ?.mapNotNull { if (it.isBlank()) null else TripSummary.decode(it) }?.toList() ?: emptyList()

    fun add(trip: TripSummary) = save((listOf(trip) + state.value).take(LIMIT))

    /** Checkpoint of the running ride, so a killed process or crash does not lose it. */
    fun checkpoint(trip: TripSummary?) = prefs.edit { if (trip == null) remove("pending") else putString("pending", trip.encode()) }

    /** A checkpoint found at process start belongs to a ride that never finished cleanly: archive it. */
    fun recoverPending() {
        val pending = prefs.getString("pending", null)?.let(TripSummary::decode)
        checkpoint(null)
        if (pending != null && state.value.none { it.startedAt == pending.startedAt }) add(pending)
    }
    fun delete(trip: TripSummary) = save(state.value - trip)
    fun clear() = save(emptyList())

    private fun save(list: List<TripSummary>) {
        state.value = list
        prefs.edit { putString("trips", list.joinToString("\n") { it.encode() }) }
    }
}
