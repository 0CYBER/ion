package com.freecyber.ion

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide owner of the BLE link, preferences and stores.
 * The link outlives activities so the foreground service, widgets and the UI share one session. */
class IonApplication : Application() {
    lateinit var graph: IonGraph; private set
    override fun onCreate() {
        super.onCreate()
        IonFormat.locale = java.util.Locale.forLanguageTag(getString(R.string.format_locale))
        graph = IonGraph(this)
        IonNotifications.createChannels(this)
        graph.monitor.start()
    }
}

class IonGraph(val context: Context) {
    /** Main-thread scope that lives as long as the process; GATT callbacks hop onto it. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = AppPrefs(context)
    internal val store = WheelStore(context)
    private val savedState = MutableStateFlow(store.load())
    val saved: StateFlow<WheelDevice?> = savedState.asStateFlow()
    val trips = TripRecorder()
    val history = TripHistory(context)
    val voice = VoiceAnnouncer(context)
    /** Recent telemetry for the live chart; written by the monitor, read by the UI. */
    val samples = SampleBuffer()
    internal val chartState = MutableStateFlow<List<Sample>>(emptyList())
    val chart: StateFlow<List<Sample>> = chartState.asStateFlow()
    val ble = BleController(context, scope) { device ->
        store.saveVerified(device)
        savedState.value = device
    }
    val monitor = WheelMonitor(this)
    fun forget() { monitor.userDisconnect(); store.forget(); savedState.value = null }
}

val Context.ionGraph: IonGraph get() = (applicationContext as IonApplication).graph
