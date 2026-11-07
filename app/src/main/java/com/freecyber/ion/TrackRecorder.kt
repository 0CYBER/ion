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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpsStatus { OFF, PRECISE_REQUIRED, DISABLED, WAITING, RECORDING, ERROR }

/** Subscriptions can restart without clearing the ride or changing the telemetry/GPS time origin. */
class TrackRecorder(
    private val context: Context,
    private val enabled: () -> Boolean,
    private val onPoint: (TrackPoint) -> Unit = {},
) {
    private var startedAt: Long? = null
    private var listener: Listener? = null
    private var latest: TrackPoint? = null
    private val countState = MutableStateFlow(0)
    val count = countState.asStateFlow()
    private val statusState = MutableStateFlow(GpsStatus.OFF)
    val status = statusState.asStateFlow()
    val listening: Boolean get() = listener != null
    val lastPoint: TrackPoint? get() = latest

    fun granted(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(sessionStart: Long) {
        startedAt = startedAt ?: sessionStart
        if (!enabled() || !granted()) {
            pause()
            statusState.value = if (!enabled()) GpsStatus.OFF else GpsStatus.PRECISE_REQUIRED
            return
        }
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        if (listener != null) {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) statusState.value = GpsStatus.DISABLED
            else if (latest == null || SystemClock.elapsedRealtime() - sessionStart - latest!!.t > 15_000) statusState.value = GpsStatus.WAITING
            return
        }
        val l = Listener()
        // Register even while the provider is disabled: Android resumes this request when GPS is enabled.
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, l, Looper.getMainLooper())
            listener = l
            statusState.value = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) GpsStatus.WAITING else GpsStatus.DISABLED
        } catch (_: SecurityException) { statusState.value = GpsStatus.PRECISE_REQUIRED }
        catch (_: IllegalArgumentException) { statusState.value = GpsStatus.ERROR }
    }

    fun pause() {
        val lm = context.getSystemService(LocationManager::class.java)
        listener?.let { lm?.removeUpdates(it) }
        listener = null
        statusState.value = GpsStatus.OFF
    }

    fun stop() {
        pause()
        startedAt = null; latest = null; countState.value = 0
    }

    private inner class Listener : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (listener !== this || !enabled() || !granted()) return
            val start = startedAt ?: return
            val fixElapsed = location.elapsedRealtimeNanos / 1_000_000
            val now = SystemClock.elapsedRealtime()
            if (!location.hasAccuracy() || !GpsFix.valid(location.latitude, location.longitude,
                    location.accuracy.toDouble(), fixElapsed, now, start)) return
            val fix = TrackPoint(fixElapsed - start, location.latitude, location.longitude,
                location.altitude.takeIf { location.hasAltitude() && it.isFinite() },
                location.speed.toDouble().takeIf { location.hasSpeed() && it.isFinite() && it >= 0 })
            if (!GpsFix.follows(latest, fix)) return
            latest = fix
            countState.value++
            statusState.value = GpsStatus.RECORDING
            onPoint(fix)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { statusState.value = GpsStatus.WAITING }
        override fun onProviderDisabled(provider: String) { statusState.value = GpsStatus.DISABLED }
    }
}
