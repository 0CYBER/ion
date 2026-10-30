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
data class VelaUi(val wheel: WheelState = WheelState(), val saved: WheelDevice? = null, val now: Long = 0) {
    val fresh get() = wheel.phase == Phase.CONNECTED && wheel.lastPacketAt?.let { now-it in 0..2500 } == true
    val busy get() = wheel.phase in listOf(Phase.CONNECTING, Phase.DISCOVERING, Phase.IDENTIFYING)
    val connected get() = wheel.verifiedHt && wheel.phase == Phase.CONNECTED
    val canWrite get() = wheel.stationaryPackets >= 3 && V12Protocol.canWrite(wheel.verifiedHt,connected,
        wheel.lastPacketAt?.let { now-it },wheel.telemetry?.speed,wheel.telemetry?.charging ?: true,
        wheel.telemetry?.firmwareUpdate ?: true,wheel.workPending)
    val settingsFresh get() = connected && wheel.settingsAt?.let { now-it in 0..15_000 } == true
}
// Sample the monotonic clock for EVERY combined emission, not only the 500ms ticker.
// A BLE packet can be newer than the last ticker value; that is not a disconnect.
internal fun wheelUiSnapshot(wheel: WheelState, saved: WheelDevice?, clock: () -> Long): VelaUi =
    VelaUi(wheel,saved,clock())

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
    val ui: StateFlow<VelaUi> = combine(ble.state,saved,tick) { wheel,device,_ -> wheelUiSnapshot(wheel,device,SystemClock::elapsedRealtime) }
        .stateIn(viewModelScope,SharingStarted.Eagerly,VelaUi(saved=saved.value))
    init { viewModelScope.launch { while(true) { tick.value=SystemClock.elapsedRealtime(); delay(500) } } }
    fun scan()=ble.scan()
    fun connect(device: WheelDevice)=ble.connect(device)
    fun connectSaved() { saved.value?.let { ble.connect(it) } }
    fun forget() { ble.disconnect(); store.forget(); saved.value=null }
    fun disconnect()=ble.disconnect()
    override fun onCleared() { ble.disconnect() }
}
