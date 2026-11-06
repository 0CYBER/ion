package com.freecyber.ion

import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backoff for automatic reconnects: 2, 4, 8, 16, 32, then 60 s; bounded attempts so a wheel left
 * switched off does not keep the radio busy forever. */
object ReconnectPolicy {
    const val MAX_ATTEMPTS = 40
    fun delayFor(attempt: Int): Long = (2_000L shl attempt.coerceIn(0, 5)).coerceAtMost(60_000L)
    fun shouldRetry(attempt: Int) = attempt < MAX_ATTEMPTS
}

/** Session coordinator living in the process scope: feeds the trip recorder, evaluates alerts, keeps the
 * foreground service in step with the link and restores a link that dropped without the user asking. */
class WheelMonitor(private val graph: IonGraph) {
    private val engine = AlertEngine()
    private val reconnectingState = MutableStateFlow(false)
    /** True while the monitor is between automatic reconnect attempts or running one. */
    val reconnecting: StateFlow<Boolean> = reconnectingState.asStateFlow()
    private val attemptState = MutableStateFlow(0)
    val attempt: StateFlow<Int> = attemptState.asStateFlow()
    private var reconnectJob: Job? = null
    private var lastPhase = Phase.OFFLINE
    private var lastPackets = 0
    private var userStopped = true
    private var lastWidgetAt = 0L
    private var lastWidgetPhase: Phase? = null

    fun start() {
        graph.scope.launch { graph.ble.state.collect { onState(it) } }
    }

    /** The user asked for a link: a fresh trip unless this continues a dropped session. */
    fun userConnect(device: WheelDevice) {
        cancelReconnect()
        if (lastPhase == Phase.OFFLINE || graph.trips.stats.value.samples == 0) finishTrip()
        userStopped = false
        engine.reset()
        graph.ble.connect(device)
    }

    fun userDisconnect() {
        userStopped = true
        cancelReconnect()
        graph.ble.disconnect()
        finishTrip()
    }

    /** Archives the running session (if it was a real ride) and starts a fresh one. */
    fun finishTrip() {
        TripSummary.from(graph.trips.stats.value, System.currentTimeMillis(), SystemClock.elapsedRealtime())?.let(graph.history::add)
        graph.trips.reset()
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel(); reconnectJob = null
        reconnectingState.value = false; attemptState.value = 0
    }

    private fun onState(s: WheelState) {
        val now = SystemClock.elapsedRealtime()
        val connected = s.verifiedHt && s.phase == Phase.CONNECTED
        if (s.packets > lastPackets && connected) s.telemetry?.let { graph.trips.feed(it, now) }
        lastPackets = s.packets

        if (connected && reconnectingState.value) { reconnectingState.value = false; attemptState.value = 0 }
        if (s.phase == Phase.ERROR && lastPhase != Phase.ERROR) onLinkFailed(lastPhase)

        val errors = s.records[V12Protocol.Read.TELEMETRY]?.frame?.let { f -> V12Protocol.errors(f).filterNot(WheelErrors::isBenign).toSet() } ?: emptySet()
        val input = AlertInput(connected, linkLost = s.phase == Phase.ERROR || reconnectingState.value,
            battery = s.telemetry?.battery, charging = s.telemetry?.charging ?: false,
            mosTemp = s.telemetry?.mosTemp, motorTemp = s.telemetry?.motorTemp, speed = s.telemetry?.speed, errors = errors)
        engine.evaluate(input, graph.prefs.alerts.value, now).forEach { IonNotifications.alert(graph.context, it) }

        val active = s.phase != Phase.OFFLINE && s.phase != Phase.ERROR && s.phase != Phase.SCANNING
        if ((active || reconnectingState.value) && graph.prefs.background.value.keepAlive) WheelService.ensureRunning(graph.context)

        if (s.phase != lastWidgetPhase || now - lastWidgetAt >= IonWidgetUpdater.MIN_INTERVAL_MS) {
            lastWidgetPhase = s.phase; lastWidgetAt = now
            IonWidgetUpdater.push(graph.context, s, graph.trips.stats.value, reconnectingState.value)
        }
        lastPhase = s.phase
    }

    private fun onLinkFailed(previous: Phase) {
        val bg = graph.prefs.background.value
        val device = graph.saved.value
        val wasLive = previous == Phase.CONNECTED
        val retrying = reconnectingState.value
        if (userStopped || device == null || !bg.autoReconnect || !bg.keepAlive) { cancelReconnect(); return }
        // A failure while the user is still setting up a link is shown, not retried behind their back.
        if (!wasLive && !retrying) return
        val attempt = attemptState.value
        if (!ReconnectPolicy.shouldRetry(attempt)) { cancelReconnect(); return }
        reconnectingState.value = true
        reconnectJob?.cancel()
        reconnectJob = graph.scope.launch {
            delay(ReconnectPolicy.delayFor(attempt))
            attemptState.value = attempt + 1
            graph.ble.connect(device)
        }
    }
}
