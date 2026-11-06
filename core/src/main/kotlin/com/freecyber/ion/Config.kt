package com.freecyber.ion

/** User-tunable alert thresholds; 0 disables a numeric alert. Pure data so AlertEngine stays testable. */
data class AlertConfig(
    val batteryLowPercent: Int = 20,
    val temperatureHighC: Int = 75,
    val speedHighKmh: Int = 0,
    val disconnect: Boolean = true,
    val wheelErrors: Boolean = true,
    /** Speak alerts aloud (text-to-speech). */
    val voice: Boolean = false,
    /** Speak the battery level at every 10 % lost. */
    val voiceBattery: Boolean = false,
    /** Tell the rider when charging reaches this level (0 = off); many riders stop at 80-90 % to spare the pack. */
    val chargeTargetPercent: Int = 0,
) {
    companion object {
        val CHARGE_RANGE = 0..100
        const val CHARGE_STEP = 5
        val BATTERY_RANGE = 0..60
        val TEMPERATURE_RANGE = 0..100
        val SPEED_RANGE = 0..70
    }
}

/** Background behaviour; the defaults keep the link alive while the screen is off, as a companion app should. */
data class BackgroundConfig(
    val keepAlive: Boolean = true,
    val autoReconnect: Boolean = true,
    val liveNotification: Boolean = true,
    val keepScreenOn: Boolean = false,
    /** Reconnect to the saved wheel when the app is opened. */
    val connectOnLaunch: Boolean = true,
)
