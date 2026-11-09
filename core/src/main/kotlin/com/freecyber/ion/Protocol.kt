package com.freecyber.ion


/** Offline-derived protocol profile; physical HT/firmware compatibility remains unverified.
 * Reference: WheelLog f44e39e..., GPL-3.0 (see THIRD_PARTY.md). No vendor code/assets.
 * Allowlisted accessory and settings controls only; no firmware, calibration or power-off builders.
 * All multi-byte values are little-endian on the wire (WheelLog getBytes is BE, then reversed).
 */
object V12Protocol {
    const val SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    /** How stale telemetry may be for STATIONARY writes: must cover one lost poll cycle (1.7 s worst case). */
    const val STATIONARY_AGE_WINDOW_MS = 2500L
    const val WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val NOTIFY = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"
    enum class Read(val flags: Int, val command: Int, val data: ByteArray) {
        MODEL(0x11, 0x02, byteArrayOf(1)),
        TELEMETRY(0x14, 0x04, byteArrayOf()),
        SETTINGS(0x14, 0x20, byteArrayOf(0x20)),
        SERIAL(0x11, 0x02, byteArrayOf(2)), VERSIONS(0x11, 0x02, byteArrayOf(6)),
        MAIN_VERSION(0x14, 0x01, byteArrayOf()), BATTERY(0x14, 0x05, byteArrayOf()),
        DIAGNOSTIC(0x14, 0x03, byteArrayOf()), STATISTICS(0x14, 0x11, byteArrayOf());
        val label get() = when(this) {
            MODEL -> tr("Модель","Model"); TELEMETRY -> tr("Телеметрия","Telemetry"); SETTINGS -> tr("Настройки","Settings")
            SERIAL -> tr("Серийный номер","Serial number"); VERSIONS -> tr("Версии модулей","Module versions")
            MAIN_VERSION -> tr("Основная версия","Main version"); BATTERY -> tr("Батарея / BMS","Battery / BMS")
            DIAGNOSTIC -> tr("Диагностика","Diagnostics"); STATISTICS -> tr("Статистика","Statistics")
        }
        fun matches(f: Frame): Boolean = f.response && f.flags == flags && f.command == command &&
            (this !in listOf(MODEL, SERIAL, VERSIONS) || f.payload.firstOrNull() == data.firstOrNull())
    }
    fun request(read: Read): ByteArray = frame(read.flags, read.command, read.data)
    fun frame(flags: Int, command: Int, data: ByteArray): ByteArray {
        require(flags in 0..255 && command in 0..255 && data.size <= 254)
        val body = byteArrayOf(flags.toByte(), (data.size + 1).toByte(), command.toByte()) + data
        val out = mutableListOf(0xaa.toByte(), 0xaa.toByte())
        body.forEach { b -> if (b.u() == 0xaa || b.u() == 0xa5) out.add(0xa5.toByte()); out.add(b) }
        out.add(body.fold(0) { a, b -> a xor b.u() }.toByte())
        return out.toByteArray()
    }
    data class Frame(val flags: Int, val commandByte: Int, val payload: ByteArray) {
        val command get() = commandByte and 0x7f
        val response get() = commandByte and 0x80 != 0
    }
    /** Body escaping + raw checksum, matching the published reference writer. Bounded 2KB queue. */
    class Decoder {
        // kotlin.collections.ArrayDeque: O(1) indexed reads, so a header candidate no longer copies the whole queue.
        private val buffer = ArrayDeque<Int>()
        var rejected = 0; private set
        fun reset() { buffer.clear(); rejected = 0 }
        @Synchronized fun feed(bytes: ByteArray): List<Frame> {
            val frames = mutableListOf<Frame>()
            bytes.forEach { buffer.addLast(it.u()) }
            if (buffer.size > 2048) { buffer.clear(); rejected++; return emptyList() }
            while (buffer.size >= 2) {
                if (buffer.first() != 0xaa || buffer[1] != 0xaa) { buffer.removeFirst(); continue }
                val list: List<Int> = buffer; val body = mutableListOf<Int>()
                var i = 2; var size = Int.MAX_VALUE; var invalid = false
                while (body.size < size) {
                    if (i >= list.size) return frames
                    var b = list[i++]
                    if (b == 0xa5) {
                        if (i >= list.size) return frames
                        b = list[i++]
                        if (b != 0xaa && b != 0xa5) { invalid = true; break }
                    }
                    body.add(b)
                    if (body.size == 2) {
                        if (body[1] == 0) { invalid = true; break }
                        size = body[1] + 2
                    }
                }
                if (invalid) { buffer.removeFirst(); rejected++; continue }
                if (i >= list.size) return frames
                if (body.fold(0) { a, b -> a xor b } != list[i++]) { buffer.removeFirst(); rejected++; continue }
                frames.add(Frame(body[0], body[2], body.drop(3).map(Int::toByte).toByteArray()))
                repeat(i) { buffer.removeFirst() }
            }
            return frames
        }
    }
    data class Model(val series: Int, val variant: Int) {
        val isHt get() = series == 7 && variant == 2
        val label get() = when { isHt -> "INMOTION V12 HT"; series == 7 && variant == 1 -> "V12 HS"; series == 7 && variant == 3 -> "V12 Pro"; else -> tr("Неизвестная модель ($series/$variant)","Unknown model ($series/$variant)") }
    }
    fun model(f: Frame): Model? = if (f.flags == 0x11 && f.command == 2 && f.response && f.payload.size >= 7 && f.payload[0].u() == 1)
        Model(f.payload[2].u(), f.payload[3].u()) else null
    data class Telemetry(
        val voltage: Double, val current: Double, val speed: Double, val battery: Double,
        val mosTemp: Int, val motorTemp: Int, val pitch: Double, val roll: Double,
        val charging: Boolean, val lowBeam: Boolean, val highBeam: Boolean,
        val motorActive: Boolean = false, val firmwareUpdate: Boolean = false,
        val tripKm: Double = 0.0, val rangeKm: Double = 0.0,
        /** Motor load in percent (s16[8]/100); the closer to 100, the less safety margin is left. */
        val pwm: Double = 0.0,
        val batteryPower: Int = 0, val motorPower: Int = 0
    ) {
        /** Electrical power from the pack; negative while regenerating. */
        val power: Double get() = voltage * current
    }
    fun telemetry(f: Frame): Telemetry? {
        if (f.flags != 0x14 || f.command != 4 || !f.response || f.payload.size < 56) return null
        val p = f.payload
        return Telemetry(p.u16(0)/100.0, p.s16(2)/100.0, p.s16(4)/100.0, p.u16(24)/100.0,
            p[40].u()-176, p[41].u()-176, p.s16(16)/100.0, p.s16(20)/100.0,
            p[54].u() and 0x80 != 0, p[55].u() and 1 != 0, p[55].u() and 2 != 0, p[54].u() and 0x40 != 0, p[55].u() and 0x20 != 0,
            p.u16(22)/100.0, p.u16(26)/100.0, p.s16(8)/100.0, p.s16(10), p.s16(12))
    }
    data class Settings(val speedLimit: Double, val comfortable: Int, val classic: Int, val standbySeconds: Int,
        val alarm1: Double, val alarm2: Double, val pedalTilt: Double, val classicMode: Boolean,
        val fancier: Boolean, val volume: Int, val lowBrightness: Int, val highBrightness: Int,
        val splitAccel: Int, val splitBrake: Int, val muted: Boolean, val handleEnabled: Boolean,
        val autoLight: Boolean, val transport: Boolean, val soundWave: Boolean, val splitMode: Boolean,
        val locked: Boolean = false)
    fun settings(f: Frame): Settings? {
        if (f.flags != 0x14 || f.command != 0x20 || !f.response || f.payload.size < 42 || f.payload[0].u() != 0x20) return null
        val p = f.payload
        return Settings(p.u16(9)/100.0, p[20].u(), p[21].u(), p.u16(17),
            p.s16(11)/100.0, p.s16(13)/100.0, p.s16(15)/100.0, p[19].u() and 1 != 0,
            p[19].u() and 16 != 0, p[22].u(), p[26].u(), p[27].u(), p[31].u(), p[32].u(),
            p[39].u() and 1 == 0, p[39].u() and 4 != 0, p[39].u() and 8 != 0,
            p[39].u() and 64 != 0, p[40].u() and 4 != 0, p[41].u() and 1 != 0,
            // bit5 of byte39: observed flipping exactly when the vendor app toggles the wheel lock.
            p[39].u() and 0x20 != 0)
    }
    /** WheelLog parseTotalStats (0x14/0x11): u32LE ×6; distance in 10 m units, energies /100, times in seconds. */
    data class Statistics(val totalKm: Double, val dischargedKwh: Double, val recoveredKwh: Double,
        val rideTimeSec: Long, val powerOnSec: Long, val extra: Long = -1)
    fun statistics(f: Frame): Statistics? {
        if (f.flags != 0x14 || f.command != 0x11 || !f.response || f.payload.size < 20) return null
        val p = f.payload
        return Statistics(u32(p,0)/100.0, u32(p,4)/100.0, u32(p,8)/100.0, u32(p,12), u32(p,16), if (p.size >= 24) u32(p,20) else -1)
    }
    /** WheelLog parseBatteryRealTimeInfo (0x14/0x05). Voltage/current scale /100 is unconfirmed on HT. */
    data class BatteryInfo(val bat1Voltage: Double, val bat1Temp: Int, val bat1Valid: Boolean, val bat1Enabled: Boolean, val bat1Work: Int,
        val bat2Voltage: Double, val bat2Temp: Int, val bat2Valid: Boolean, val bat2Enabled: Boolean, val bat2Work: Int,
        val chargeVoltage: Double, val chargeCurrent: Double)
    fun battery(f: Frame): BatteryInfo? {
        if (f.flags != 0x14 || f.command != 0x05 || !f.response || f.payload.size < 20) return null
        val p = f.payload
        return BatteryInfo(p.u16(0)/100.0, p[4].u(), p[5].u() and 1 != 0, p[5].u() and 2 != 0, p[6].u() and 3,
            p.u16(8)/100.0, p[12].u(), p[13].u() and 1 != 0, p[13].u() and 2 != 0, p[14].u() and 3, p.u16(16)/100.0, p.u16(18)/100.0)
    }
    /** WheelLog parseMainData key 0x02: 16 ASCII bytes after the selector. */
    fun serial(f: Frame): String? {
        if (f.flags != 0x11 || f.command != 0x02 || !f.response || f.payload.size < 17 || f.payload[0].u() != 2) return null
        val text = String(f.payload, 1, 16, Charsets.US_ASCII).filter { it.code in 0x20..0x7e }.trim()
        return text.ifBlank { null }
    }
    /** WheelLog parseMainData key 0x06: triplets (u16LE@2, b[4], b[5]) etc., printed X.Y.Z. */
    data class Versions(val driver: String, val main: String, val ble: String)
    fun versions(f: Frame): Versions? {
        if (f.flags != 0x11 || f.command != 0x02 || !f.response || f.payload.size < 24 || f.payload[0].u() != 6) return null
        val p = f.payload
        fun triple(lo16: Int, mid: Int, high: Int) = "${p[high].u()}.${p[mid].u()}.${p.u16(lo16)}"
        return Versions(triple(2,4,5), triple(11,13,14), triple(20,22,23))
    }
    /** WheelLog getError over payload[59..], byte 3 carries two 2-bit severity fields. Reference names kept verbatim. */
    fun errors(f: Frame): List<String> {
        val rest = f.payload.drop(59); val out = mutableListOf<String>()
        fun bit(i: Int, bit: Int, name: String) { val b = rest.getOrNull(i)?.u() ?: 0; if (name.isNotEmpty() && (b shr bit) and 1 == 1) out += name }
        intArrayOf(0,1,2,4,5).forEach { i ->
            for (bit in 0..7) bit(i, bit, SINGLE_BIT_ERRORS.getOrNull(i)?.getOrNull(bit) ?: "")
        }
        val b3 = rest.getOrNull(3)?.u() ?: 0
        if (b3 and 1 == 1) out += "err_underVoltageState"
        if (b3 and 2 == 2) out += "err_overVoltageState"
        val bus = (b3 shr 2) and 3; if (bus > 0) out += "err_overBusCurrentState-$bus"
        val low = (b3 shr 4) and 3; if (low > 0) out += "err_lowBatteryState-$low"
        if (b3 and 0x40 == 0x40) out += "err_mosTempState"
        if (b3 and 0x80 == 0x80) out += "err_motorTempState"
        return out
    }
    private val SINGLE_BIT_ERRORS = listOf(
        listOf("err_iPhaseSensorState","err_iBusSensorState","err_motorHallState","err_batteryState","err_imuSensorState","err_controllerCom1State","err_controllerCom2State","err_bleCom1State"),
        listOf("err_bleCom2State","err_mosTempSensorState","err_motorTempSensorState","err_batteryTempSensorState","err_boardTempSensorState","err_fanState","err_rtcState","err_externalRomState"),
        listOf("err_vBusSensorState","err_vBatterySensorState","err_canNotPowerOffState","err_notKnown1"),
        emptyList(),
        listOf("err_batteryTempState","err_overBoardTempState","err_overSpeedState","err_outputSaturationState","err_motorSpinState","err_motorBlockState","err_postureState","err_riskBehaviourState"),
        listOf("err_motorNoLoadState","err_noSelfTestState","err_compatibilityState","err_powerKeyLongPressState","err_forceDfuState","err_deviceLockState","err_cpuOverTempState","err_imuOverTempState"),
        listOf("","err_hwCompatibilityState","err_fanLowSpeedState","err_notKnown2"))
    enum class Tier { SAFE, STATIONARY }
    enum class Control(val tier: Tier) {
        LIGHTS(Tier.SAFE), BRIGHTNESS(Tier.SAFE), AUTO_LIGHT(Tier.SAFE),
        VOLUME(Tier.SAFE), MUTE(Tier.SAFE), SOUND_WAVE(Tier.SAFE), PLAY_SOUND(Tier.SAFE),
        CLASSIC(Tier.STATIONARY), FANCIER(Tier.STATIONARY), SPLIT_MODE(Tier.STATIONARY), SPLIT_ACCEL_BRAKE(Tier.STATIONARY),
        PEDAL_SENS(Tier.STATIONARY), PEDAL_TILT(Tier.STATIONARY), ALARMS(Tier.STATIONARY),
        HANDLE_BUTTON(Tier.STATIONARY), LOCK(Tier.STATIONARY), TRANSPORT(Tier.STATIONARY), STANDBY(Tier.STATIONARY)
    }
    data class Write(val kind: Control, val a: Int, val b: Int = 0) {
        init {
            when(kind) {
                Control.LIGHTS -> require(a in 0..1 && b in 0..1)
                Control.BRIGHTNESS -> require(a in 0..100 && b in 0..100)
                Control.VOLUME, Control.PEDAL_SENS, Control.SPLIT_ACCEL_BRAKE -> require(a in 0..100 && b in 0..100)
                Control.STANDBY -> require(a in 1..30)
                Control.PEDAL_TILT -> require(a in -100..100) // tenths of a degree
                Control.ALARMS -> require(a in 0..70 && b in 0..70)
                Control.PLAY_SOUND -> require(a in 0..15)
                else -> require(a in 0..1)
            }
        }
        /** Authoritative read-back target; null means the value is not observable in known reads. */
        val readBack: Read? get() = when(kind) {
            Control.LIGHTS -> Read.TELEMETRY
            Control.PLAY_SOUND -> null
            else -> Read.SETTINGS
        }
        val label: String get() {
            fun on(v: Int) = if(v==1) tr("вкл","on") else tr("выкл","off")
            return when(kind) {
                Control.LIGHTS -> tr("Свет: ближний ${on(a)}, дальний ${on(b)}","Lights: low ${on(a)}, high ${on(b)}")
                Control.BRIGHTNESS -> tr("Яркость: ближний $a%, дальний $b%","Brightness: low $a%, high $b%")
                Control.AUTO_LIGHT -> tr("Автосвет: ${on(a)}","Auto light: ${on(a)}")
                Control.VOLUME -> tr("Громкость: $a%","Volume: $a%")
                Control.MUTE -> if(a==1) tr("Без звука: да","Mute: yes") else tr("Без звука: нет","Mute: no")
                Control.SOUND_WAVE -> tr("Звук движения: ${on(a)}","Riding sound: ${on(a)}")
                Control.PLAY_SOUND -> tr("Проиграть звук $a","Play sound $a")
                Control.CLASSIC -> tr("Бездорожье: ${on(a)} (выкл = Комфорт)","Off-road: ${on(a)} (off = Comfort)")
                Control.FANCIER -> tr("Продвинутый: ${on(a)} (выкл = Нормальный)","Advanced: ${on(a)} (off = Normal)")
                Control.SPLIT_MODE -> "Split mode: ${on(a)}"
                Control.SPLIT_ACCEL_BRAKE -> tr("Split: ускорение $a, торможение $b","Split: acceleration $a, braking $b")
                Control.PEDAL_SENS -> tr("Чувствительность педалей: $a","Pedal sensitivity: $a")
                Control.PEDAL_TILT -> tr("Наклон педалей: ${(a/10.0).fmt(1)}°","Pedal tilt: ${(a/10.0).fmt(1)}°")
                Control.ALARMS -> tr("Пороги сигнала: $a и $b км/ч","Alarm thresholds: $a and $b km/h")
                Control.HANDLE_BUTTON -> tr("Кнопка ручки: ${on(a)}","Handle button: ${on(a)}")
                Control.LOCK -> tr("Блокировка колеса: ${on(a)}","Wheel lock: ${on(a)}")
                Control.TRANSPORT -> tr("Транспортный режим: ${on(a)}","Transport mode: ${on(a)}")
                Control.STANDBY -> tr("Ожидание: $a мин","Standby: $a min")
            }
        }
        fun bytes(): ByteArray {
            val payload = when(kind) {
                Control.LIGHTS -> byteArrayOf(0x50, a.toByte(), b.toByte())
                Control.BRIGHTNESS -> byteArrayOf(0x2b, a.toByte(), b.toByte())
                Control.AUTO_LIGHT -> byteArrayOf(0x2f, a.toByte())
                Control.VOLUME -> byteArrayOf(0x26, a.toByte())
                Control.MUTE -> byteArrayOf(0x2c, (1-a).toByte())
                Control.SOUND_WAVE -> byteArrayOf(0x39, a.toByte())
                Control.PLAY_SOUND -> byteArrayOf(0x51, a.toByte(), 0x01)
                Control.CLASSIC -> byteArrayOf(0x23, a.toByte())
                Control.FANCIER -> byteArrayOf(0x24, a.toByte())
                Control.SPLIT_MODE -> byteArrayOf(0x42, a.toByte())
                Control.SPLIT_ACCEL_BRAKE -> byteArrayOf(0x40, a.toByte(), b.toByte())
                Control.PEDAL_SENS -> byteArrayOf(0x25, a.toByte(), a.toByte())
                Control.PEDAL_TILT -> byteArrayOf(0x22) + le16(a*10) // tenths -> wire hundredths (1° = 100, same unit as settings[15])
                Control.ALARMS -> byteArrayOf(0x3e) + le16(a*100) + le16(b*100)
                Control.HANDLE_BUTTON -> byteArrayOf(0x2e, (1-a).toByte())
                Control.LOCK -> byteArrayOf(0x31, a.toByte())
                Control.TRANSPORT -> byteArrayOf(0x32, a.toByte())
                Control.STANDBY -> byteArrayOf(0x28) + le16(a*60)
            }
            // V12 HT lock frames observed in INMOTION 8.1.34 on this wheel. The
            // address can be session-dependent; this test path requires hardware verification.
            return if (kind == Control.LOCK) frame(0x16, 0x02, byteArrayOf(0x21, 0x60) + payload)
                   else frame(0x14, 0x60, payload)
        }
        /** Only an authoritative read-back confirms success, never a GATT callback or guessed ACK. */
        fun confirmed(f: Frame): Boolean? {
            val back = readBack ?: return null
            if(!back.matches(f)) return null
            if(kind == Control.LIGHTS) return telemetry(f)?.let { it.lowBeam == (a==1) && it.highBeam == (b==1) }
            val s = settings(f) ?: return null
            return when(kind) {
                Control.BRIGHTNESS -> s.lowBrightness == a && s.highBrightness == b
                Control.AUTO_LIGHT -> s.autoLight == (a==1)
                Control.VOLUME -> s.volume == a
                Control.MUTE -> s.muted == (a==1)
                Control.SOUND_WAVE -> s.soundWave == (a==1)
                Control.STANDBY -> s.standbySeconds == a*60
                Control.CLASSIC -> s.classicMode == (a==1)
                Control.FANCIER -> s.fancier == (a==1)
                Control.SPLIT_MODE -> s.splitMode == (a==1)
                Control.SPLIT_ACCEL_BRAKE -> s.splitAccel == a && s.splitBrake == b
                Control.PEDAL_SENS -> s.comfortable == a && s.classic == a
                Control.PEDAL_TILT -> kotlin.math.abs(s.pedalTilt - a/10.0) < 0.05
                Control.ALARMS -> kotlin.math.abs(s.alarm1 - a) < 0.005 && kotlin.math.abs(s.alarm2 - b) < 0.005
                Control.HANDLE_BUTTON -> s.handleEnabled == (a==1)
                Control.TRANSPORT -> s.transport == (a==1)
                Control.LOCK -> s.locked == (a==1)
                else -> null
            }
        }
    }
    private fun le16(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())
    /** SAFE tier: a verified connected wheel without a firmware update; telemetry age and motion are
     * not required, matching the vendor app. A live GATT link is enough — read-back still verifies. */
    fun canWriteSafe(verified: Boolean, connected: Boolean, firmwareUpdate: Boolean, pending: Boolean): Boolean =
        verified && connected && !pending && !firmwareUpdate
    /** STATIONARY tier adds stillness on top of the SAFE gate. Charging is allowed: a charging wheel is not moving.
     * The 0.5 km/h threshold tolerates balancing micro-corrections while standing, and the age window
     * spans one lost poll cycle (1.2 s ask + 0.5 s delay) so a telemetry gap never greys the controls. */
    fun canWrite(tier: Tier, verified: Boolean, connected: Boolean, ageMs: Long?, speed: Double?, charging: Boolean,
                 firmwareUpdate: Boolean, pending: Boolean): Boolean = canWriteSafe(verified, connected, firmwareUpdate, pending) &&
        (tier == Tier.SAFE || (ageMs != null && ageMs in 0..STATIONARY_AGE_WINDOW_MS && speed != null && speed.isFinite() && kotlin.math.abs(speed) < 0.5))

