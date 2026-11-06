package com.freecyber.ion

/** Backoff for automatic reconnects: 2, 4, 8, 16, 32, then 60 s; bounded attempts so a wheel left
 * switched off does not keep the radio busy forever. */
object ReconnectPolicy {
    const val MAX_ATTEMPTS = 40
    fun delayFor(attempt: Int): Long = (2_000L shl attempt.coerceIn(0, 5)).coerceAtMost(60_000L)
    fun shouldRetry(attempt: Int) = attempt < MAX_ATTEMPTS
}
