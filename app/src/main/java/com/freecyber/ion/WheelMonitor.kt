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
    private var userStopped = true
    private var lastWidgetAt = 0L
    private var lastWidgetPhase: Phase? = null
    private var lastCheckpointAt = 0L
    private var sessionStart: Long? = null
    private var sessionWall: Long? = null
    private companion object { const val CHECKPOINT_MS = 5_000L; const val VIBRATE_AFTER_MS = 700L }
    /** A link is being made or held: the foreground service must run so Android does not kill it in the pocket. */
    private val Phase.holdsLink get() = this != Phase.OFFLINE && this != Phase.ERROR && this != Phase.SCANNING

    fun start() {
        graph.history.recoverPending()
        graph.ble.telemetrySink = ::onTelemetry
        graph.scope.launch { graph.ble.state.collect { onState(it) } }
        graph.scope.launch { graph.prefs.background.collect { refreshRecording() } }
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
        val summary = summary(SystemClock.elapsedRealtime())
        if (summary != null) {
            graph.history.add(summary)
        } else sessionWall?.let(graph.journal::delete)
        graph.track.stop()
        sessionStart = null; sessionWall = null
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
        if (connected && now - lastCheckpointAt >= CHECKPOINT_MS) {
            lastCheckpointAt = now
            summary(now)?.let(graph.history::checkpoint)
        }

        if (connected && reconnectingState.value) { reconnectingState.value = false; attemptState.value = 0 }
        if (s.phase == Phase.ERROR && lastPhase != Phase.ERROR) onLinkFailed(lastPhase)

        val errors = s.records[V12Protocol.Read.TELEMETRY]?.frame?.let { f -> V12Protocol.errors(f).filterNot(WheelErrors::isBenign).toSet() } ?: emptySet()
        val input = AlertInput(connected, linkLost = s.phase == Phase.ERROR || reconnectingState.value,
            battery = s.telemetry?.battery, charging = s.telemetry?.charging ?: false,
            mosTemp = s.telemetry?.mosTemp, motorTemp = s.telemetry?.motorTemp, speed = s.telemetry?.speed, errors = errors)
        val config = graph.prefs.alerts.value
        val fired = engine.evaluate(input, config, now)
        fired.forEach { IonNotifications.alert(graph.context, it) }
        if (config.voice) AlertHaptics.voiceOrder(fired).forEach { (alert, interrupt) ->
            graph.voice.speak(VoiceAnnouncer.phrase(graph.context, alert), interrupt)
        }
        // One rhythm per evaluation (the most severe), played after the channel's own buzz so it is not cut off.
        if (config.vibrate) AlertHaptics.strongest(fired)?.let { alert ->
            graph.scope.launch { delay(VIBRATE_AFTER_MS); graph.vibrator.play(alert.kind) }
        }
        if (connected && config.voiceBattery) stepper.next(s.telemetry?.battery, s.telemetry?.charging ?: false)
            ?.let { graph.voice.speak(graph.context.resources.getQuantityString(R.plurals.voice_battery_step, it, it)) }
        if (!config.voice && !config.voiceBattery) graph.voice.shutdown()

        if ((s.phase.holdsLink || reconnectingState.value) && graph.prefs.background.value.keepAlive) WheelService.ensureRunning(graph.context)

        if (s.phase != lastWidgetPhase || now - lastWidgetAt >= IonWidgetUpdater.MIN_INTERVAL_MS) {
            lastWidgetPhase = s.phase; lastWidgetAt = now
            IonWidgetUpdater.push(graph.context, s, graph.trips.stats.value, reconnectingState.value)
        }
        lastPhase = s.phase
    }

    private fun summary(now: Long): TripSummary? {
        val wall = sessionWall ?: return null
        return TripSummary.from(graph.trips.stats.value, System.currentTimeMillis(), now)?.copy(startedAt = wall)
    }

    /** Called directly for each decoded telemetry packet: StateFlow can conflate packets in a burst. */
    private fun onTelemetry(t: V12Protocol.Telemetry, now: Long) {
        if (sessionStart == null) { sessionStart = now; sessionWall = System.currentTimeMillis() }
        graph.trips.feed(t, now)
        refreshRecording(false)
        graph.journal.telemetry(sessionWall!!, TripCsv.row(sessionWall!!, now - sessionStart!!,
            t, graph.trips.stats.value.distanceKm, graph.track.lastPoint))
        if (graph.samples.add(t, now)) graph.chartState.value = graph.samples.snapshot()
    }

    fun recordPoint(point: TrackPoint) { sessionWall?.let { graph.journal.point(it, point) } }

    /** Also called from the resumed Activity after a permission change, before it goes into the pocket. */
    fun refreshRecording(refreshService: Boolean = true) {
        val s = graph.ble.state.value
        if (s.phase.holdsLink && graph.prefs.background.value.keepAlive)
            if (refreshService) WheelService.start(graph.context, WheelService.ACTION_START)
            else WheelService.ensureRunning(graph.context)
        sessionStart?.let(graph.track::start)
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
