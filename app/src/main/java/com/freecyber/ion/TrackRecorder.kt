package com.freecyber.ion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Collects GPS fixes while a ride session runs and hands them over when the trip is archived.
 * Platform [LocationManager] only (no Play Services); silently idle without the location
 * permission or while the rider turns track recording off. One session spans reconnects,
 * like [TripRecorder]; fixes are deduplicated so standing still does not fill the track.
 */
class TrackRecorder(private val context: Context, private val enabled: () -> Boolean) {
    private companion object {
        const val MIN_TIME_MS = 1000L
        const val MIN_DISTANCE_M = 3f
        /** A fix less accurate than this is discarded: too jumpy to draw. */
        const val MAX_ACCURACY_M = 30f
        /** Skip fixes that barely move within the window, so stops do not bloat the track. */
        const val SKIP_METRES = 4.0
        const val SKIP_MS = 3_000L
    }

    private var startedAt = 0L
    private var listener: Listener? = null
    private val points = mutableListOf<TrackPoint>()

    fun granted(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Starts a session; safe to call repeatedly while connected. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (listener != null || !granted() || !enabled()) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        startedAt = SystemClock.elapsedRealtime()
        points.clear()
        val l = Listener()
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            runCatching { if (lm.isProviderEnabled(provider)) lm.requestLocationUpdates(provider, MIN_TIME_MS, MIN_DISTANCE_M, l, Looper.getMainLooper()) }
        }
        listener = l
    }

    /** Stops listening and returns the collected points. */
    fun stop(): List<TrackPoint> {
        val lm = context.getSystemService(LocationManager::class.java)
        listener?.let { runCatching { lm?.removeUpdates(it) } }
        listener = null
        return points.toList().also { points.clear() }
    }

    private inner class Listener : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.accuracy > MAX_ACCURACY_M) return
            val t = SystemClock.elapsedRealtime() - startedAt
            val fix = TrackPoint(t, location.latitude, location.longitude,
                if (location.hasAltitude()) location.altitude else null,
                if (location.hasSpeed()) location.speed.toDouble() else null)
            val last = points.lastOrNull()
            if (last != null && t - last.t < SKIP_MS && Track.metres(last, fix) < SKIP_METRES) return
            points += fix
            // Long rides are decimated in place, never truncated.
            if (points.size >= Track.MAX_POINTS * 2) {
                val thinned = Track.thin(points.toList())
                points.clear(); points.addAll(thinned)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}
