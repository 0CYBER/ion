package com.freecyber.ion

/**
 * The wheel's own password (INMOTION 8.1.34 "Vehicle settings → Password"), from the owner's logcat of the app turning a
 * PIN on, changing it and turning it off (2026-11-25, `docs/evidence/password-frames.txt`).
 *
 * Everything goes over the addressed bus to target 0x23:
 * - read the type: `0x0b` → reply `0x8b [type]` (0 off, 1 PIN; the app polls it every second);
 * - set the type: `0x4b [type]` → `0xcb [status]`; the app runs the lock's security exchange right before it;
 * - set the PIN: `0x4a [01, length, ASCII digits]` → `0xca [status]`; 4 or 6 digits, sent as plain text.
 * The PIN itself cannot be read back; only the type can. Neither step makes the wheel beep.
 */
object WheelPassword {
    const val TARGET = 0x23
    const val OFF = 0
    const val PIN = 1
    private const val READ = 0x0b
    private const val SET_TYPE = 0x4b
    private const val SET_PIN = 0x4a

    fun valid(pin: String) = (pin.length == 4 || pin.length == 6) && pin.all { it in '0'..'9' }

    fun readType(): ByteArray = LightEffects.message(0x16, TARGET, READ, byteArrayOf())
    fun setType(type: Int): ByteArray {
        require(type == OFF || type == PIN)
        return LightEffects.message(0x16, TARGET, SET_TYPE, byteArrayOf(type.toByte()))
    }
    fun setPin(pin: String): ByteArray {
        require(valid(pin))
        return LightEffects.message(0x16, TARGET, SET_PIN, byteArrayOf(0x01, pin.length.toByte()) + pin.toByteArray(Charsets.US_ASCII))
    }

    /** A reply from the password unit: [channel] carries the 0x80 reply bit, [data] follows it. */
    class Reply(val channel: Int, val data: ByteArray) {
        /** The password type when this answers [readType]. */
        val type: Int? get() = if (channel == READ or 0x80) data.firstOrNull()?.toInt()?.and(0xff) else null
        val typeSet get() = channel == SET_TYPE or 0x80 && data.firstOrNull()?.toInt() == 0
        val pinSet get() = channel == SET_PIN or 0x80 && data.firstOrNull()?.toInt() == 0
        fun answersType() = channel == SET_TYPE or 0x80
        fun answersPin() = channel == SET_PIN or 0x80
    }

    /** Frames from 0x23 to the phone (2): flags 0x16, the source in the command byte. */
    fun reply(f: V12Protocol.Frame): Reply? =
        if (f.flags == 0x16 && f.commandByte == TARGET && f.payload.size >= 2 && f.payload[0].toInt() == 0x02)
            Reply(f.payload[1].toInt() and 0xff, f.payload.copyOfRange(2, f.payload.size)) else null
}
