package dev.vela.v12

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
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
    const val FRESH_WINDOW_MS = 2500L
    /** Sustained staleness before any banner may appear: brief packet gaps never flash UI. */
    const val STALE_BANNER_AFTER_MS = 2500L
}
data class WheelDevice(val address: String, val name: String, val rssi: Int)
data class PacketRecord(val read: V12Protocol.Read, val frame: V12Protocol.Frame? = null, val at: Long? = null, val status: String = "Ещё не запрошено")
data class WheelState(
    val phase: Phase = Phase.OFFLINE,
    val message: String = "Колесо не подключено",
    val devices: List<WheelDevice> = emptyList(),
    val deviceName: String? = null,
    val verifiedHt: Boolean = false,
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
    /** Consecutive telemetry packets carrying the firmware-update bit; the guard acts from 2 in a row. */
    val firmwareUpdateStreak: Int = 0
)
/** Foreground-only BLE. Only serialized, validated reads/accessory controls. No internet access.
 * All GATT setup operations run sequentially; notifications are incrementally decoded.
 */
@SuppressLint("MissingPermission")
class BleController(private val context: Context, private val scope: CoroutineScope, private val onConnected: (WheelDevice) -> Unit = {}) {
    private val mutable = MutableStateFlow(WheelState())
    val state = mutable.asStateFlow()
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var awaitingWrite = false
    private var session: Job? = null
    private var scanTimeout: Job? = null
    private var scanning = false
    private var decoder = V12Protocol.Decoder()
    private data class Event(val kind: String, val status: Int)
    private val events = Channel<Event>(Channel.BUFFERED)
    private val responses = Channel<V12Protocol.Frame>(Channel.BUFFERED)
    private sealed interface Work {
        data object ReadAll : Work
        data class ReadOne(val read: V12Protocol.Read) : Work
        data class Write(val command: V12Protocol.Write) : Work
    }
    private val work = Channel<Work>(1)
    private fun mayWrite(tier: V12Protocol.Tier, pending: Boolean, ignorePending: Boolean = false): Boolean {
        val s = mutable.value; val t = s.telemetry
        return (tier != V12Protocol.Tier.STATIONARY || s.stationaryPackets >= 3) && V12Protocol.canWrite(tier, s.verifiedHt, s.phase == Phase.CONNECTED,
            s.lastPacketAt?.let { SystemClock.elapsedRealtime()-it }, t?.speed, t?.charging ?: false,
            t?.firmwareUpdate ?: false, pending && !ignorePending)
    }
    fun readAll() = enqueue(Work.ReadAll)
    fun read(read: V12Protocol.Read) = enqueue(Work.ReadOne(read))
    fun apply(command: V12Protocol.Write) {
        // Only an in-flight write blocks a new one; queued reads must not reject the tap.
        if(!mayWrite(command.kind.tier, mutable.value.writeInFlight)) { mutable.update { it.copy(writeMessage=if(command.kind.tier == V12Protocol.Tier.SAFE)
            "Запись заблокирована: нужны свежие данные с колеса." else
            "Запись заблокирована: нужны свежие данные и остановленное колесо.") }; return }
        enqueue(Work.Write(command))
    }
    private fun enqueue(item: Work) {
        if(mutable.value.phase != Phase.CONNECTED || !mutable.value.verifiedHt || mutable.value.workPending) return
        if(work.trySend(item).isSuccess) mutable.update { it.copy(workPending=true, writeMessage=if(item is Work.Write) "Ожидает отправки…" else it.writeMessage) }
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
    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch {
                if (!scanning || !hasPermissions()) return@launch
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "BLE-устройство"
                val device = WheelDevice(result.device.address, name, result.rssi)
                mutable.update { s -> s.copy(devices = (s.devices.filterNot { it.address == device.address } + device)
                    .sortedWith(compareByDescending<WheelDevice> { it.name.contains("V12", ignoreCase = true) }.thenByDescending { it.rssi }).take(30)) }
            }
        }
        override fun onScanFailed(errorCode: Int) { scope.launch { stopScan(); error("Не удалось начать поиск ($errorCode). Попробуй ещё раз.") } }
    }
    fun scan() {
        disconnect()
        if (!hasPermissions()) { error("Нужно разрешение на поиск и подключение BLE."); return }
        try {
            val a = adapter
            if (a == null || !a.isEnabled) { error("Включи Bluetooth в настройках телефона."); return }
            mutable.value = WheelState(phase = Phase.SCANNING, message = "Ищу устройства рядом…")
            scanning = true
            val scanner = a.bluetoothLeScanner ?: throw IllegalStateException("BLE-поиск недоступен")
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            scanTimeout = scope.launch { delay(12_000); stopScan() }
        } catch (_: SecurityException) { error("Разрешение Bluetooth отозвано.") }
        catch (_: Exception) { error("Поиск BLE недоступен. Проверь Bluetooth и разрешения.") }
    }
    fun stopScan() {
        scanTimeout?.cancel(); scanTimeout = null
        if (scanning) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
        mutable.update { s -> if (s.phase == Phase.SCANNING) s.copy(phase = Phase.OFFLINE,
            message = if (s.devices.isEmpty()) "Ничего не найдено. На Android 11 и ниже проверь геолокацию." else "Выбери своё колесо из списка") else s }
    }
    fun permissionDenied() { error("Для подключения нужно разрешение Bluetooth. Его можно выдать в настройках приложения.") }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                if (g !== gatt) return@launch
                // A single non-success status on a live link (e.g. transient 133/8/19) is not a disconnect:
                // only STATE_DISCONNECTED ends the session. Setup-time failures still fail via waitEvent.
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    error("Связь прервана${if (status != 0) " (GATT $status)" else ""}. Данные больше не обновляются.")
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    mutable.update { it.copy(message = "Предупреждение BLE (GATT $status); соединение удерживается.") }
                } else if (newState == BluetoothProfile.STATE_CONNECTED) events.trySend(Event("connected", status))
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { if (g === gatt) events.trySend(Event("services", status)) }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { if (g === gatt) events.trySend(Event("descriptor", status)) }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) scope.launch { if (g === gatt) error("Ошибка BLE-запроса ($status).") }
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
                if (!f.response) continue
                if (mutable.value.verifiedHt || V12Protocol.Read.MODEL.matches(f)) {
                    V12Protocol.Read.entries.firstOrNull { it.matches(f) }?.let { record(it,f,"Получено") }
                }
                if (mutable.value.verifiedHt) {
                    V12Protocol.telemetry(f)?.let { parsed ->
                        // Debounce the firmware-update bit: one noisy packet must not lock the controls.
                        val streak = if(parsed.firmwareUpdate) mutable.value.firmwareUpdateStreak+1 else 0
                        val t = parsed.copy(firmwareUpdate = streak >= 2)
                        mutable.update { s -> s.copy(telemetry = t,
                        phase = Phase.CONNECTED, message = "Данные с колеса", lastPacketAt = SystemClock.elapsedRealtime(), packets = s.packets + 1,
                        firmwareUpdateStreak = streak,
                        // Stillness counter: charging does not reset it — a charging wheel is not moving.
                        stationaryPackets = if(kotlin.math.abs(t.speed)<0.1 && !t.firmwareUpdate) (s.stationaryPackets+1).coerceAtMost(100) else 0) } }
                    V12Protocol.settings(f)?.let { v -> mutable.update { it.copy(settings = v, settingsAt = SystemClock.elapsedRealtime()) } }
                }
                responses.trySend(f)
            }
        }
    }
    fun connect(device: WheelDevice) {
        disconnect()
        if (!hasPermissions()) { permissionDenied(); return }
        if(adapter?.isEnabled != true) { error("Включи Bluetooth в настройках телефона."); return }
        mutable.value = WheelState(phase = Phase.CONNECTING, message = "Подключаюсь…", deviceName = device.name)
        session = scope.launch {
            var owned: BluetoothGatt? = null
            try {
                while (events.tryReceive().isSuccess) { /* clear previous session */ }
                while (responses.tryReceive().isSuccess) { }
                decoder = V12Protocol.Decoder()
                val link = adapter?.getRemoteDevice(device.address)?.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                    ?: throw IllegalStateException("BLE недоступен")
                owned = link
                gatt = link
                waitEvent("connected", 12_000)
                mutable.update { it.copy(phase = Phase.DISCOVERING, message = "Проверяю BLE-сервисы…") }
                check(link.discoverServices()) { "Не удалось запросить BLE-сервисы" }
                waitEvent("services")
                val service = link.getService(UUID.fromString(V12Protocol.SERVICE)) ?: throw IllegalStateException("Нет ожидаемого сервиса INMOTION V2")
                val write = service.getCharacteristic(UUID.fromString(V12Protocol.WRITE)) ?: throw IllegalStateException("Нет характеристики запросов")
                val notify = service.getCharacteristic(UUID.fromString(V12Protocol.NOTIFY)) ?: throw IllegalStateException("Нет характеристики телеметрии")
                check(write.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) { "Характеристика не принимает запросы" }
                val notifyValue = when {
                    notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    else -> throw IllegalStateException("Характеристика не поддерживает уведомления")
                }
                check(link.setCharacteristicNotification(notify, true)) { "Не удалось включить уведомления" }
                val cccd = notify.getDescriptor(UUID.fromString(V12Protocol.CCCD)) ?: throw IllegalStateException("Нет дескриптора уведомлений")
                if (Build.VERSION.SDK_INT >= 33) check(link.writeDescriptor(cccd, notifyValue) == BluetoothStatusCodes.SUCCESS) { "Запись CCCD отклонена" }
                else {
                    @Suppress("DEPRECATION")
                    cccd.value = notifyValue
                    @Suppress("DEPRECATION")
                    check(link.writeDescriptor(cccd)) { "Запись CCCD отклонена" }
                }
                waitEvent("descriptor")
                mutable.update { it.copy(phase = Phase.IDENTIFYING, message = "Проверяю, что это V12 HT…") }
                var identity: V12Protocol.Model? = null
                repeat(3) {
                    if (identity == null) try { identity = V12Protocol.model(ask(link, write, V12Protocol.Read.MODEL)) }
                    catch (_: TimeoutCancellationException) { /* bounded retry */ }
                }
                val model = identity ?: throw IllegalStateException("Колесо не ответило на запрос модели")
                check(model.isHt) { "Ожидалась V12 HT, ответ: ${model.label}. Опрос остановлен." }
                mutable.update { it.copy(verifiedHt = true, deviceName = model.label, message = "V12 HT определена. Жду телеметрию…") }
                optionalRead(link,write,V12Protocol.Read.SETTINGS)
                optionalRead(link,write,V12Protocol.Read.STATISTICS) // one-time: odometer for the ride screen
                var missed = 0
                var saved = false
                var settingsDue = SystemClock.elapsedRealtime() + 5000
                while (isActive) {
                    try {
                        val frame = ask(link, write, V12Protocol.Read.TELEMETRY, Timing.TELEMETRY_ASK_MS)
                        checkNotNull(V12Protocol.telemetry(frame)) { "Неподдерживаемый формат телеметрии. Нужна проверка прошивки." }
                        missed = 0
                        if(!saved) { onConnected(device); saved = true }
                    } catch (_: TimeoutCancellationException) {
                        record(V12Protocol.Read.TELEMETRY,status="Нет ответа · тайм-аут")
                        missed++
                        if (missed >= 3) throw IllegalStateException("Нет ответа телеметрии. Проверь колесо и подключись заново.")
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
                                is Work.Write -> {
                                    mutable.update { it.copy(writeInFlight=true) }
                                    if(!mayWrite(job.command.kind.tier, pending=true, ignorePending=true)) mutable.update { it.copy(writeMessage=
                                        if(job.command.kind.tier == V12Protocol.Tier.SAFE) "Не отправлено: связь устарела или запись запрещена."
                                        else "Не отправлено: колесо движется, связь устарела или запись запрещена.") }
                                    else {
                                        mutable.update { it.copy(writeMessage="Отправляю: ${job.command.label}") }
                                        send(link,write,job.command.bytes())
                                        val back = job.command.readBack
                                        if(back == null) mutable.update { it.copy(writeMessage="Отправлено: ${job.command.label}. У этой команды нет читаемого подтверждения — проверь результат на колесе.") }
                                        else {
                                            mutable.update { it.copy(writeMessage="Отправлено · проверяю ответ колеса…") }
                                            delay(200)
                                            // Read-back is read-only, so one retry on a lost answer is safe.
                                            var answer = optionalRead(link,write,back) ?: optionalRead(link,write,back)
                                            val confirmed = answer?.let { job.command.confirmed(it) }
                                            mutable.update { it.copy(writeMessage=if(confirmed == true) "Подтверждено колесом: ${job.command.label}" else
                                                "Изменение НЕ подтверждено. Проверь значения; автоматического повтора нет.") }
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
                    }
                    delay(Timing.POLL_DELAY_MS)
                }
            } catch (_: TimeoutCancellationException) { error("Время ожидания BLE истекло. Проверь колесо и попробуй снова.") }
            catch (e: CancellationException) { throw e }
            catch (_: SecurityException) { error("Разрешение Bluetooth отозвано.") }
            catch (e: Exception) { error(e.message ?: "Не удалось подключиться") }
            finally {
                runCatching { owned?.disconnect(); owned?.close() }
                if (gatt === owned) gatt = null
            }
        }
    }
    private suspend fun waitEvent(kind: String, timeout: Long = 6000) = withTimeout(timeout) {
        while (true) {
            val e = events.receive()
            if (e.kind == kind) { check(e.status == BluetoothGatt.GATT_SUCCESS) { "Ошибка BLE ${e.status}" }; break }
        }
    }
    private suspend fun ask(g: BluetoothGatt, c: BluetoothGattCharacteristic, read: V12Protocol.Read, timeout: Long = 2000): V12Protocol.Frame {
        while (responses.tryReceive().isSuccess) { }
        record(read,status="Ожидаю ответ…")
        send(g,c,V12Protocol.request(read))
        return withTimeout(timeout) {
            var found: V12Protocol.Frame? = null
            while (found == null) { val f = responses.receive(); if (read.matches(f)) found = f }
            found
        }
    }
    private suspend fun send(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray) {
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        awaitingWrite = type == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= 33) check(g.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS) { "BLE-запрос отклонён" }
        else {
            @Suppress("DEPRECATION")
            c.value = bytes
            c.writeType = type
            @Suppress("DEPRECATION")
            check(g.writeCharacteristic(c)) { "BLE-запрос отклонён" }
        }
        if (type == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) waitEvent("write") else delay(60)
        awaitingWrite = false
    }
    private suspend fun optionalRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, read: V12Protocol.Read): V12Protocol.Frame? {
        return try {
            val f=ask(g,c,read)
            if(read == V12Protocol.Read.MODEL) check(V12Protocol.model(f)?.isHt == true) { "Модель изменилась. Связь остановлена." }
            f
        } catch(_: TimeoutCancellationException) { record(read,status="Нет ответа · тайм-аут"); null }
    }
    fun disconnect() {
        clearWork(); stopScan(); session?.cancel(); session = null
        val old = gatt; gatt = null
        runCatching { old?.disconnect(); old?.close() }
        mutable.update { it.copy(phase = Phase.OFFLINE, message = "Колесо не подключено", verifiedHt = false, telemetry = null, settings = null, lastPacketAt = null, packets = 0, settingsAt = null, records = emptyMap(), workPending = false, writeMessage = null, stationaryPackets = 0) }
    }
    private fun error(text: String) {
        clearWork(); stopScan(); session?.cancel(); session = null
        val old = gatt; gatt = null
        runCatching { old?.disconnect(); old?.close() }
        mutable.update { it.copy(phase = Phase.ERROR, message = text, verifiedHt = false, workPending = false, stationaryPackets = 0, writeMessage=if(it.workPending && it.writeMessage!=null) "Связь прервана. Результат записи неизвестен; автоматического повтора нет." else it.writeMessage) }
    }
    fun onBackground() { if (mutable.value.phase !in listOf(Phase.OFFLINE, Phase.ERROR)) disconnect() }
}
