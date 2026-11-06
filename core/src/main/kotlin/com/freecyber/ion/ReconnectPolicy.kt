package com.freecyber.ion

/** Backoff for automatic reconnects: 2, 4, 8, 16, 32, then 60 s; bounded attempts so a wheel left
 * switched off does not keep the radio busy forever. */
object ReconnectPolicy {
    const val MAX_ATTEMPTS = 40
    fun delayFor(attempt: Int): Long = (2_000L shl attempt.coerceIn(0, 5)).coerceAtMost(60_000L)
    fun shouldRetry(attempt: Int) = attempt < MAX_ATTEMPTS

    sealed interface Decision {
        /** Stop any pending retry and clear the "reconnecting" state. */
        data object Cancel : Decision
        /** Leave the failure on screen; nothing is retried behind the user's back. */
        data object Show : Decision
        data class Retry(val attempt: Int, val delayMs: Long) : Decision
    }

    /** What to do when the link fails. [wasLive] means it dropped from an established ride link,
     * [retrying] that a previous automatic attempt failed. */
    fun decide(userStopped: Boolean, hasDevice: Boolean, autoReconnect: Boolean, keepAlive: Boolean,
               wasLive: Boolean, retrying: Boolean, attempt: Int): Decision = when {
        userStopped || !hasDevice || !autoReconnect || !keepAlive -> Decision.Cancel
        // A failure while the user is still setting up a link is shown, not retried.
        !wasLive && !retrying -> Decision.Show
        !shouldRetry(attempt) -> Decision.Cancel
        else -> Decision.Retry(attempt, delayFor(attempt))
    }
}
