package com.freecyber.ion

import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private var requestedAddress: String? = null
    internal val linkAddress get() = requestedAddress
    private var lastWidgetAt = 0L
    private var lastWidgetPhase: Phase? = null
    private var lastCheckpointAt = 0L
    private var sessionStart: Long? = null
    private var sessionWall: Long? = null
    private val companionWakeLock by lazy {
        graph.context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ion:companion-link")
            ?.apply { setReferenceCounted(false) }
    }
    private companion object { const val CHECKPOINT_MS = 5_000L; const val VIBRATE_AFTER_MS = 700L }
    /** A link is being made or held: the foreground service must run so Android does not kill it in the pocket. */
    private val Phase.holdsLink get() = this != Phase.OFFLINE && this != Phase.ERROR && this != Phase.SCANNING

    fun start() {
        graph.history.recoverPending()
        graph.ble.telemetrySink = ::onTelemetry
        graph.scope.launch { graph.ble.state.collect { onState(it) } }
        graph.scope.launch {
            graph.prefs.background.collect { config ->
                if (!config.autoReconnect || !config.keepAlive) cancelReconnect()
                refreshRecording()
            }
        }
        graph.scope.launch { graph.companionWheel.ready.collect { refreshRecording() } }
        graph.scope.launch {
            combine(graph.ble.state, reconnecting, graph.companionWheel.ready, graph.prefs.background) { state, retry, ready, config ->
                ready && config.keepAlive && graph.companionWheel.covers(requestedAddress) && (state.phase.holdsLink || retry)
            }.distinctUntilChanged().collectLatest { hold ->
                if (hold) {
                    val lock = companionWakeLock ?: return@collectLatest
                    try {
                        while (true) {
                            lock.acquire(WheelService.WAKE_LOCK_MS)
                            delay(WheelService.WAKE_LOCK_MS / 2)
                        }
                    } finally {
                        if (lock.isHeld) lock.release()
                    }
                }
            }
        }
    }

    /** The user asked for a link: a fresh trip unless this continues a dropped session. */
    fun userConnect(device: WheelDevice) {
        cancelReconnect()
        if (lastPhase == Phase.OFFLINE || graph.trips.stats.value.samples == 0) finishTrip()
        userStopped = false
        graph.prefs.companionReconnectArmed = device.address == graph.saved.value?.address
        requestedAddress = device.address
        engine.reset(); stepper.reset()
        graph.ble.connect(device, fromSaved = device == graph.saved.value)
    }

    internal fun onWheelVerified(address: String) {
        requestedAddress = address
        if (!userStopped) graph.prefs.companionReconnectArmed = true
    }

    fun userDisconnect() {
        discardUnverifiedAssociation()
        userStopped = true
        graph.prefs.companionReconnectArmed = false
        requestedAddress = null
        cancelReconnect()
        graph.ble.disconnect()
        finishTrip()
    }

    fun userScan() {
        discardUnverifiedAssociation()
        userStopped = true
        graph.prefs.companionReconnectArmed = false
        requestedAddress = null
        cancelReconnect()
        graph.ble.scan()
    }

    fun resumeSavedLink() {
        if (graph.ble.state.value.phase == Phase.ERROR && reconnectJob?.isActive != true &&
            graph.ble.hasPermissions()) onLinkFailed()
    }

    fun resumeCompanionLink(address: String) {
        val config = graph.prefs.background.value
        if (!config.keepAlive || !config.autoReconnect || !graph.prefs.companionReconnectArmed ||
            reconnectJob?.isActive == true || !graph.ble.hasPermissions()) return
        val device = graph.saved.value?.takeIf { it.address.equals(address, ignoreCase = true) } ?: return
        when (graph.ble.state.value.phase) {
            Phase.OFFLINE -> userConnect(device)
            Phase.ERROR -> resumeSavedLink()
            else -> Unit
        }
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
        val connected = s.verifiedV12 && s.phase == Phase.CONNECTED
        if (connected && now - lastCheckpointAt >= CHECKPOINT_MS) {
            lastCheckpointAt = now
            summary(now)?.let(graph.history::checkpoint)
        }

        if (s.phase == Phase.ERROR && lastPhase != Phase.ERROR && reconnectJob?.isActive != true) onLinkFailed()

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

        if ((s.phase.holdsLink || reconnectingState.value) && graph.companionWheel.usesForegroundService())
            WheelService.ensureRunning(graph.context)

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
        if ((s.phase.holdsLink || reconnectingState.value) && graph.companionWheel.usesForegroundService())
            if (refreshService) WheelService.start(graph.context, WheelService.ACTION_START)
            else WheelService.ensureRunning(graph.context)
        sessionStart?.let(graph.track::start)
    }

    private fun onLinkFailed() {
        val bg = graph.prefs.background.value
        val device = graph.saved.value?.takeIf { it.address == requestedAddress } ?: run {
            discardUnverifiedAssociation()
            return
        }
        val first = ReconnectPolicy.decide(userStopped, true, bg.autoReconnect, bg.keepAlive,
            wasLive = lastPhase == Phase.CONNECTED, retrying = false, attempt = 0, restoringSavedWheel = true)
        if (first !is ReconnectPolicy.Decision.Retry) return

        reconnectingState.value = true
        reconnectJob = graph.scope.launch {
            try {
                for (attempt in first.attempt until ReconnectPolicy.MAX_ATTEMPTS) {
                    delay(if (attempt == first.attempt) first.delayMs else ReconnectPolicy.delayFor(attempt))
                    attemptState.value = attempt + 1
                    graph.ble.connect(device, fromSaved = true)
                    val result = graph.ble.state.first { it.phase == Phase.CONNECTED || it.phase == Phase.ERROR }
                    if (result.phase == Phase.CONNECTED) break
                }
            } finally {
                reconnectingState.value = false
                attemptState.value = 0
            }
        }
    }

    private fun discardUnverifiedAssociation() {
        val saved = graph.saved.value?.address
        if (requestedAddress != null && requestedAddress != saved) graph.companionWheel.pruneAssociations(saved)
    }
}
