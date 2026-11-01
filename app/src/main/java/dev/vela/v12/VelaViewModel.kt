package dev.vela.v12

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

object SavedWheelFormat {
    fun validAddress(value: String?) = value != null && Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(value)
}
data class VelaUi(val wheel: WheelState = WheelState(), val saved: WheelDevice? = null, val now: Long = 0,
    val staleSince: Long? = null) {
    val fresh get() = wheel.phase == Phase.CONNECTED && wheel.lastPacketAt?.let { now-it in 0..Timing.FRESH_WINDOW_MS } == true
    val busy get() = wheel.phase in listOf(Phase.CONNECTING, Phase.DISCOVERING, Phase.IDENTIFYING)
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
}
// Sample the monotonic clock for EVERY combined emission, not only the 500ms ticker.
// A BLE packet can be newer than the last ticker value; that is not a disconnect.
internal fun wheelUiSnapshot(wheel: WheelState, saved: WheelDevice?, staleSince: Long? = null, clock: () -> Long): VelaUi =
    VelaUi(wheel,saved,clock(),staleSince)

class VelaViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs=app.getSharedPreferences("vela_preferences",0)
    private val store=WheelStore(app)
    private val saved=MutableStateFlow(store.load())
    val ble=BleController(app,viewModelScope) { device ->
        store.saveVerified(device)
        saved.value=device
    }
    val dark=MutableStateFlow<Boolean?>(if(prefs.contains("dark")) prefs.getBoolean("dark",true) else null)
    fun setDark(value: Boolean) { dark.value=value; prefs.edit().putBoolean("dark",value).apply() }
    private val tick=MutableStateFlow(SystemClock.elapsedRealtime())
    private var staleSince: Long? = null
    val ui: StateFlow<VelaUi> = combine(ble.state,saved,tick) { wheel,device,_ ->
        val now=SystemClock.elapsedRealtime()
        val age=wheel.lastPacketAt?.let { now-it }
        staleSince = if(wheel.phase == Phase.CONNECTED && wheel.verifiedHt && (age == null || age > Timing.FRESH_WINDOW_MS)) staleSince ?: now else null
        wheelUiSnapshot(wheel,device,staleSince) { now }
    }.stateIn(viewModelScope,SharingStarted.Eagerly,VelaUi(saved=saved.value))
    init { viewModelScope.launch { while(true) { tick.value=SystemClock.elapsedRealtime(); delay(Timing.POLL_DELAY_MS) } } }
    fun scan()=ble.scan()
    fun connect(device: WheelDevice)=ble.connect(device)
    fun connectSaved() { saved.value?.let { ble.connect(it) } }
    fun forget() { ble.disconnect(); store.forget(); saved.value=null }
    fun disconnect()=ble.disconnect()
    override fun onCleared() { ble.disconnect() }
}
