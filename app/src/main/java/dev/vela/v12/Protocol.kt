package dev.vela.v12

import java.util.ArrayDeque

/** Offline-derived protocol profile; physical HT/firmware compatibility remains unverified.
 * Reference: WheelLog f44e39e..., GPL-3.0 (see THIRD_PARTY.md). No vendor code/assets.
 * Allowlisted accessory and settings controls only; no firmware, calibration, power-off or unlock builders.
 * All multi-byte values are little-endian on the wire (WheelLog getBytes is BE, then reversed).
 */
object V12Protocol {
    const val SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
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
            MODEL -> "Модель"; TELEMETRY -> "Телеметрия"; SETTINGS -> "Настройки"; SERIAL -> "Серийный номер"
            VERSIONS -> "Версии модулей"; MAIN_VERSION -> "Основная версия"; BATTERY -> "Батарея / BMS"
            DIAGNOSTIC -> "Диагностика"; STATISTICS -> "Статистика"
        }
        fun matches(f: Frame): Boolean = f.response && f.flags == flags && f.command == command &&
            (this !in listOf(MODEL, SERIAL, VERSIONS) || f.payload.firstOrNull() == data.firstOrNull())
    }
    fun request(read: Read): ByteArray = frame(read.flags, read.command, read.data)
    internal fun frame(flags: Int, command: Int, data: ByteArray): ByteArray {
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
        private val buffer = ArrayDeque<Int>()
        var rejected = 0; private set
        fun reset() { buffer.clear(); rejected = 0 }
        @Synchronized fun feed(bytes: ByteArray): List<Frame> {
            val frames = mutableListOf<Frame>()
            bytes.forEach { buffer.addLast(it.u()) }
            if (buffer.size > 2048) { buffer.clear(); rejected++; return emptyList() }
            while (buffer.size >= 2) {
                if (buffer.first() != 0xaa || buffer.elementAt(1) != 0xaa) { buffer.removeFirst(); continue }
                val list = buffer.toList(); val body = mutableListOf<Int>()
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
        val label get() = when { isHt -> "INMOTION V12 HT"; series == 7 && variant == 1 -> "V12 HS"; series == 7 && variant == 3 -> "V12 Pro"; else -> "Неизвестная модель ($series/$variant)" }
    }
    fun model(f: Frame): Model? = if (f.flags == 0x11 && f.command == 2 && f.response && f.payload.size >= 7 && f.payload[0].u() == 1)
        Model(f.payload[2].u(), f.payload[3].u()) else null
    data class Telemetry(
        val voltage: Double, val current: Double, val speed: Double, val battery: Double,
        val mosTemp: Int, val motorTemp: Int, val pitch: Double, val roll: Double,
        val charging: Boolean, val lowBeam: Boolean, val highBeam: Boolean,
        val motorActive: Boolean = false, val firmwareUpdate: Boolean = false,
        val mileageKm: Double = 0.0, val rangeKm: Double = 0.0
    )
    fun telemetry(f: Frame): Telemetry? {
        if (f.flags != 0x14 || f.command != 4 || !f.response || f.payload.size < 56) return null
        val p = f.payload
        return Telemetry(p.u16(0)/100.0, p.s16(2)/100.0, p.s16(4)/100.0, p.u16(24)/100.0,
            p[40].u()-176, p[41].u()-176, p.s16(16)/100.0, p.s16(20)/100.0,
            p[54].u() and 0x80 != 0, p[55].u() and 1 != 0, p[55].u() and 2 != 0, p[54].u() and 0x40 != 0, p[55].u() and 0x20 != 0,
            p.u16(22)/100.0, p.u16(26)/100.0)
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
            p.s16(11)/100.0, p.s16(13)/100.0, p.s16(15)/10.0, p[19].u() and 1 != 0,
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
        LIGHTS(Tier.SAFE), BRIGHTNESS(Tier.SAFE), AUTO_LIGHT(Tier.SAFE), DRL(Tier.SAFE),
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
                Control.PEDAL_TILT -> require(a in -10..10)
                Control.ALARMS -> require(a in 0..70 && b in 0..70)
                Control.PLAY_SOUND -> require(a in 0..15)
                else -> require(a in 0..1)
            }
        }
        /** Authoritative read-back target; null means the value is not observable in known reads. */
        val readBack: Read? get() = when(kind) {
            Control.LIGHTS -> Read.TELEMETRY
            Control.DRL, Control.PLAY_SOUND -> null
            else -> Read.SETTINGS
        }
        val label get() = when(kind) {
            Control.LIGHTS -> "Свет: ближний ${if(a==1) "вкл" else "выкл"}, дальний ${if(b==1) "вкл" else "выкл"}"
            Control.BRIGHTNESS -> "Яркость: ближний $a%, дальний $b%"
            Control.AUTO_LIGHT -> "Автосвет: ${if(a==1) "вкл" else "выкл"}"
            Control.DRL -> "Дневной свет (DRL): ${if(a==1) "вкл" else "выкл"}"
            Control.VOLUME -> "Громкость: $a%"
            Control.MUTE -> "Без звука: ${if(a==1) "да" else "нет"}"
            Control.SOUND_WAVE -> "Звук движения: ${if(a==1) "вкл" else "выкл"}"
            Control.PLAY_SOUND -> "Проиграть звук $a"
            Control.CLASSIC -> "Режим: ${if(a==1) "классический" else "fancier"}"
            Control.FANCIER -> "Режим: ${if(a==1) "fancier" else "классический"}"
            Control.SPLIT_MODE -> "Split mode: ${if(a==1) "вкл" else "выкл"}"
            Control.SPLIT_ACCEL_BRAKE -> "Split: ускорение $a, торможение $b"
            Control.PEDAL_SENS -> "Чувствительность педалей: $a"
            Control.PEDAL_TILT -> "Наклон педалей: $a°"
            Control.ALARMS -> "Пороги сигнала: ${a} и ${b} км/ч"
            Control.HANDLE_BUTTON -> "Кнопка ручки: ${if(a==1) "вкл" else "выкл"}"
            Control.LOCK -> "Блокировка колеса: ${if(a==1) "вкл" else "выкл"}"
            Control.TRANSPORT -> "Транспортный режим: ${if(a==1) "вкл" else "выкл"}"
            Control.STANDBY -> "Ожидание: $a мин"
        }
        fun bytes(): ByteArray {
            val payload = when(kind) {
                Control.LIGHTS -> byteArrayOf(0x50, a.toByte(), b.toByte())
                Control.BRIGHTNESS -> byteArrayOf(0x2b, a.toByte(), b.toByte())
                Control.AUTO_LIGHT -> byteArrayOf(0x2f, a.toByte())
                Control.DRL -> byteArrayOf(0x2d, a.toByte())
                Control.VOLUME -> byteArrayOf(0x26, a.toByte())
                Control.MUTE -> byteArrayOf(0x2c, (1-a).toByte())
                Control.SOUND_WAVE -> byteArrayOf(0x39, a.toByte())
                Control.PLAY_SOUND -> byteArrayOf(0x51, a.toByte(), 0x01)
                Control.CLASSIC -> byteArrayOf(0x23, a.toByte())
                Control.FANCIER -> byteArrayOf(0x24, a.toByte())
                Control.SPLIT_MODE -> byteArrayOf(0x42, a.toByte())
                Control.SPLIT_ACCEL_BRAKE -> byteArrayOf(0x40, a.toByte(), b.toByte())
                Control.PEDAL_SENS -> byteArrayOf(0x25, a.toByte(), a.toByte())
                Control.PEDAL_TILT -> byteArrayOf(0x22) + le16(a*10)
                Control.ALARMS -> byteArrayOf(0x3e) + le16(a*100) + le16(b*100)
                Control.HANDLE_BUTTON -> byteArrayOf(0x2e, (1-a).toByte())
                Control.LOCK -> byteArrayOf(0x31, a.toByte())
                Control.TRANSPORT -> byteArrayOf(0x32, a.toByte())
                Control.STANDBY -> byteArrayOf(0x28) + le16(a*60)
            }
            return frame(0x14, 0x60, payload)
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
                Control.PEDAL_TILT -> kotlin.math.abs(s.pedalTilt - a) < 0.05
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
    /** STATIONARY tier adds stillness on top of the SAFE gate. Charging is allowed: a charging wheel is not moving. */
    fun canWrite(tier: Tier, verified: Boolean, connected: Boolean, ageMs: Long?, speed: Double?, charging: Boolean,
                 firmwareUpdate: Boolean, pending: Boolean): Boolean = canWriteSafe(verified, connected, firmwareUpdate, pending) &&
        (tier == Tier.SAFE || (ageMs != null && ageMs in 0..1500 && speed != null && speed.isFinite() && kotlin.math.abs(speed) < 0.1))

    data class Field(val key: String, val label: String, val value: String, val note: String = "")
    fun fields(f: Frame): List<Field> {
        val p = f.payload; val rows = mutableListOf<Field>()
        fun add(key: String, label: String, value: String, note: String = "") { rows += Field(key,label,value,note) }
        fun n(v: Double, unit: String) = String.format(java.util.Locale.forLanguageTag("ru"), "%.2f %s",v,unit)
        fun yes(v: Boolean) = if(v) "Да" else "Нет"
        telemetry(f)?.let { t ->
            add("voltage", "Напряжение", n(t.voltage,"В")); add("current","Ток",n(t.current,"А"))
            add("speed","Скорость",n(t.speed,"км/ч")); add("torque","Крутящий момент",n(p.s16(6)/100.0,"Н·м"))
            add("pwm","PWM · значение протокола",n(p.s16(8)/100.0,"%"),"Не оценка запаса безопасности")
            add("batteryPower","Мощность батареи", "${p.s16(10)} Вт"); add("motorPower","Мощность мотора","${p.s16(12)} Вт")
            add("pitch","Наклон",n(t.pitch,"°")); add("pitchAim","Целевой наклон",n(p.s16(18)/100.0,"°")); add("roll","Крен",n(t.roll,"°"))
            add("mileage","Пробег · поле телеметрии",n(t.mileageKm,"км"),"WheelLog трактует u16[22] как общий одометр, но на HT значение расходится со статистикой — семантика не подтверждена")
            add("battery","Батарея",n(t.battery,"%"),"Поле прошивки; на старых версиях возможны аномалии")
            add("range","Остаток пути",n(t.rangeKm,"км"),"WheelLog: u16[26] в единицах 10 м; единицы подтверди на своей HT")
            add("speedLimitDynamic","Динамический лимит скорости",n(p.u16(30)/100.0,"км/ч"))
            add("currentLimitDynamic","Динамический лимит тока",n(p.u16(32)/100.0,"А"))
            listOf("MOS","Мотор","Батарея","Плата","CPU","IMU","Фара").forEachIndexed { i,label ->
                val temp=p[40+i].u()-176
                add("temp$i","Температура · $label",if(temp in -50..180) "$temp °C" else "Нет корректного значения · raw %02X".format(p[40+i].u()),
                    if(i in listOf(2,3,6)) "Служебное поле; датчик может отсутствовать" else "")
            }
            add("pcMode","PC mode · raw",(p[54].u() and 7).toString())
            add("mcMode","MC mode · raw",((p[54].u() ushr 3) and 7).toString())
            add("motorActive","Мотор активен",yes(t.motorActive)); add("charging","Заряжается",yes(t.charging))
            add("lowBeam","Ближний свет",yes(t.lowBeam)); add("highBeam","Дальний свет",yes(t.highBeam))
            add("lifted","Поднято",yes(p[55].u() and 4 != 0)); add("tailLight","Задний свет · raw",((p[55].u() ushr 3) and 3).toString())
            add("firmwareUpdate","Обновление прошивки",yes(t.firmwareUpdate))
            val errs = errors(f)
            if(errs.isNotEmpty()) add("errors","Ошибки",errs.joinToString(" "),"Имена — WheelLog; диагноз не ставится")
            if(p.size>59) add("errorsRaw","Байты ошибок [59..]",p.drop(59).joinToString(" ") { "%02X".format(it.u()) },"Битовые значения не интерпретируются как диагноз")
        }
        settings(f)?.let { s ->
            add("speedLimit","Лимит скорости",n(s.speedLimit,"км/ч")); add("alarm1","Порог сигнала 1",n(s.alarm1,"км/ч")); add("alarm2","Порог сигнала 2",n(s.alarm2,"км/ч"))
            add("pedalTilt","Наклон педалей",n(s.pedalTilt,"°")); add("standby","Ожидание","${s.standbySeconds} с")
            add("classicMode","Классический режим",yes(s.classicMode)); add("fancier","Fancier mode",yes(s.fancier))
            add("comfort","Чувствительность · comfort","${s.comfortable}"); add("classic","Чувствительность · classic","${s.classic}")
            add("volume","Громкость","${s.volume}%"); add("lowBrightness","Яркость · ближний","${s.lowBrightness}%"); add("highBrightness","Яркость · дальний","${s.highBrightness}%")
            add("splitAccel","Split · ускорение","${s.splitAccel}"); add("splitBrake","Split · торможение","${s.splitBrake}")
            add("mute","Без звука",yes(s.muted)); add("handle","Кнопка ручки включена",yes(s.handleEnabled))
            add("autoLight","Автосвет",yes(s.autoLight)); add("transport","Транспортный режим",yes(s.transport))
            add("soundWave","Звук движения",yes(s.soundWave)); add("splitMode","Split mode",yes(s.splitMode))
            add("locked","Блокировка (замок)",yes(s.locked),"Бит 5 байта 39; найден через before/after-дифф с офф. приложением")
        }
        model(f)?.let { add("model","Модель",it.label); add("series","Серия","${it.series}"); add("variant","Вариант","${it.variant}") }
        statistics(f)?.let { st ->
            add("statsTotal","Пробег (одометр)",n(st.totalKm,"км"),"WheelLog: u32LE[0] в единицах 10 м")
            add("statsDischarged","Энергия отдана",n(st.dischargedKwh,"кВт·ч"),"WheelLog: u32LE[4]; единица не подтверждена")
            add("statsRecovered","Энергия рекуперации",n(st.recoveredKwh,"кВт·ч"),"WheelLog: u32LE[8]; единица не подтверждена")
            add("statsRide","Время в поездке",formatDuration(st.rideTimeSec),"WheelLog: u32LE[12], секунды")
            add("statsPowerOn","Время включения",formatDuration(st.powerOnSec),"WheelLog: u32LE[16], секунды")
            if(st.extra >= 0) add("statsExtra","Поле [20] · raw u32","${st.extra}","Шестое поле статистики; смысл неизвестен")
        }
        battery(f)?.let { b ->
            val scaleNote="Масштаб /100 В по аналогии с телеметрией; не подтверждён на HT"
            add("bat1v","Батарея 1 · напряжение",n(b.bat1Voltage,"В"),scaleNote); add("bat1t","Батарея 1 · температура",tempOrRaw(b.bat1Temp))
            add("bat1state","Батарея 1 · состояние","Валидна: ${yes(b.bat1Valid)} · Подключена: ${yes(b.bat1Enabled)} · Статус работы: ${b.bat1Work}")
            add("bat2v","Батарея 2 · напряжение",n(b.bat2Voltage,"В"),scaleNote); add("bat2t","Батарея 2 · температура",tempOrRaw(b.bat2Temp))
            add("bat2state","Батарея 2 · состояние","Валидна: ${yes(b.bat2Valid)} · Подключена: ${yes(b.bat2Enabled)} · Статус работы: ${b.bat2Work}")
            add("chargeV","Зарядное напряжение",n(b.chargeVoltage,"В"),scaleNote); add("chargeI","Зарядный ток",n(b.chargeCurrent,"А"),scaleNote)
        }
        serial(f)?.let { add("serial","Серийный номер",it,"WheelLog: 16 ASCII-байт после селектора") }
        versions(f)?.let { v ->
            add("verMain","Main board",v.main,"WheelLog: ключ 06 · формат X.Y.Z")
            add("verDrv","Driver board",v.driver); add("verBle","BLE",v.ble)
        }
        if (f.flags == 0x14 && f.command == 0x03 && f.response && f.payload.isNotEmpty())
            add("diag","Диагностика", if (f.payload.all { it.u() == 0 }) "Без ошибок · все байты 00" else "Есть ненулевые байты · смотри raw","WheelLog: все нули = ok; расшифровки нет")
        return rows
    }
    private fun tempOrRaw(raw: Int) = if (raw in -50..200) "$raw °C" else "raw $raw"
    fun formatDuration(sec: Long): String { val h=sec/3600; val m=(sec/60)%60; val s=sec%60; return "%d:%02d:%02d".format(h,m,s) }
    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.u()) }
    fun raw(f: Frame): String = "flags=%02X command=%02X payload=%d B\n".format(f.flags,f.commandByte,f.payload.size) +
        f.payload.toList().chunked(8).mapIndexed { i, bytes -> "%03d: ".format(i*8) + hex(bytes.toByteArray()) }.joinToString("\n")
    private fun u32(p: ByteArray, i: Int): Long = ((p[i].u() or (p[i+1].u() shl 8) or (p[i+2].u() shl 16) or (p[i+3].u() shl 24)).toLong() and 0xFFFFFFFFL)
    private fun Byte.u() = toInt() and 255
    private fun ByteArray.u16(i: Int) = this[i].u() or (this[i+1].u() shl 8)
    private fun ByteArray.s16(i: Int) = u16(i).toShort().toInt()
}