    data class Field(val key: String, val label: String, val value: String, val note: String = "")
    fun fields(f: Frame): List<Field> {
        val p = f.payload; val rows = mutableListOf<Field>()
        fun add(key: String, label: String, value: String, note: String = "") { rows += Field(key,label,value,note) }
        fun n(v: Double, unit: String) = String.format(IonFormat.locale, "%.2f %s",v,unit)
        fun yes(v: Boolean) = if(v) tr("Да","Yes") else tr("Нет","No")
        telemetry(f)?.let { t ->
            add("voltage", tr("Напряжение","Voltage"), n(t.voltage,tr("В","V"))); add("current",tr("Ток","Current"),n(t.current,tr("А","A")))
            add("speed",tr("Скорость","Speed"),n(t.speed,tr("км/ч","km/h"))); add("torque",tr("Крутящий момент","Torque"),n(p.s16(6)/100.0,tr("Н·м","N·m")))
            add("pwm","PWM",n(p.s16(8)/100.0,"%"))
            add("batteryPower",tr("Мощность батареи","Battery power"), "${p.s16(10)} ${tr("Вт","W")}"); add("motorPower",tr("Мощность мотора","Motor power"),"${p.s16(12)} ${tr("Вт","W")}")
            add("pitch",tr("Наклон","Pitch"),n(t.pitch,"°")); add("pitchAim",tr("Целевой наклон","Target pitch"),n(p.s16(18)/100.0,"°")); add("roll",tr("Крен","Roll"),n(t.roll,"°"))
            // u16[22]: distance of the current ride (since power-on), not the odometer — confirmed by the owner on HT.
            add("trip",tr("Дистанция поездки","Ride distance"),n(t.tripKm,tr("км","km")))
            add("battery",tr("Батарея","Battery"),n(t.battery,"%"))
            add("range",tr("Остаток пути","Range"),n(t.rangeKm,tr("км","km")))
            add("speedLimitDynamic",tr("Динамический лимит скорости","Dynamic speed limit"),n(p.u16(30)/100.0,tr("км/ч","km/h")))
            add("currentLimitDynamic",tr("Динамический лимит тока","Dynamic current limit"),n(p.u16(32)/100.0,tr("А","A")))
            // Offsets 42 (battery) and 46 (headlight) are not real sensors on HT and are intentionally not shown.
            listOf(0 to "MOS",1 to tr("Мотор","Motor"),3 to tr("Плата","Board"),4 to "CPU",5 to "IMU").forEach { (i,label) ->
                val temp=p[40+i].u()-176
                add("temp$i","${tr("Температура","Temperature")} · $label",if(temp in -50..180) "$temp °C" else "—")
            }
            add("pcMode","PC mode",(p[54].u() and 7).toString())
            add("mcMode","MC mode",((p[54].u() ushr 3) and 7).toString())
            add("motorActive",tr("Мотор активен","Motor active"),yes(t.motorActive)); add("charging",tr("Заряжается","Charging"),yes(t.charging))
            add("lowBeam",tr("Ближний свет","Low beam"),yes(t.lowBeam)); add("highBeam",tr("Дальний свет","High beam"),yes(t.highBeam))
            add("lifted",tr("Поднято","Lifted"),yes(p[55].u() and 4 != 0)); add("tailLight",tr("Задний свет","Tail light"),((p[55].u() ushr 3) and 3).toString())
            add("firmwareUpdate",tr("Обновление прошивки","Firmware update"),yes(t.firmwareUpdate))
            val errs = errors(f)
            if(errs.isNotEmpty()) add("errors",tr("Ошибки","Errors"),errs.joinToString(" "))
            if(p.size>59) add("errorsRaw",tr("Байты ошибок [59..]","Error bytes [59..]"),p.drop(59).joinToString(" ") { "%02X".format(it.u()) })
        }
        settings(f)?.let { s ->
            add("speedLimit",tr("Лимит скорости","Speed limit"),n(s.speedLimit,tr("км/ч","km/h"))); add("alarm1",tr("Порог сигнала 1","Alarm 1"),n(s.alarm1,tr("км/ч","km/h"))); add("alarm2",tr("Порог сигнала 2","Alarm 2"),n(s.alarm2,tr("км/ч","km/h")))
            add("pedalTilt",tr("Наклон педалей","Pedal tilt"),String.format(IonFormat.locale,"%.1f °",s.pedalTilt)); add("standby",tr("Ожидание","Standby"),"${s.standbySeconds} ${tr("с","s")}")
            add("classicMode",tr("Бездорожье","Off-road"),yes(s.classicMode)); add("fancier",tr("Продвинутый","Advanced"),yes(s.fancier))
            add("comfort",tr("Чувствительность · comfort","Sensitivity · comfort"),"${s.comfortable}"); add("classic",tr("Чувствительность · classic","Sensitivity · classic"),"${s.classic}")
            add("volume",tr("Громкость","Volume"),"${s.volume}%"); add("lowBrightness",tr("Яркость · ближний","Brightness · low"),"${s.lowBrightness}%"); add("highBrightness",tr("Яркость · дальний","Brightness · high"),"${s.highBrightness}%")
            add("splitAccel",tr("Split · ускорение","Split · acceleration"),"${s.splitAccel}"); add("splitBrake",tr("Split · торможение","Split · braking"),"${s.splitBrake}")
            add("mute",tr("Без звука","Mute"),yes(s.muted)); add("handle",tr("Кнопка ручки","Handle button"),yes(s.handleEnabled))
            add("autoLight",tr("Автосвет","Auto light"),yes(s.autoLight)); add("transport",tr("Транспортный режим","Transport mode"),yes(s.transport))
            add("soundWave",tr("Звук движения","Riding sound"),yes(s.soundWave)); add("splitMode","Split mode",yes(s.splitMode))
            add("locked",tr("Блокировка","Lock"),yes(s.locked))
        }
        model(f)?.let { add("model",tr("Модель","Model"),it.label); add("series",tr("Серия","Series"),"${it.series}"); add("variant",tr("Вариант","Variant"),"${it.variant}") }
        statistics(f)?.let { st ->
            add("statsTotal",tr("Одометр","Odometer"),n(st.totalKm,tr("км","km")))
            add("statsDischarged",tr("Энергия отдана","Energy discharged"),n(st.dischargedKwh,tr("кВт·ч","kWh")))
            add("statsRecovered",tr("Энергия рекуперации","Energy recovered"),n(st.recoveredKwh,tr("кВт·ч","kWh")))
            add("statsRide",tr("Время в поездке","Riding time"),formatDuration(st.rideTimeSec))
            add("statsPowerOn",tr("Время включения","Power-on time"),formatDuration(st.powerOnSec))
        }
        battery(f)?.let { b ->
            add("bat1v",tr("Батарея 1 · напряжение","Battery 1 · voltage"),n(b.bat1Voltage,tr("В","V")))
            add("bat1state",tr("Батарея 1 · состояние","Battery 1 · state"),tr("Валидна: ${yes(b.bat1Valid)} · Подключена: ${yes(b.bat1Enabled)} · Статус: ${b.bat1Work}","Valid: ${yes(b.bat1Valid)} · Enabled: ${yes(b.bat1Enabled)} · Status: ${b.bat1Work}"))
            add("bat2v",tr("Батарея 2 · напряжение","Battery 2 · voltage"),n(b.bat2Voltage,tr("В","V")))
            add("bat2state",tr("Батарея 2 · состояние","Battery 2 · state"),tr("Валидна: ${yes(b.bat2Valid)} · Подключена: ${yes(b.bat2Enabled)} · Статус: ${b.bat2Work}","Valid: ${yes(b.bat2Valid)} · Enabled: ${yes(b.bat2Enabled)} · Status: ${b.bat2Work}"))
            add("chargeV",tr("Зарядное напряжение","Charge voltage"),n(b.chargeVoltage,tr("В","V")))
        }
        serial(f)?.let { add("serial",tr("Серийный номер","Serial number"),it) }
        versions(f)?.let { v ->
            add("verMain","Main board",v.main); add("verDrv","Driver board",v.driver); add("verBle","BLE",v.ble)
        }
        if (f.flags == 0x14 && f.command == 0x03 && f.response && f.payload.isNotEmpty())
            add("diag",tr("Диагностика","Diagnostics"), if (f.payload.all { it.u() == 0 }) tr("Без ошибок","No errors") else tr("Есть ненулевые байты","Non-zero bytes present"))
        return rows
    }
    /** A negative span (clock moved back) reads as 0:00:00, not "-0:-1:-5". */
    fun formatDuration(sec: Long): String { val t=sec.coerceAtLeast(0); val h=t/3600; val m=(t/60)%60; val s=t%60; return "%d:%02d:%02d".format(h,m,s) }
    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.u()) }
    fun raw(f: Frame): String = "flags=%02X command=%02X payload=%d B\n".format(f.flags,f.commandByte,f.payload.size) +
        f.payload.toList().chunked(8).mapIndexed { i, bytes -> "%03d: ".format(i*8) + hex(bytes.toByteArray()) }.joinToString("\n")
    private fun u32(p: ByteArray, i: Int): Long = ((p[i].u() or (p[i+1].u() shl 8) or (p[i+2].u() shl 16) or (p[i+3].u() shl 24)).toLong() and 0xFFFFFFFFL)
    private fun Byte.u() = toInt() and 255
    private fun ByteArray.u16(i: Int) = this[i].u() or (this[i+1].u() shl 8)
    private fun ByteArray.s16(i: Int) = u16(i).toShort().toInt()
}
