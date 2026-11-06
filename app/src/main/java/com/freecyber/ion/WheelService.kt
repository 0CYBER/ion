package com.freecyber.ion

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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

        fun ensureRunning(context: Context) { if (!running) start(context, ACTION_START) }
        fun start(context: Context, action: String) {
            try { ContextCompat.startForegroundService(context, Intent(context, WheelService::class.java).setAction(action)) }
            catch (_: Exception) { /* background start not allowed: the UI path will start us next time */ }
        }
    }

    private var observer: Job? = null
    /** The wheel is polled, not streaming: if the CPU suspends between polls the session silently stalls.
     * A non-counted partial wake lock, renewed while the link is active, keeps polling and alerts alive in a pocket. */
    private val wakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ion:wheel-link").apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); running = true }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = ionGraph
        if (!promote(graph)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_DISCONNECT -> graph.monitor.userDisconnect()
            ACTION_CONNECT -> graph.saved.value?.let { if (graph.ble.hasPermissions()) graph.monitor.userConnect(it) }
            ACTION_TOGGLE_LIGHT -> graph.ble.state.value.telemetry?.let { t ->
                graph.ble.apply(V12Protocol.Write(V12Protocol.Control.LIGHTS, if (t.lowBeam) 0 else 1, if (t.highBeam) 1 else 0))
            }
        }
        if (observer == null) observer = graph.scope.launch { observe(graph) }
        return START_NOT_STICKY
    }

    private fun promote(graph: IonGraph): Boolean = try {
        val n = IonNotifications.live(this, graph.ble.state.value, graph.trips.stats.value,
            graph.monitor.reconnecting.value, graph.monitor.attempt.value)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, IonNotifications.LIVE_ID, n, type)
        true
    } catch (_: Exception) { false }

    private suspend fun observe(graph: IonGraph) {
        var idleSince: Long? = null
        combine(graph.ble.state, graph.trips.stats, graph.monitor.reconnecting, graph.prefs.background) { s, trip, retry, bg ->
            Snapshot(s, trip, retry, bg)
        }.conflate().collect { snap ->
            val active = snap.state.phase !in listOf(Phase.OFFLINE, Phase.ERROR, Phase.SCANNING) || snap.reconnecting
            val now = android.os.SystemClock.elapsedRealtime()
            if (!active || !snap.background.keepAlive) {
                idleSince = idleSince ?: now
                if (now - idleSince!! >= IDLE_STOP_MS || !snap.background.keepAlive) { shutdown(); return@collect }
            } else { idleSince = null; holdWakeLock() }
            if (snap.background.liveNotification || !active) refresh(graph, snap)
            delay(UPDATE_INTERVAL_MS)
        }
    }

    private fun refresh(graph: IonGraph, snap: Snapshot) {
        if (!IonNotifications.canPost(this)) return
        val n = IonNotifications.live(this, snap.state, snap.trip, snap.reconnecting, graph.monitor.attempt.value)
        try { NotificationManagerCompat.from(this).notify(IonNotifications.LIVE_ID, n) } catch (_: SecurityException) { }
    }

    private fun holdWakeLock() { runCatching { wakeLock.acquire(WAKE_LOCK_MS) } }
    private fun releaseWakeLock() { runCatching { if (wakeLock.isHeld) wakeLock.release() } }

    private fun shutdown() {
        observer?.cancel(); observer = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        observer?.cancel(); observer = null
        releaseWakeLock()
        running = false
        super.onDestroy()
    }

    /** The app was swiped from recents: honour the keep-alive choice instead of dying with the task. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!ionGraph.prefs.background.value.keepAlive) { ionGraph.monitor.userDisconnect(); shutdown() }
        super.onTaskRemoved(rootIntent)
    }

    private data class Snapshot(val state: WheelState, val trip: TripStats, val reconnecting: Boolean, val background: BackgroundConfig)
}
