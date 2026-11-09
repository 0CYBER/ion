package com.freecyber.ion

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

object SavedWheelFormat {
    fun validAddress(value: String?) = value != null && Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(value)
}
data class VelaUi(val wheel: WheelState = WheelState(), val saved: WheelDevice? = null, val now: Long = 0,
    val staleSince: Long? = null, val reconnecting: Boolean = false, val trip: TripStats = TripStats(),
    val chart: List<Sample> = emptyList(), val gpsPoints: Int = 0,
    val gpsStatus: GpsStatus = GpsStatus.OFF, val recordingFailed: Boolean = false) {
    val fresh get() = wheel.phase == Phase.CONNECTED && wheel.lastPacketAt?.let { now-it in 0..Timing.FRESH_WINDOW_MS } == true
    val busy get() = when(wheel.phase) {Phase.CONNECTING, Phase.DISCOVERING, Phase.IDENTIFYING -> true; else -> false}
    val connected get() = wheel.verifiedHt && wheel.phase == Phase.CONNECTED
    /** Staleness only surfaces after a sustained gap, so brief packet lag never flashes a banner. */
    val staleConfirmed get() = connected && staleSince != null && now-staleSince >= Timing.STALE_BANNER_AFTER_MS
    val safeWrite get() = V12Protocol.canWriteSafe(wheel.verifiedHt, connected,
        wheel.telemetry?.firmwareUpdate ?: false, wheel.writeInFlight)
    val stoppedWrite get() = wheel.stationaryPackets >= 3 && V12Protocol.canWrite(V12Protocol.Tier.STATIONARY,
        wheel.verifiedHt, connected, wheel.lastPacketAt?.let { now-it }, wheel.telemetry?.speed,
        wheel.telemetry?.charging ?: false, wheel.telemetry?.firmwareUpdate ?: false, wheel.writeInFlight)
    fun canTier(tier: V12Protocol.Tier) = if(tier == V12Protocol.Tier.SAFE) safeWrite else stoppedWrite
    fun canWrite(command: V12Protocol.Write) = canTier(command.kind.tier)
    val settingsFresh get() = connected && wheel.settingsAt?.let { now-it in 0..15_000 } == true
    val odometerKm get() = wheel.records[V12Protocol.Read.STATISTICS]?.frame?.let(V12Protocol::statistics)?.totalKm?.takeIf { it > 0 }
}
// Sample the monotonic clock for EVERY combined emission, not only the 500ms ticker.
// A BLE packet can be newer than the last ticker value; that is not a disconnect.
internal fun wheelUiSnapshot(wheel: WheelState, saved: WheelDevice?, staleSince: Long? = null, clock: () -> Long): VelaUi =
    VelaUi(wheel,saved,clock(),staleSince)

/** A clock tick only reaches the UI when it changes a freshness/permission boundary.
 * Packet age has its own leaf clock in diagnostics; raw `now` is not presentation identity. */
internal fun VelaUi.samePresentationAs(other: VelaUi): Boolean =
    wheel == other.wheel && saved == other.saved && reconnecting == other.reconnecting &&
        trip == other.trip && chart == other.chart && gpsPoints == other.gpsPoints && gpsStatus == other.gpsStatus && recordingFailed == other.recordingFailed &&
        fresh == other.fresh && settingsFresh == other.settingsFresh &&
        stoppedWrite == other.stoppedWrite && staleConfirmed == other.staleConfirmed

/** Thin presentation adapter over the process-wide [IonGraph]; the BLE session itself outlives this ViewModel. */
class VelaViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.ionGraph
    val ble get() = graph.ble
    val prefs get() = graph.prefs
    val dark: StateFlow<Boolean?> get() = graph.prefs.dark
    fun setDark(value: Boolean?) = graph.prefs.setDark(value)
    private val tick=flow {
        while(true) {emit(SystemClock.elapsedRealtime());delay(Timing.POLL_DELAY_MS)}
    }
    private var staleSince: Long? = null
    val ui: StateFlow<VelaUi> = combine(graph.ble.state,graph.saved,tick,graph.monitor.reconnecting,graph.trips.stats) { wheel,device,_,retry,trip ->
        val now=SystemClock.elapsedRealtime()
        val age=wheel.lastPacketAt?.let { now-it }
        staleSince = if(wheel.phase == Phase.CONNECTED && wheel.verifiedHt && (age == null || age > Timing.FRESH_WINDOW_MS)) staleSince ?: now else null
        wheelUiSnapshot(wheel,device,staleSince) { now }.copy(reconnecting=retry,trip=trip)
    }.combine(graph.chart) { ui,chart -> ui.copy(chart=chart) }
        .combine(graph.track.count) { ui,gps -> ui.copy(gpsPoints=gps) }
        .combine(graph.track.status) { ui,status -> ui.copy(gpsStatus=status) }
        .combine(graph.journal.failed) { ui,failed -> ui.copy(recordingFailed=failed) }
        .distinctUntilChanged { previous,next -> previous.samePresentationAs(next) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),VelaUi(saved=graph.saved.value))
    fun scan()=graph.ble.scan()
    fun connect(device: WheelDevice)=graph.monitor.userConnect(device)
    fun forget()=graph.forget()
    fun disconnect()=graph.monitor.userDisconnect()
    fun resetTrip()=graph.monitor.finishTrip()
    val history get() = graph.history
    val journal get() = graph.journal
    fun refreshRecording() = graph.monitor.refreshRecording()
    /** Cold start: pick the saved wheel back up if the rider wants that and nothing else is running. */
    fun autoConnect() {
        val saved=graph.saved.value ?: return
        if(!graph.prefs.background.value.connectOnLaunch || !graph.ble.hasPermissions()) return
        if(graph.ble.state.value.phase == Phase.OFFLINE && !graph.monitor.reconnecting.value) graph.monitor.userConnect(saved)
    }
    /** Called when the activity leaves the screen: the link stays only if background mode is on. */
    fun onBackground() {
        if(!graph.prefs.background.value.keepAlive && graph.ble.state.value.phase !in listOf(Phase.OFFLINE, Phase.ERROR)) graph.monitor.userDisconnect()
        else if(graph.ble.state.value.phase == Phase.SCANNING) graph.ble.stopScan()
    }
}
