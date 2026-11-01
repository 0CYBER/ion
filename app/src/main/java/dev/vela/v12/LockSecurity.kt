package dev.vela.v12

/** Experimental V12 HT security exchange, observed on one wheel only.
 * Never persist, print, or reuse the returned encrypted data between transactions.
 * Echo is an experimentally observed identity, not a universal security algorithm.
 */
internal object LockSecurity {
    fun keyRequest(): ByteArray = V12Protocol.frame(0x13, 0x02, byteArrayOf(0, 0, 0x02))
    fun keyFrom(f: V12Protocol.Frame): ByteArray? =
        if (f.flags == 0x12 && f.commandByte == 0x02 && f.payload.size == 18 &&
            f.payload[0] == 0x80.toByte() && f.payload[1] == 0x02.toByte())
            f.payload.copyOfRange(2, 18) else null
    fun verifyRequest(key: ByteArray): ByteArray {
        require(key.size == 16)
        return V12Protocol.frame(0x13, 0x02, byteArrayOf(0, 0, 0x82.toByte()) + key)
    }
    fun isVerifyReply(f: V12Protocol.Frame): Boolean =
        f.flags == 0x12 && f.commandByte == 0x02 &&
            f.payload.contentEquals(byteArrayOf(0x80.toByte(), 0x82.toByte(), 0x01))
    fun isRelevantReply(f: V12Protocol.Frame): Boolean =
        f.flags == 0x12 && f.commandByte == 0x02 && f.payload.size >= 2 &&
            f.payload[0] == 0x80.toByte() && (f.payload[1] == 0x02.toByte() || f.payload[1] == 0x82.toByte())
}
