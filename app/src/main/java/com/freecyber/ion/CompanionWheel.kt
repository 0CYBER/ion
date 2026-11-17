package com.freecyber.ion

import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.companion.ObservingDevicePresenceRequest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class CompanionWheel(private val graph: IonGraph) {
    private val manager = graph.context.getSystemService(CompanionDeviceManager::class.java)
    val supported = Build.VERSION.SDK_INT >= 31 && manager != null &&
        graph.context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    private var observed: Association? = null
    private val readyState = MutableStateFlow(false)
    val ready = readyState.asStateFlow()

    fun start() {
        if (!supported) return
        graph.scope.launch {
            combine(graph.saved, graph.prefs.background) { device, config -> device?.address to config.keepAlive }
                .distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        if (Build.VERSION.SDK_INT < 31 || !supported) return
        val service = manager ?: return
        val address = graph.saved.value?.address?.uppercase()
        val config = graph.prefs.background.value
        val associations = runCatching { associations(service) }.getOrElse {
            observed = null
            readyState.value = false
            return
        }
        val next = associations.firstOrNull { it.address == address && config.keepAlive }
        if (observed != next) {
            observed?.let { runCatching { observe(service, it, false) } }
            observed = next?.takeIf { runCatching { observe(service, it, true) }.isSuccess }
        }
        readyState.value = observed != null
    }

    fun pruneAssociations(keepAddress: String?) {
        if (!supported) return
        val service = manager ?: return
        runCatching {
            associations(service).filter { !it.address.equals(keepAddress, ignoreCase = true) }
                .forEach { remove(service, it) }
        }
        refresh()
    }

    fun covers(address: String?): Boolean = observed?.address == address?.uppercase() && ready.value

    fun usesForegroundService(config: BackgroundConfig = graph.prefs.background.value): Boolean =
        config.keepAlive && (showsLiveCard(config) || graph.track.listening)

    fun showsLiveCard(config: BackgroundConfig): Boolean =
        config.liveNotification && IonNotifications.canPostLive(graph.context)

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    fun associate(address: String, launch: (IntentSender) -> Unit, failed: () -> Unit) {
        val service = manager ?: return failed()
        if (!supported) return failed()
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(intentSender: IntentSender) = launch(intentSender)
            override fun onAssociationPending(intentSender: IntentSender) = launch(intentSender)
            override fun onFailure(error: CharSequence?) = failed()
        }
        runCatching {
            val filter = BluetoothLeDeviceFilter.Builder()
                .setScanFilter(ScanFilter.Builder().setDeviceAddress(address.uppercase()).build()).build()
            val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build()
            if (Build.VERSION.SDK_INT >= 33) service.associate(request, graph.context.mainExecutor, callback)
            else service.associate(request, callback, Handler(Looper.getMainLooper()))
        }.onFailure { failed() }
    }

    fun deviceAppeared(address: String? = null, id: Int? = null) {
        val association = observed ?: return
        if ((id != null && association.id == id) || (address != null && association.address == address)) {
            graph.monitor.resumeCompanionLink(association.address)
        }
    }

    @Suppress("DEPRECATION")
    private fun associations(service: CompanionDeviceManager): List<Association> =
        if (Build.VERSION.SDK_INT >= 33) service.myAssociations.mapNotNull { info ->
            info.deviceMacAddress?.toString()?.uppercase()?.let { Association(it, info.id) }
        } else service.associations.map { Association(it.uppercase()) }

    @Suppress("DEPRECATION")
    private fun remove(service: CompanionDeviceManager, association: Association) {
        if (Build.VERSION.SDK_INT >= 33) service.disassociate(checkNotNull(association.id))
        else service.disassociate(association.address)
    }

    @RequiresApi(31)
    @Suppress("DEPRECATION")
    private fun observe(service: CompanionDeviceManager, association: Association, start: Boolean) {
        if (Build.VERSION.SDK_INT >= 36) {
            val request = ObservingDevicePresenceRequest.Builder().setAssociationId(checkNotNull(association.id)).build()
            if (start) service.startObservingDevicePresence(request) else service.stopObservingDevicePresence(request)
        } else {
            if (start) service.startObservingDevicePresence(association.address)
            else service.stopObservingDevicePresence(association.address)
        }
    }

    private data class Association(val address: String, val id: Int? = null)
}

@RequiresApi(31)
@Suppress("OVERRIDE_DEPRECATION")
class WheelCompanionService : CompanionDeviceService() {
    override fun onDeviceAppeared(address: String) = ionGraph.companionWheel.deviceAppeared(address.uppercase())

    @RequiresApi(33)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) =
        ionGraph.companionWheel.deviceAppeared(id = associationInfo.id)

    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED, DevicePresenceEvent.EVENT_BT_CONNECTED ->
                ionGraph.companionWheel.deviceAppeared(id = event.associationId)
        }
    }
}
