package com.freecyber.ion

import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Session coordinator living in the process scope: feeds the trip recorder, evaluates alerts, keeps the
 * foreground service in step with the link and restores a link that dropped without the user asking. */
class WheelMonitor(private val graph: IonGraph) {
    private val engine = AlertEngine()
    private val stepper = BatteryStepper()
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
    private var lastCheckpointAt = 0L
    private var tracking = false
    private companion object { const val CHECKPOINT_MS = 30_000L }

    fun start() {
        graph.history.recoverPending()
        graph.scope.launch { graph.ble.state.collect { onState(it) } }
    }

    /** The user asked for a link: a fresh trip unless this continues a dropped session. */
    fun userConnect(device: WheelDevice) {
        cancelReconnect()
        if (lastPhase == Phase.OFFLINE || graph.trips.stats.value.samples == 0) finishTrip()
        userStopped = false
        engine.reset(); stepper.reset()
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
        val summary = TripSummary.from(graph.trips.stats.value, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        if (summary != null) {
            graph.history.add(summary)
            graph.history.saveTrack(summary.startedAt, graph.track.stop())
        } else graph.track.stop()
        tracking = false
        graph.history.checkpoint(null); lastCheckpointAt = 0L
        graph.trips.reset()
        graph.samples.clear(); graph.chartState.value = emptyList()
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel(); reconnectJob = null
        reconnectingState.value = false; attemptState.value = 0
    }

    private fun onState(s: WheelState) {
        val now = SystemClock.elapsedRealtime()
        val connected = s.verifiedHt && s.phase == Phase.CONNECTED
        if (s.packets > lastPackets && connected) s.telemetry?.let {
            graph.trips.feed(it, now)
            if (!tracking) { tracking = true; graph.track.start() }
            if (graph.samples.add(it, now)) graph.chartState.value = graph.samples.snapshot()
        }
        lastPackets = s.packets
        if (connected && now - lastCheckpointAt >= CHECKPOINT_MS) {
            lastCheckpointAt = now
            TripSummary.from(graph.trips.stats.value, System.currentTimeMillis(), now)?.let(graph.history::checkpoint)
        }

        if (connected && reconnectingState.value) { reconnectingState.value = false; attemptState.value = 0 }
        if (s.phase == Phase.ERROR && lastPhase != Phase.ERROR) onLinkFailed(lastPhase)

        val errors = s.records[V12Protocol.Read.TELEMETRY]?.frame?.let { f -> V12Protocol.errors(f).filterNot(WheelErrors::isBenign).toSet() } ?: emptySet()
        val input = AlertInput(connected, linkLost = s.phase == Phase.ERROR || reconnectingState.value,
            battery = s.telemetry?.battery, charging = s.telemetry?.charging ?: false,
            mosTemp = s.telemetry?.mosTemp, motorTemp = s.telemetry?.motorTemp, speed = s.telemetry?.speed, errors = errors)
        val config = graph.prefs.alerts.value
        engine.evaluate(input, config, now).forEach {
            IonNotifications.alert(graph.context, it)
            if (config.voice) graph.voice.speak(VoiceAnnouncer.phrase(graph.context, it))
        }
        if (connected && config.voiceBattery) stepper.next(s.telemetry?.battery, s.telemetry?.charging ?: false)
            ?.let { graph.voice.speak(graph.context.resources.getQuantityString(R.plurals.voice_battery_step, it, it)) }
        if (!config.voice && !config.voiceBattery) graph.voice.shutdown()

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
        when (val d = ReconnectPolicy.decide(userStopped, device != null, bg.autoReconnect, bg.keepAlive,
            wasLive = previous == Phase.CONNECTED, retrying = reconnectingState.value, attempt = attemptState.value)) {
            ReconnectPolicy.Decision.Cancel -> cancelReconnect()
            ReconnectPolicy.Decision.Show -> Unit
            is ReconnectPolicy.Decision.Retry -> {
                reconnectingState.value = true
                reconnectJob?.cancel()
                reconnectJob = graph.scope.launch {
                    delay(d.delayMs)
                    attemptState.value = d.attempt + 1
                    device?.let(graph.ble::connect)
                }
            }
        }
    }
}
