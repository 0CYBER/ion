package com.freecyber.ion

/** Backoff for automatic reconnects: 2, 4, 8, 16, 32, then 60 s. The rider picks how many attempts
 * ([BackgroundConfig.reconnectAttempts], 3 by default); even "no limit" stops after [MAX_ATTEMPTS] (about 40 min),
 * so a wheel left switched off does not keep the radio busy forever. */
object ReconnectPolicy {
    const val MAX_ATTEMPTS = 40
    const val DEFAULT_ATTEMPTS = 3
    /** The choices offered in settings; 0 is "no limit". */
    val CHOICES = listOf(1, 3, 5, 10, 0)
    /** Attempts actually allowed for a configured value. */
    fun limit(configured: Int): Int = if (configured <= 0) MAX_ATTEMPTS else configured.coerceAtMost(MAX_ATTEMPTS)
    fun delayFor(attempt: Int): Long = (2_000L shl attempt.coerceIn(0, 5)).coerceAtMost(60_000L)
    fun shouldRetry(attempt: Int, limit: Int = MAX_ATTEMPTS) = attempt < limit

    sealed interface Decision {
        /** Stop any pending retry and clear the "reconnecting" state. */
        data object Cancel : Decision
        /** Leave the failure on screen; nothing is retried behind the user's back. */
        data object Show : Decision
        data class Retry(val attempt: Int, val delayMs: Long) : Decision
    }

    /** What to do when the link fails. [wasLive] means it dropped from an established ride link,
     * [retrying] that a previous automatic attempt failed, [restoringSavedWheel] that the rider
     * is reconnecting to an already verified device. */
    fun decide(userStopped: Boolean, hasDevice: Boolean, autoReconnect: Boolean, keepAlive: Boolean,
               wasLive: Boolean, retrying: Boolean, attempt: Int, restoringSavedWheel: Boolean = false,
               limit: Int = MAX_ATTEMPTS): Decision = when {
        userStopped || !hasDevice || !autoReconnect || !keepAlive -> Decision.Cancel
        // A failure while the user is still setting up a link is shown, not retried.
        !wasLive && !retrying && !restoringSavedWheel -> Decision.Show
        !shouldRetry(attempt, limit) -> Decision.Cancel
        else -> Decision.Retry(attempt, delayFor(attempt))
    }
}
