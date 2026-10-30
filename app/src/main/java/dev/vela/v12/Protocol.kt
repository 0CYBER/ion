package dev.vela.v12

import java.util.ArrayDeque

/** Offline-derived protocol profile; physical HT/firmware compatibility remains unverified.
 * Reference: WheelLog f44e39e..., GPL-3.0 (see THIRD_PARTY.md). No vendor code/assets.
 * Allowlisted accessory controls only; no ride tuning, firmware, calibration or unlock builders.
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
        val motorActive: Boolean = false, val firmwareUpdate: Boolean = false
    )
    fun telemetry(f: Frame): Telemetry? {
        if (f.flags != 0x14 || f.command != 4 || !f.response || f.payload.size < 56) return null
        val p = f.payload
        return Telemetry(p.u16(0)/100.0, p.s16(2)/100.0, p.s16(4)/100.0, p.u16(24)/100.0,
            p[40].u()-176, p[41].u()-176, p.s16(16)/100.0, p.s16(20)/100.0,
            p[54].u() and 0x80 != 0, p[55].u() and 1 != 0, p[55].u() and 2 != 0, p[54].u() and 0x40 != 0, p[55].u() and 0x20 != 0)
    }
    data class Settings(val speedLimit: Double, val comfortable: Int, val classic: Int, val standbySeconds: Int,
        val alarm1: Double, val alarm2: Double, val pedalTilt: Double, val classicMode: Boolean,
        val fancier: Boolean, val volume: Int, val lowBrightness: Int, val highBrightness: Int,
        val splitAccel: Int, val splitBrake: Int, val muted: Boolean, val handleEnabled: Boolean,
        val autoLight: Boolean, val transport: Boolean, val soundWave: Boolean, val splitMode: Boolean)
    fun settings(f: Frame): Settings? {
        if (f.flags != 0x14 || f.command != 0x20 || !f.response || f.payload.size < 42 || f.payload[0].u() != 0x20) return null
        val p = f.payload
        return Settings(p.u16(9)/100.0, p[20].u(), p[21].u(), p.u16(17),
            p.s16(11)/100.0, p.s16(13)/100.0, p.s16(15)/10.0, p[19].u() and 1 != 0,
            p[19].u() and 16 != 0, p[22].u(), p[26].u(), p[27].u(), p[31].u(), p[32].u(),
            p[39].u() and 1 == 0, p[39].u() and 4 != 0, p[39].u() and 8 != 0,
            p[39].u() and 64 != 0, p[40].u() and 4 != 0, p[41].u() and 1 != 0)
    }
    enum class Control { LIGHTS, BRIGHTNESS, AUTO_LIGHT, VOLUME, MUTE, SOUND_WAVE, STANDBY }
    data class Write(val kind: Control, val a: Int, val b: Int = 0) {
        init {
            when(kind) {
                Control.LIGHTS -> require(a in 0..1 && b in 0..1)
                Control.BRIGHTNESS -> require(a in 0..100 && b in 0..100)
                Control.VOLUME -> require(a in 0..100)
                Control.STANDBY -> require(a in 1..30)
                else -> require(a in 0..1)
            }
        }
        val readBack get() = if(kind == Control.LIGHTS) Read.TELEMETRY else Read.SETTINGS
        val label get() = when(kind) {
            Control.LIGHTS -> "Свет: ближний ${if(a==1) "вкл" else "выкл"}, дальний ${if(b==1) "вкл" else "выкл"}"
            Control.BRIGHTNESS -> "Яркость: ближний $a%, дальний $b%"
            Control.AUTO_LIGHT -> "Автосвет: ${if(a==1) "вкл" else "выкл"}"
            Control.VOLUME -> "Громкость: $a%"
            Control.MUTE -> "Без звука: ${if(a==1) "да" else "нет"}"
            Control.SOUND_WAVE -> "Звук движения: ${if(a==1) "вкл" else "выкл"}"
            Control.STANDBY -> "Ожидание: $a мин"
        }
        fun bytes(): ByteArray {
            val payload = when(kind) {
                Control.LIGHTS -> byteArrayOf(0x50, a.toByte(), b.toByte())
                Control.BRIGHTNESS -> byteArrayOf(0x2b, a.toByte(), b.toByte())
                Control.AUTO_LIGHT -> byteArrayOf(0x2f, a.toByte())
                Control.VOLUME -> byteArrayOf(0x26, a.toByte())
                Control.MUTE -> byteArrayOf(0x2c, (1-a).toByte())
                Control.SOUND_WAVE -> byteArrayOf(0x39, a.toByte())
                Control.STANDBY -> byteArrayOf(0x28, (a*60).toByte(), ((a*60) ushr 8).toByte())
            }
            return frame(0x14, 0x60, payload)
        }
        /** Only an authoritative read-back confirms success, never a GATT callback or guessed ACK. */
        fun confirmed(f: Frame): Boolean? {
            if(!readBack.matches(f)) return null
            if(kind == Control.LIGHTS) return telemetry(f)?.let { it.lowBeam == (a==1) && it.highBeam == (b==1) }
            val s = settings(f) ?: return null
            return when(kind) {
                Control.BRIGHTNESS -> s.lowBrightness == a && s.highBrightness == b
                Control.AUTO_LIGHT -> s.autoLight == (a==1)
                Control.VOLUME -> s.volume == a
                Control.MUTE -> s.muted == (a==1)
                Control.SOUND_WAVE -> s.soundWave == (a==1)
                Control.STANDBY -> s.standbySeconds == a*60
                else -> null
            }
        }
    }
    fun canWrite(verified: Boolean, connected: Boolean, ageMs: Long?, speed: Double?, charging: Boolean,
                 firmwareUpdate: Boolean, pending: Boolean): Boolean = verified && connected && !pending &&
        ageMs != null && ageMs in 0..1500 && speed != null && speed.isFinite() && kotlin.math.abs(speed) < 0.1 &&
        !charging && !firmwareUpdate

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
            add("mileageRaw","Пробег · raw u16[22]",p.u16(22).toString(),"Единицы не подтверждены на HT")
            add("battery","Батарея",n(t.battery,"%"),"Поле прошивки; на старых версиях возможны аномалии")
            add("rangeRaw","Остаток пути · raw u16[26]",p.u16(26).toString(),"Единицы не подтверждены на HT")
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
        }
        model(f)?.let { add("model","Модель",it.label); add("series","Серия","${it.series}"); add("variant","Вариант","${it.variant}") }
        return rows
    }
    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.u()) }
    fun raw(f: Frame): String = "flags=%02X command=%02X payload=%d B\n".format(f.flags,f.commandByte,f.payload.size) +
        f.payload.toList().chunked(8).mapIndexed { i, bytes -> "%03d: ".format(i*8) + hex(bytes.toByteArray()) }.joinToString("\n")
    private fun Byte.u() = toInt() and 255
    private fun ByteArray.u16(i: Int) = this[i].u() or (this[i+1].u() shl 8)
    private fun ByteArray.s16(i: Int) = u16(i).toShort().toInt()
}
