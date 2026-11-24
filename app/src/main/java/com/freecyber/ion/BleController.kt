package com.freecyber.ion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.util.Log
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class Phase { OFFLINE, SCANNING, CONNECTING, DISCOVERING, IDENTIFYING, CONNECTED, ERROR }
/** One telemetry timeout must not exceed the UI freshness window: ask+delay stays under FRESH_WINDOW_MS. */
internal object Timing {
    const val TELEMETRY_ASK_MS = 1200L
    const val POLL_DELAY_MS = 500L
    const val STATISTICS_INTERVAL_MS = 5_000L
    const val FRESH_WINDOW_MS = 2500L
    /** Sustained staleness before any banner may appear: brief packet gaps never flash UI. */
    const val STALE_BANNER_AFTER_MS = 2500L
}
data class WheelDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val advertisedName: String? = null,
    val serial: String? = null
) {
    internal fun recoveryName(): String? = advertisedName?.takeIf { it.isNotBlank() && !serial.isNullOrBlank() }

    internal fun acceptsRecoveredAddress(address: String, reportedSerial: String?): Boolean =
        this.address.equals(address, ignoreCase = true) || (serial != null && reportedSerial == serial)
}
data class PacketRecord(val read: V12Protocol.Read, val frame: V12Protocol.Frame? = null, val at: Long? = null, val status: String = "Ещё не запрошено")
/** A personal light effect upload: [progress] 0..1 over the file chunks, [error] set when a step failed. */
data class LightTransfer(val slot: Int, val id: Long?, val progress: Float, val done: Boolean = false, val error: String? = null)

data class WheelState(
    val phase: Phase = Phase.OFFLINE,
    val message: String = "", // set from resources by BleController
    val devices: List<WheelDevice> = emptyList(),
    val deviceName: String? = null,
    val model: V12Protocol.Model? = null,
    val telemetry: V12Protocol.Telemetry? = null,
    val settings: V12Protocol.Settings? = null,
    val lastPacketAt: Long? = null,
    val packets: Int = 0,
    val settingsAt: Long? = null,
    val records: Map<V12Protocol.Read, PacketRecord> = emptyMap(),
    val workPending: Boolean = false,
    /** True only while an actual write is being processed; background reads must not lock the controls. */
    val writeInFlight: Boolean = false,
    val writeMessage: String? = null,
    val stationaryPackets: Int = 0,
    /** Consecutive telemetry packets above the stillness speed threshold; resets the counter only from 2 in a row. */
    val movingStreak: Int = 0,
    /** Consecutive telemetry packets carrying the firmware-update bit; the guard acts from 2 in a row. */
    val firmwareUpdateStreak: Int = 0,
    /** Personal light effect upload in progress or just finished. */
    val lightTransfer: LightTransfer? = null,
    /** Effect files on the wheel from the last listing; null until listed. */
    val storedLights: List<LightEffects.Stored>? = null,
) {
    val verifiedV12 get() = model?.isSupported == true
}
/** Foreground-only BLE. Only serialized, validated reads/accessory controls. No internet access.
 * All GATT setup operations run sequentially; notifications are incrementally decoded.
 */
