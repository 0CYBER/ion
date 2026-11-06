package com.freecyber.ion

import androidx.core.content.edit
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) {
    companion object {
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

/** One SharedPreferences file, exposed as StateFlows so the UI, service and widgets observe the same values. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("vela_preferences", Context.MODE_PRIVATE)

    private val darkState = MutableStateFlow(if (prefs.contains("dark")) prefs.getBoolean("dark", true) else null)
    val dark: StateFlow<Boolean?> = darkState.asStateFlow()
    fun setDark(value: Boolean?) {
        darkState.value = value
        prefs.edit { if (value == null) remove("dark") else putBoolean("dark", value) }
    }

    private val alertState = MutableStateFlow(loadAlerts())
    val alerts: StateFlow<AlertConfig> = alertState.asStateFlow()
    private fun loadAlerts() = AlertConfig(
        prefs.getInt("alert_battery", 20).coerceIn(AlertConfig.BATTERY_RANGE),
        prefs.getInt("alert_temperature", 75).coerceIn(AlertConfig.TEMPERATURE_RANGE),
        prefs.getInt("alert_speed", 0).coerceIn(AlertConfig.SPEED_RANGE),
        prefs.getBoolean("alert_disconnect", true),
        prefs.getBoolean("alert_errors", true),
        prefs.getBoolean("alert_voice", false), prefs.getBoolean("alert_voice_battery", false))
    fun setAlerts(value: AlertConfig) {
        alertState.value = value
        prefs.edit { putInt("alert_battery", value.batteryLowPercent).putInt("alert_temperature", value.temperatureHighC)
            .putInt("alert_speed", value.speedHighKmh).putBoolean("alert_disconnect", value.disconnect)
            .putBoolean("alert_errors", value.wheelErrors)
            .putBoolean("alert_voice", value.voice).putBoolean("alert_voice_battery", value.voiceBattery) }
    }

    private val backgroundState = MutableStateFlow(loadBackground())
    val background: StateFlow<BackgroundConfig> = backgroundState.asStateFlow()
    private fun loadBackground() = BackgroundConfig(
        prefs.getBoolean("bg_keep_alive", true), prefs.getBoolean("bg_reconnect", true),
        prefs.getBoolean("bg_live_notification", true), prefs.getBoolean("keep_screen_on", false),
        prefs.getBoolean("bg_connect_on_launch", true))
    fun setBackground(value: BackgroundConfig) {
        backgroundState.value = value
        prefs.edit { putBoolean("bg_keep_alive", value.keepAlive).putBoolean("bg_reconnect", value.autoReconnect)
            .putBoolean("bg_live_notification", value.liveNotification).putBoolean("keep_screen_on", value.keepScreenOn)
            .putBoolean("bg_connect_on_launch", value.connectOnLaunch) }
    }

    private val dashboardState = MutableStateFlow(DashboardLayout.decode(prefs.getString("dashboard", null)))
    val dashboard: StateFlow<DashboardLayout> = dashboardState.asStateFlow()
    fun setDashboard(value: DashboardLayout) {
        dashboardState.value = value
        prefs.edit { putString("dashboard", value.encode()) }
    }
}
