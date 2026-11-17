package com.freecyber.ion

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** Keeps the process (and so the BLE session) alive while the screen is off, and owns the live notification.
 * It never talks to the wheel itself: actions are forwarded to the shared [IonGraph]. */
class WheelService : Service() {
    companion object {
        const val ACTION_START = "com.freecyber.ion.action.START"
        const val ACTION_CONNECT = "com.freecyber.ion.action.CONNECT"
        const val ACTION_DISCONNECT = "com.freecyber.ion.action.DISCONNECT"
        const val ACTION_TOGGLE_LIGHT = "com.freecyber.ion.action.TOGGLE_LIGHT"
        /** Minimum spacing of live-card refreshes; Android throttles faster updates anyway. */
        const val UPDATE_INTERVAL_MS = 1_000L
        /** Grace period before the service stops once the link is idle, so a quick reconnect keeps the card. */
        const val IDLE_STOP_MS = 4_000L
        /** Each renewal extends the lock this far; if the app dies the lock expires on its own. */
        const val WAKE_LOCK_MS = 120_000L
        @Volatile var running = false; private set

        fun ensureRunning(context: Context) {
            if (!running && context.ionGraph.companionWheel.usesForegroundService()) start(context, ACTION_START)
        }
        fun start(context: Context, action: String) {
            try { ContextCompat.startForegroundService(context, Intent(context, WheelService::class.java).setAction(action)) }
            catch (_: Exception) { /* background start not allowed: the UI path will start us next time */ }
        }

        fun dispatch(graph: IonGraph, action: String?) {
            when (action) {
                ACTION_DISCONNECT -> graph.monitor.userDisconnect()
                ACTION_CONNECT -> graph.saved.value?.let { if (graph.ble.hasPermissions()) graph.monitor.userConnect(it) }
                ACTION_TOGGLE_LIGHT -> graph.ble.state.value.takeIf { it.verifiedV12 && it.phase == Phase.CONNECTED }?.telemetry?.let { t ->
                    graph.ble.apply(V12Protocol.Write(V12Protocol.Control.LIGHTS, if (t.lowBeam) 0 else 1, if (t.highBeam) 1 else 0))
                }
            }
        }
    }

    private var observer: Job? = null
    private var idleStop: Job? = null
    private var foregroundId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); running = true }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = ionGraph
        if (!graph.companionWheel.usesForegroundService()) {
            dispatch(graph, intent?.action)
            shutdown()
            return START_NOT_STICKY
        }
        if (!promote(graph)) { stopSelf(); return START_NOT_STICKY }
        dispatch(graph, intent?.action)
        if (observer == null) observer = graph.scope.launch { observe(graph) }
        return START_NOT_STICKY
    }

    private fun notificationId(graph: IonGraph, background: BackgroundConfig) =
        if (graph.companionWheel.showsLiveCard(background)) IonNotifications.LIVE_ID else IonNotifications.GPS_ID

    private fun promote(graph: IonGraph): Boolean {
        val id = notificationId(graph, graph.prefs.background.value)
        val notification = if (id == IonNotifications.LIVE_ID) {
            IonNotifications.live(this, graph.ble.state.value, graph.trips.stats.value,
                graph.monitor.reconnecting.value, graph.monitor.attempt.value)
        } else {
            IonNotifications.gpsRecording(this, graph.track.status.value)
        }
        val connectedDevice = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        // Request the location type while waiting for GPS too. Upgrading can require a resumed Activity.
        val location = Build.VERSION.SDK_INT >= 29 && graph.track.listening
        val type = connectedDevice or (if (location) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        try {
            ServiceCompat.startForeground(this, id, notification, type)
        } catch (_: SecurityException) {
            if (!location || id == IonNotifications.GPS_ID) return false
            try {
                ServiceCompat.startForeground(this, id, notification, connectedDevice)
            } catch (_: Exception) {
                return false
            }
        } catch (_: Exception) {
            return false
        }
        foregroundId?.takeIf { it != id }?.let(NotificationManagerCompat.from(this)::cancel)
        foregroundId = id
        return true
    }

    private fun linkActive(state: WheelState, reconnecting: Boolean): Boolean =
        reconnecting || state.phase !in listOf(Phase.OFFLINE, Phase.ERROR, Phase.SCANNING)

    private suspend fun observe(graph: IonGraph) {
        var lastGpsStatus: GpsStatus? = null
        combine(graph.ble.state, graph.trips.stats, graph.monitor.reconnecting, graph.prefs.background, graph.track.status) { s, trip, retry, _, gps ->
            Snapshot(s, trip, retry, gps)
        }.conflate().collect { snap ->
            val background = graph.prefs.background.value
            if (!graph.companionWheel.usesForegroundService(background)) {
                shutdown()
                return@collect
            }
            if (linkActive(snap.state, snap.reconnecting)) {
                idleStop?.cancel()
                idleStop = null
            } else if (idleStop == null) {
                idleStop = graph.scope.launch {
                    delay(IDLE_STOP_MS)
                    idleStop = null
                    if (!linkActive(graph.ble.state.value, graph.monitor.reconnecting.value)) shutdown()
                }
            }
            val id = notificationId(graph, background)
            if (foregroundId != id) {
                lastGpsStatus = null
                if (!promote(graph)) shutdown()
            } else if (id == IonNotifications.LIVE_ID) {
                refresh(graph, snap)
            } else if (snap.gps != lastGpsStatus && IonNotifications.canPost(this)) {
                try {
                    NotificationManagerCompat.from(this).notify(id, IonNotifications.gpsRecording(this, snap.gps))
                    lastGpsStatus = snap.gps
                } catch (_: SecurityException) { }
            }
            delay(UPDATE_INTERVAL_MS)
        }
    }

    private fun refresh(graph: IonGraph, snap: Snapshot) {
        if (!IonNotifications.canPost(this)) return
        val n = IonNotifications.live(this, snap.state, snap.trip, snap.reconnecting, graph.monitor.attempt.value)
        try { NotificationManagerCompat.from(this).notify(IonNotifications.LIVE_ID, n) } catch (_: SecurityException) { }
    }


    private fun shutdown() {
        observer?.cancel(); observer = null
        idleStop?.cancel(); idleStop = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        observer?.cancel(); observer = null
        idleStop?.cancel(); idleStop = null
        running = false
        super.onDestroy()
    }

    /** The app was swiped from recents: honour the keep-alive choice instead of dying with the task. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!ionGraph.prefs.background.value.keepAlive) { ionGraph.monitor.userDisconnect(); shutdown() }
        super.onTaskRemoved(rootIntent)
    }

    private data class Snapshot(val state: WheelState, val trip: TripStats, val reconnecting: Boolean, val gps: GpsStatus)
}