@SuppressLint("MissingPermission")
class BleController(private val context: Context, private val scope: CoroutineScope, private val onConnected: (WheelDevice) -> Unit = {}) {
    /** Lossless main-thread session input; presentation StateFlow may conflate packet bursts. */
    internal var telemetrySink: ((V12Protocol.Telemetry, Long) -> Unit)? = null
    /** UI-facing status texts come from resources so they follow the app language. */
    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)
    private val mutable = MutableStateFlow(WheelState(message = context.getString(R.string.ble_offline)))
    val state = mutable.asStateFlow()
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var awaitingWrite = false
    private var session: Job? = null
    private var scanTimeout: Job? = null
    private var scanning = false
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    private var decoder = V12Protocol.Decoder()
    private data class Event(val kind: String, val status: Int, val value: Int = 0)
    private val events = Channel<Event>(Channel.BUFFERED)
    private val responses = Channel<V12Protocol.Frame>(Channel.BUFFERED)
    private val securityResponses = Channel<V12Protocol.Frame>(Channel.BUFFERED)
    @Volatile private var awaitingSecurity = false
    @Volatile private var negotiatedMtu = 23
    private sealed interface Work {
        data object ReadAll : Work
        data class ReadOne(val read: V12Protocol.Read) : Work
        data class Write(val command: V12Protocol.Write) : Work
        data class LightUpload(val design: LightDesign, val designs: List<LightDesign>) : Work
        data object LightList : Work
    }
    private val fileResponses = Channel<LightEffects.Reply>(Channel.BUFFERED)
    @Volatile private var awaitingFile = false
    private val work = Channel<Work>(1)
    // Debug-only diagnostics: messages are fixed labels/statuses, never security payloads or identifiers.
    private fun lockTrace(message: String, reset: Boolean = false) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.i("IonLockTest", message)
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return@runCatching
            val file = java.io.File(dir, "lock-test-diagnostic.txt")
            if (reset || file.length() > 4096) file.writeText("")
            file.appendText("${SystemClock.elapsedRealtime()} $message\n")
        }
    }
    private fun mayWrite(tier: V12Protocol.Tier, pending: Boolean, ignorePending: Boolean = false): Boolean {
        val s = mutable.value; val t = s.telemetry
        return (tier != V12Protocol.Tier.STATIONARY || s.stationaryPackets >= 3) && V12Protocol.canWrite(tier, s.verifiedV12, s.phase == Phase.CONNECTED,
            s.lastPacketAt?.let { SystemClock.elapsedRealtime()-it }, t?.speed, t?.charging ?: false,
            t?.firmwareUpdate ?: false, pending && !ignorePending)
    }
    fun readAll() { enqueue(Work.ReadAll) }
    fun read(read: V12Protocol.Read) { enqueue(Work.ReadOne(read)) }
    fun apply(command: V12Protocol.Write) {
        // The lock is offered on every supported V12 (only proven on the HT); its security exchange and read-back
        // still refuse to report success unless the wheel confirms the new state.
        // Only an in-flight write blocks a new one; queued reads must not reject the tap.
        if(!mayWrite(command.kind.tier, mutable.value.writeInFlight)) { mutable.update { it.copy(writeMessage=if(command.kind.tier == V12Protocol.Tier.SAFE)
            str(R.string.ble_write_no_link) else
            str(R.string.ble_write_moving)) }; return }
        enqueue(Work.Write(command))
    }
    /** Uploads a personal light effect file and makes it the active effect, as the vendor app does. */
    fun uploadLight(design: LightDesign, designs: List<LightDesign>) {
        if (!mayWrite(V12Protocol.Tier.SAFE, mutable.value.writeInFlight)) {
            mutable.update { it.copy(writeMessage = str(R.string.ble_write_no_link)) }; return
        }
        if (enqueue(Work.LightUpload(design, designs))) mutable.update { it.copy(lightTransfer = LightTransfer(design.slot, null, 0f)) }
        else mutable.update { it.copy(writeMessage = str(R.string.light_upload_busy)) }
    }
    fun listLights() { enqueue(Work.LightList) }
    fun clearLightTransfer() = mutable.update { it.copy(lightTransfer = null) }
    private fun enqueue(item: Work): Boolean {
        if(mutable.value.phase != Phase.CONNECTED || !mutable.value.verifiedV12 || mutable.value.workPending) return false
        if(!work.trySend(item).isSuccess) return false
        mutable.update { it.copy(workPending=true, writeMessage=if(item is Work.Write || item is Work.LightUpload) null else it.writeMessage) }
        return true
    }
    private fun clearWork() { while(work.tryReceive().isSuccess) {} }
    private fun record(read: V12Protocol.Read, frame: V12Protocol.Frame? = null, status: String) {
        mutable.update { s ->
            val old = s.records[read]
            s.copy(records=s.records + (read to PacketRecord(read,frame ?: old?.frame,
                if(frame != null) SystemClock.elapsedRealtime() else old?.at,status)))
        }
    }

    fun hasPermissions(): Boolean = permissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    /** Location permission for the GPS track; separate from the Bluetooth set and never auto-requested. */
    fun grantedLocation(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch {
                if (!scanning || !hasPermissions()) return@launch
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: str(R.string.ble_device_generic)
                val device = WheelDevice(result.device.address, name, result.rssi, result.scanRecord?.deviceName)
                mutable.update { s -> s.copy(devices = (s.devices.filterNot { it.address == device.address } + device)
                    .sortedWith(compareByDescending<WheelDevice> { it.name.contains("V12", ignoreCase = true) }.thenByDescending { it.rssi }).take(30)) }
                discoveredDevices[device.address] = result.device
                discoveredDevices.keys.retainAll(mutable.value.devices.map { it.address }.toSet())
            }
        }
        override fun onScanFailed(errorCode: Int) {
            scope.launch { if (scanning) error(str(R.string.ble_scan_failed, errorCode)) }
        }
    }
    fun scan() {
        disconnect()
        discoveredDevices.clear()
        if (!hasPermissions()) { error(str(R.string.ble_need_permission)); return }
        try {
            val a = adapter
            if (a == null || !a.isEnabled) { error(str(R.string.ble_turn_on)); return }
            mutable.value = WheelState(phase = Phase.SCANNING, message = str(R.string.ble_scanning))
            scanning = true
            // Show the specific reason; a thrown exception fell into the generic "check Bluetooth" branch below.
            val scanner = a.bluetoothLeScanner ?: run { scanning = false; error(str(R.string.ble_scan_unavailable)); return }
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            scanTimeout = scope.launch { delay(12_000); stopScan() }
        } catch (_: SecurityException) { error(str(R.string.ble_permission_revoked)) }
        catch (_: Exception) { error(str(R.string.ble_scan_check)) }
    }
    fun stopScan() {
        scanTimeout?.cancel(); scanTimeout = null
        if (scanning) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
        mutable.update { s -> if (s.phase == Phase.SCANNING) s.copy(phase = Phase.OFFLINE,
            message = if (s.devices.isEmpty()) str(R.string.ble_nothing_found) else str(R.string.ble_pick_wheel)) else s }
    }
    fun permissionDenied() { error(str(R.string.ble_permission_denied)) }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                if (g !== gatt) return@launch
                // A single non-success status on a live link (e.g. transient 133/8/19) is not a disconnect:
                // only STATE_DISCONNECTED ends the session. Setup-time failures still fail via waitEvent.
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    error(str(R.string.ble_link_dropped, if (status != 0) " (GATT $status)" else ""))
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    mutable.update { it.copy(message = str(R.string.ble_gatt_warning, status)) }
                } else if (newState == BluetoothProfile.STATE_CONNECTED) events.trySend(Event("connected", status))
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { if (g === gatt) events.trySend(Event("services", status)) }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { if (g === gatt) events.trySend(Event("descriptor", status)) }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu
            events.trySend(Event("mtu", status, mtu))
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) scope.launch { if (g === gatt) error(str(R.string.ble_request_error, status)) }
            else if (awaitingWrite) events.trySend(Event("write", status))
        }
        @Deprecated("Legacy callback for API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) receive(g, c.uuid, c.value?.clone() ?: byteArrayOf())
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { receive(g, c.uuid, value.clone()) }
    }
    private fun receive(g: BluetoothGatt, uuid: UUID, value: ByteArray) {
        scope.launch {
            if (g !== gatt || uuid.toString() != V12Protocol.NOTIFY) return@launch
            for (f in decoder.feed(value)) {
                // File transfer and effect replies use the addressed layout too; only while a transfer waits for them.
                if (awaitingFile) LightEffects.reply(f)?.let { fileResponses.trySend(it); continue }
                // Security replies use a different address layout, not the legacy response bit.
                if (awaitingSecurity && LockSecurity.isRelevantReply(f)) {
                    securityResponses.trySend(f)
                    continue
                }
                // The addressed lock reply is not represented as Frame.response by the legacy parser.
                if (f.flags == 0x16 && f.commandByte == 0x21 &&
                    f.payload.contentEquals(byteArrayOf(0x02, 0xe0.toByte(), 0x31, 0x00))) {
                    lockTrace("lock_reply observed; status semantics unknown")
                }
                if (!f.response) continue
                if (mutable.value.verifiedV12 || V12Protocol.Read.MODEL.matches(f)) {
                    V12Protocol.Read.entries.firstOrNull { it.matches(f) }?.let { record(it,f,str(R.string.ble_received)) }
                }
                if (mutable.value.verifiedV12) {
                    V12Protocol.telemetry(f)?.let { parsed ->
                        // Debounce the firmware-update bit: one noisy packet must not lock the controls.
                        val streak = if(parsed.firmwareUpdate) mutable.value.firmwareUpdateStreak+1 else 0
                        val t = parsed.copy(firmwareUpdate = streak >= 2)
                        mutable.update { s ->
                            // Stillness: a balancing wheel micro-moves, so the threshold is 0.5 km/h and a
                            // single speed spike must not reset the counter (2 consecutive movers do).
                            val moving = kotlin.math.abs(t.speed) >= 0.5
                            val movingStreak = if(moving) s.movingStreak+1 else 0
                            val stationary = if(movingStreak >= 2) 0 else (s.stationaryPackets+1).coerceAtMost(100)
                            s.copy(telemetry = t,
                        phase = Phase.CONNECTED, message = str(R.string.ble_live), lastPacketAt = SystemClock.elapsedRealtime(), packets = s.packets + 1,
                        firmwareUpdateStreak = streak,
                        movingStreak = movingStreak,
                        stationaryPackets = if(!t.firmwareUpdate) stationary else 0) }
                        telemetrySink?.invoke(t, SystemClock.elapsedRealtime()) }
                    V12Protocol.settings(f)?.let { v -> mutable.update { it.copy(settings = v, settingsAt = SystemClock.elapsedRealtime()) } }
                }
                responses.trySend(f)
            }
        }
    }
    /** Scan filters allow rediscovery during a screen-off reconnect. A matching name only selects a candidate. */
    private suspend fun rediscover(saved: WheelDevice): ScanResult? {
        val scanner = adapter?.bluetoothLeScanner ?: return null
        val name = saved.recoveryName()
        val filters = buildList {
            add(ScanFilter.Builder().setDeviceAddress(saved.address.uppercase()).build())
            name?.let { add(ScanFilter.Builder().setDeviceName(it).build()) }
        }
        val exact = CompletableDeferred<ScanResult?>()
        var alternate: ScanResult? = null
        var ambiguous = false
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch {
                    if (exact.isCompleted || !result.isConnectable) return@launch
                    val address = result.device.address
                    if (address.equals(saved.address, ignoreCase = true)) exact.complete(result)
                    else if (name != null && result.scanRecord?.deviceName == name) {
                        if (alternate?.device?.address?.let { it != address } == true) ambiguous = true
                        alternate = result
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                exact.complete(null)
            }
        }
        try {
            scanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            return withTimeoutOrNull(4_000) { exact.await() } ?: alternate?.takeUnless { ambiguous }
        } catch (_: IllegalStateException) {
            return null
        } finally {
            exact.cancel()
            runCatching { scanner.stopScan(callback) }
        }
    }

    fun connect(device: WheelDevice, fromSaved: Boolean = false) {
        val selected = discoveredDevices.remove(device.address)
        disconnect()
        if (!hasPermissions()) { permissionDenied(); return }
        if(adapter?.isEnabled != true) { error(str(R.string.ble_turn_on)); return }
        mutable.value = WheelState(phase = Phase.CONNECTING,
            message = str(if (fromSaved) R.string.ble_rediscovering else R.string.ble_connecting), deviceName = device.name)
        session = scope.launch {
            var owned: BluetoothGatt? = null
            try {
                val seen = if (fromSaved) rediscover(device) else null
                while (events.tryReceive().isSuccess) { /* clear previous session */ }
                while (responses.tryReceive().isSuccess) { }
                while (securityResponses.tryReceive().isSuccess) { }
                awaitingSecurity = false
                negotiatedMtu = 23
                decoder = V12Protocol.Decoder()
                val remote = seen?.device ?: selected ?: adapter?.getRemoteDevice(device.address.uppercase())
                mutable.update { it.copy(message = str(R.string.ble_connecting)) }
                val link = remote?.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                    ?: throw LinkFailure(str(R.string.ble_unavailable))
                owned = link
                gatt = link
                waitEvent("connected", 12_000)
                mutable.update { it.copy(phase = Phase.DISCOVERING, message = str(R.string.ble_discovering)) }
                check(link.discoverServices()) { str(R.string.ble_discover_failed) }
                waitEvent("services")
                val service = link.getService(UUID.fromString(V12Protocol.SERVICE)) ?: throw LinkFailure(str(R.string.ble_no_service))
                val write = service.getCharacteristic(UUID.fromString(V12Protocol.WRITE)) ?: throw LinkFailure(str(R.string.ble_no_write_char))
                val notify = service.getCharacteristic(UUID.fromString(V12Protocol.NOTIFY)) ?: throw LinkFailure(str(R.string.ble_no_notify_char))
                check(write.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) { str(R.string.ble_char_no_write) }
                val notifyValue = when {
                    notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    else -> throw LinkFailure(str(R.string.ble_char_no_notify))
                }
                check(link.setCharacteristicNotification(notify, true)) { str(R.string.ble_notify_failed) }
                val cccd = notify.getDescriptor(UUID.fromString(V12Protocol.CCCD)) ?: throw LinkFailure(str(R.string.ble_no_cccd))
                if (Build.VERSION.SDK_INT >= 33) check(link.writeDescriptor(cccd, notifyValue) == BluetoothStatusCodes.SUCCESS) { str(R.string.ble_cccd_rejected) }
                else {
                    @Suppress("DEPRECATION")
                    cccd.value = notifyValue
                    @Suppress("DEPRECATION")
                    check(link.writeDescriptor(cccd)) { str(R.string.ble_cccd_rejected) }
                }
                waitEvent("descriptor")
                mutable.update { it.copy(phase = Phase.IDENTIFYING, message = str(R.string.ble_identifying)) }
                var identity: V12Protocol.Model? = null
                repeat(3) {
                    if (identity == null) try { identity = V12Protocol.model(ask(link, write, V12Protocol.Read.MODEL)) }
                    catch (_: TimeoutCancellationException) { /* bounded retry */ }
                }
                val model = identity ?: throw LinkFailure(str(R.string.ble_no_model))
                check(model.isSupported) { str(R.string.ble_unsupported_model, model.label) }
                val addressChanged = !device.address.equals(link.device.address, ignoreCase = true)
                val serial = if (addressChanged || device.serial == null) {
                    optionalRead(link, write, V12Protocol.Read.SERIAL, 1_000)?.let(V12Protocol::serial)
                } else device.serial
                if (!device.acceptsRecoveredAddress(link.device.address, serial)) {
                    throw LinkFailure(str(R.string.ble_serial_mismatch))
                }
                val verifiedDevice = device.copy(
                    address = link.device.address,
                    name = model.label,
                    advertisedName = seen?.scanRecord?.deviceName ?: device.advertisedName,
                    serial = serial
                )
                mutable.update { it.copy(model = model, deviceName = model.label, message = str(R.string.ble_waiting_telemetry)) }
                optionalRead(link,write,V12Protocol.Read.SETTINGS)
                var missed = 0
                var saved = false
                var statisticsDue = SystemClock.elapsedRealtime()
                var settingsDue = SystemClock.elapsedRealtime() + 5000
                while (isActive) {
                    try {
                        val frame = ask(link, write, V12Protocol.Read.TELEMETRY, Timing.TELEMETRY_ASK_MS)
                        checkNotNull(V12Protocol.telemetry(frame)) { str(R.string.ble_bad_telemetry) }
                        missed = 0
                        if(!saved) { onConnected(verifiedDevice); saved = true }
                    } catch (_: TimeoutCancellationException) {
                        record(V12Protocol.Read.TELEMETRY,status=str(R.string.ble_timeout_status))
                        missed++
                        if (missed >= 3) throw LinkFailure(str(R.string.ble_no_telemetry))
                    }
                    val job = work.tryReceive().getOrNull()
                    if(job != null) {
                        try {
                            when(job) {
                                Work.ReadAll -> for(read in V12Protocol.Read.entries) {
                                    optionalRead(link,write,read)
                                    if(read != V12Protocol.Read.TELEMETRY) optionalRead(link,write,V12Protocol.Read.TELEMETRY)
                                }
                                is Work.ReadOne -> optionalRead(link,write,job.read)
                                is Work.LightList -> listStoredLights(link, write)
                                is Work.LightUpload -> {
                                    mutable.update { it.copy(writeInFlight=true) }
                                    // A fresh id the wheel does not have yet: renaming onto an existing file fails.
                                    listStoredLights(link, write)
                                    val stored = mutable.value.storedLights
                                    val id = stored?.let { LightDesign.nextId(it, job.designs) }
                                    val error = if (id == null) str(R.string.light_upload_no_list)
                                        else uploadLight(link, write, job.design.slot, id, job.design.file(id))
                                    mutable.update { it.copy(lightTransfer = LightTransfer(job.design.slot, id, if (error == null) 1f else
                                        it.lightTransfer?.progress ?: 0f, done = true, error = error)) }
                                    // The selected effect shows up as personal mode in the settings, and in the list.
                                    optionalRead(link, write, V12Protocol.Read.SETTINGS)
                                    if (error == null) listStoredLights(link, write)
                                }
                                is Work.Write -> {
                                    mutable.update { it.copy(writeInFlight=true) }
                                    if(!mayWrite(job.command.kind.tier, pending=true, ignorePending=true)) mutable.update { it.copy(writeMessage=
                                        if(job.command.kind.tier == V12Protocol.Tier.SAFE) str(R.string.ble_not_sent_link)
                                        else str(R.string.ble_not_sent_moving)) }
                                    else {
                                        val isLock = job.command.kind == V12Protocol.Control.LOCK
                                        if (isLock) lockTrace("begin target=${job.command.a}", reset=true)
                                        val securityReady = !isLock || prepareLockSecurity(link, write)
                                        if (!securityReady) {
                                            mutable.update { it.copy(writeMessage=str(R.string.ble_lock_refused)) }
                                        } else if (isLock && !mayWrite(job.command.kind.tier, pending=true, ignorePending=true)) {
                                            lockTrace("guard failed after security; no lock write")
                                            mutable.update { it.copy(writeMessage=str(R.string.ble_lock_moving)) }
                                        } else {
                                        mutable.update { it.copy(writeMessage=null) }
                                        val bytes = job.command.bytes()
                                        if (job.command.kind == V12Protocol.Control.LOCK) {
                                            lockTrace("request target=${job.command.a} bytes=${bytes.joinToString("") { "%02X".format(it.toInt() and 0xff) }}")
                                        }
                                        send(link,write,bytes)
                                        if (job.command.kind == V12Protocol.Control.LOCK) lockTrace("send returned; wheel state not yet confirmed")
                                        val back = job.command.readBack
                                        if(back == null) mutable.update { it.copy(writeMessage=null) }
                                        else {
                                            mutable.update { it.copy(writeMessage=null) }
                                            delay(200)
                                            // Read-back is read-only, so one retry on a lost answer is safe.
                                            val answer = optionalRead(link,write,back) ?: optionalRead(link,write,back)
                                            val confirmed = answer?.let { job.command.confirmed(it) }
                                            if (job.command.kind == V12Protocol.Control.LOCK) lockTrace(
                                                "readback target=${job.command.a} result=${if (answer == null) "timeout" else if (confirmed == null) "invalid" else if (confirmed) "match" else "mismatch"}")
                                            val result = when {
                                                confirmed == true -> null
                                                answer == null -> str(R.string.ble_apply_no_answer)
                                                confirmed == false -> str(R.string.ble_apply_mismatch)
                                                else -> str(R.string.ble_apply_unknown)
                                            }
                                            mutable.update { it.copy(writeMessage=result) }
                                        }
                                        }
                                    }
                                }
                            }
                        } finally { mutable.update { it.copy(workPending=false, writeInFlight=false) } }
                    }
                    if(SystemClock.elapsedRealtime() >= settingsDue) {
                        // One retry keeps the 15s settings-freshness window stable on a flaky radio.
                        if(optionalRead(link,write,V12Protocol.Read.SETTINGS) == null) optionalRead(link,write,V12Protocol.Read.SETTINGS)
                        settingsDue=SystemClock.elapsedRealtime()+5000
                    } else if (missed == 0 && SystemClock.elapsedRealtime() >= statisticsDue) {
                        optionalRead(link, write, V12Protocol.Read.STATISTICS, Timing.TELEMETRY_ASK_MS)
                        statisticsDue = SystemClock.elapsedRealtime() + Timing.STATISTICS_INTERVAL_MS
                    }
                    delay(Timing.POLL_DELAY_MS)
                }
            } catch (_: TimeoutCancellationException) { error(str(R.string.ble_timeout)) }
            catch (e: CancellationException) { throw e }
            catch (_: SecurityException) { error(str(R.string.ble_permission_revoked)) }
            catch (e: LinkFailure) { error(e.message ?: str(R.string.ble_connect_failed)) }
            catch (_: Exception) { error(str(R.string.ble_connect_failed)) }
            finally {
                runCatching { owned?.disconnect(); owned?.close() }
                if (gatt === owned) gatt = null
            }
        }
    }
    private suspend fun waitEvent(kind: String, timeout: Long = 6000) = withTimeout(timeout) {
        while (true) {
            val e = events.receive()
            if (e.kind == kind) { check(e.status == BluetoothGatt.GATT_SUCCESS) { str(R.string.ble_error_code, e.status) }; break }
        }
    }
    private suspend fun ask(g: BluetoothGatt, c: BluetoothGattCharacteristic, read: V12Protocol.Read, timeout: Long = 2000): V12Protocol.Frame {
        while (responses.tryReceive().isSuccess) { }
        record(read,status=str(R.string.ble_waiting_answer))
        send(g,c,V12Protocol.request(read))
        return withTimeout(timeout) {
            var found: V12Protocol.Frame? = null
            while (found == null) { val f = responses.receive(); if (read.matches(f)) found = f }
            found
        }
    }
    /** Fail closed; security exchange does not itself issue the lock command. */
    private suspend fun prepareLockSecurity(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        if (negotiatedMtu < 44) {
            while (events.tryReceive().isSuccess) { }
            if (!g.requestMtu(64)) { lockTrace("mtu request rejected"); return false }
            val mtuEvent = withTimeoutOrNull(2000) {
                var result: Event? = null
                while (result == null) { val e = events.receive(); if (e.kind == "mtu") result = e }
                result
            }
            if (mtuEvent == null || mtuEvent.status != BluetoothGatt.GATT_SUCCESS) {
                lockTrace("mtu failed or timed out"); return false
            }
        }
        // Worst-case escaped second message is <= 41 bytes; never silently truncate it.
        if (negotiatedMtu < 44) { lockTrace("mtu too small: $negotiatedMtu"); return false }
        lockTrace("mtu=$negotiatedMtu; security request")
        while (securityResponses.tryReceive().isSuccess) { }
        awaitingSecurity = true
        try {
            send(g, c, LockSecurity.keyRequest())
            val first = withTimeoutOrNull(1800) { securityResponses.receive() }
            val key = first?.let { LockSecurity.keyFrom(it) }
            if (key == null) { lockTrace("security key response absent or invalid"); return false }
            lockTrace("security key response valid")
            val verification = LockSecurity.verifyRequest(key)
            try {
                if (verification.size > negotiatedMtu - 3) { lockTrace("security message exceeds mtu"); return false }
                send(g, c, verification)
                val second = withTimeoutOrNull(1800) {
                    var f = securityResponses.receive()
                    while (f.payload.getOrNull(1) != 0x82.toByte()) f = securityResponses.receive()
                    f
                }
                if (second == null || !LockSecurity.isVerifyReply(second)) {
                    lockTrace("security verify response absent or invalid"); return false
                }
                lockTrace("security verify observed")
                return true
            } finally { key.fill(0); verification.fill(0) }
        } finally {
            awaitingSecurity = false
            while (securityResponses.tryReceive().isSuccess) { }
        }
    }
    /** Asks for a larger MTU when the link is below [minimum]; returns whether it is at least [minimum] now. */
    private suspend fun ensureMtu(g: BluetoothGatt, minimum: Int, request: Int): Boolean {
        if (negotiatedMtu >= minimum) return true
        while (events.tryReceive().isSuccess) { }
        if (!g.requestMtu(request)) return false
        withTimeoutOrNull(2000) {
            var result: Event? = null
            while (result == null) { val e = events.receive(); if (e.kind == "mtu") result = e }
        }
        return negotiatedMtu >= minimum
    }

    /** Sends one step and waits for its reply (one retry on silence for steps that are safe to repeat). */
    private suspend fun fileStep(g: BluetoothGatt, c: BluetoothGattCharacteristic, step: LightEffects.Step): LightEffects.Reply? {
        val attempts = if (step.label == "rename") 1 else 2
        repeat(attempts) {
            while (fileResponses.tryReceive().isSuccess) { }
            send(g, c, step.bytes)
            if (step.channel == null) { delay(150); return LightEffects.Reply(0, 0, byteArrayOf(0), null) }
            val reply = withTimeoutOrNull(2_500) {
                var r = fileResponses.receive()
                while (!r.answers(step)) r = fileResponses.receive()
                r
            }
            if (reply != null) return reply
        }
        return null
    }

    /** The vendor app's upload sequence ([LightEffects.upload]); returns null on success or what failed. */
    private suspend fun uploadLight(g: BluetoothGatt, c: BluetoothGattCharacteristic, slot: Int, id: Long, file: ByteArray): String? {
        // The app writes 191-byte chunk frames in one go; ask for room for them (the floor still allows small chunks).
        if (!ensureMtu(g, 200, 247) && negotiatedMtu < 64) return str(R.string.light_upload_mtu)
        val steps = runCatching { LightEffects.upload(id, file, negotiatedMtu - 3) }.getOrElse { return str(R.string.light_upload_mtu) }
        val chunks = steps.count { it.label == "chunk" }
        var sent = 0
        awaitingFile = true
        try {
            for (step in steps) {
                if (!mayWrite(V12Protocol.Tier.SAFE, pending = true, ignorePending = true)) return str(R.string.ble_not_sent_link)
                val reply = fileStep(g, c, step) ?: return str(R.string.light_upload_no_answer, step.label)
                if (!reply.ok) return str(R.string.light_upload_refused, step.label)
                if (step.label == "chunk") {
                    sent++
                    mutable.update { it.copy(lightTransfer = LightTransfer(slot, id, sent.toFloat() / chunks)) }
                }
            }
            return null
        } finally {
            awaitingFile = false
            while (fileResponses.tryReceive().isSuccess) { }
        }
    }

    private suspend fun listStoredLights(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        awaitingFile = true
        try {
            val reply = fileStep(g, c, LightEffects.list())
            reply?.let(LightEffects::parseList)?.let { list -> mutable.update { it.copy(storedLights = list) } }
        } finally {
            awaitingFile = false
            while (fileResponses.tryReceive().isSuccess) { }
        }
    }

    private suspend fun send(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray) {
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        awaitingWrite = type == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= 33) check(g.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS) { str(R.string.ble_request_rejected) }
        else {
            @Suppress("DEPRECATION")
            c.value = bytes
            c.writeType = type
            @Suppress("DEPRECATION")
            check(g.writeCharacteristic(c)) { str(R.string.ble_request_rejected) }
        }
        if (type == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) waitEvent("write") else delay(60)
        awaitingWrite = false
    }
    private suspend fun optionalRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, read: V12Protocol.Read, timeout: Long = 2_000): V12Protocol.Frame? {
        return try {
            val f=ask(g,c,read,timeout)
            if(read == V12Protocol.Read.MODEL) check(V12Protocol.model(f) == mutable.value.model) { str(R.string.ble_model_changed) }
            f
        } catch(_: TimeoutCancellationException) { record(read,status=str(R.string.ble_timeout_status)); null }
    }
    fun disconnect() {
        clearWork(); stopScan(); session?.cancel(); session = null
        val old = gatt; gatt = null
        runCatching { old?.disconnect(); old?.close() }
        mutable.update { it.copy(phase = Phase.OFFLINE, message = str(R.string.ble_offline), model = null, telemetry = null, settings = null, lastPacketAt = null, packets = 0, settingsAt = null, records = emptyMap(), workPending = false, writeMessage = null, stationaryPackets = 0, lightTransfer = null, storedLights = null) }
    }
    /** A connection failure whose message is already localized for the rider. */
    private class LinkFailure(message: String) : Exception(message)

    private fun error(text: String) {
        clearWork(); stopScan(); session?.cancel(); session = null
        val old = gatt; gatt = null
        runCatching { old?.disconnect(); old?.close() }
        mutable.update { it.copy(phase = Phase.ERROR, message = text, model = null, workPending = false, stationaryPackets = 0, writeMessage=if(it.workPending && it.writeMessage!=null) str(R.string.ble_write_interrupted) else it.writeMessage) }
    }
    fun onBackground() { if (mutable.value.phase !in listOf(Phase.OFFLINE, Phase.ERROR)) disconnect() }
}
