package com.freecyber.ion

import androidx.core.content.edit
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One SharedPreferences file, exposed as StateFlows so the UI, service and widgets observe the same values. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("vela_preferences", Context.MODE_PRIVATE)
    var onboardingDone: Boolean
        get() = prefs.getBoolean("connection_permissions_reviewed", false)
        set(value) { prefs.edit { putBoolean("connection_permissions_reviewed", value) } }
    var companionReconnectArmed: Boolean
        get() = prefs.getBoolean("companion_reconnect_armed", false)
        set(value) { prefs.edit { putBoolean("companion_reconnect_armed", value) } }

    private val darkState = MutableStateFlow(if (prefs.contains("dark")) prefs.getBoolean("dark", true) else null)
    val dark: StateFlow<Boolean?> = darkState.asStateFlow()
    fun setDark(value: Boolean?) {
        darkState.value = value
        prefs.edit { if (value == null) remove("dark") else putBoolean("dark", value) }
    }
    /** Material You: wallpaper colours on Android 12+, on by default. */
    private val dynamicState = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = dynamicState.asStateFlow()
    fun setDynamicColor(value: Boolean) { dynamicState.value = value; prefs.edit { putBoolean("dynamic_color", value) } }
    /** Seed of the generated scheme when wallpaper colours are off or unavailable. */
    private val paletteState = MutableStateFlow(IonPalette.of(prefs.getString("palette", null)))
    val palette: StateFlow<IonPalette> = paletteState.asStateFlow()
    fun setPalette(value: IonPalette) { paletteState.value = value; prefs.edit { putString("palette", value.name) } }

    private val alertState = MutableStateFlow(loadAlerts())
    val alerts: StateFlow<AlertConfig> = alertState.asStateFlow()
    private fun loadAlerts() = AlertConfig(
        prefs.getInt("alert_battery", 20).coerceIn(AlertConfig.BATTERY_RANGE),
        prefs.getInt("alert_temperature", 75).coerceIn(AlertConfig.TEMPERATURE_RANGE),
        prefs.getInt("alert_speed", 0).coerceIn(AlertConfig.SPEED_RANGE),
        prefs.getBoolean("alert_disconnect", true),
        prefs.getBoolean("alert_errors", true),
        prefs.getBoolean("alert_voice", false), prefs.getBoolean("alert_voice_battery", false),
        prefs.getInt("alert_charge", 0).coerceIn(AlertConfig.CHARGE_RANGE), prefs.getBoolean("alert_vibrate", false))
    fun setAlerts(value: AlertConfig) {
        alertState.value = value
        prefs.edit { putInt("alert_battery", value.batteryLowPercent).putInt("alert_temperature", value.temperatureHighC)
            .putInt("alert_speed", value.speedHighKmh).putBoolean("alert_disconnect", value.disconnect)
            .putBoolean("alert_errors", value.wheelErrors)
            .putBoolean("alert_voice", value.voice).putBoolean("alert_voice_battery", value.voiceBattery)
            .putInt("alert_charge", value.chargeTargetPercent).putBoolean("alert_vibrate", value.vibrate) }
    }

    private val backgroundState = MutableStateFlow(loadBackground())
    val background: StateFlow<BackgroundConfig> = backgroundState.asStateFlow()
    private fun loadBackground() = BackgroundConfig(
        true, prefs.getBoolean("bg_reconnect", true),
        prefs.getBoolean("bg_live_notification", false), prefs.getBoolean("keep_screen_on", false),
        prefs.getBoolean("bg_connect_on_launch", true), prefs.getBoolean("bg_gps_track", false),
        prefs.getInt("bg_reconnect_attempts", ReconnectPolicy.DEFAULT_ATTEMPTS))
    fun setBackground(value: BackgroundConfig) {
        backgroundState.value = value
        prefs.edit { putBoolean("bg_reconnect", value.autoReconnect)
            .putBoolean("bg_live_notification", value.liveNotification).putBoolean("keep_screen_on", value.keepScreenOn)
            .putBoolean("bg_connect_on_launch", value.connectOnLaunch).putBoolean("bg_gps_track", value.gpsTrack)
            .putInt("bg_reconnect_attempts", value.reconnectAttempts) }
    }

    private val updatesState = MutableStateFlow(UpdateConfig(
        UpdateChannel.entries.firstOrNull { it.name == prefs.getString("update_channel", null) } ?: UpdateChannel.STABLE,
        prefs.getBoolean("update_auto_check", true)))
    val updates: StateFlow<UpdateConfig> = updatesState.asStateFlow()
    fun setUpdates(value: UpdateConfig) {
        updatesState.value = value
        prefs.edit { putString("update_channel", value.channel.name).putBoolean("update_auto_check", value.autoCheck) }
    }
    private val updateCheckedState = MutableStateFlow(prefs.getLong("update_checked_at", 0L))
    val updateCheckedAt: StateFlow<Long> = updateCheckedState.asStateFlow()
    fun setUpdateCheckedAt(value: Long) { updateCheckedState.value = value; prefs.edit { putLong("update_checked_at", value) } }

    private val soundLabelsState = MutableStateFlow(SoundLabels.decode(prefs.getString("sound_labels", null)))
    val soundLabels: StateFlow<SoundLabels> = soundLabelsState.asStateFlow()
    fun setSoundLabels(value: SoundLabels) { soundLabelsState.value = value; prefs.edit { putString("sound_labels", value.encode()) } }

    private val lightDesignsState = MutableStateFlow(LightDesign.decodeAll(prefs.getString("light_designs", null)))
    val lightDesigns: StateFlow<List<LightDesign>> = lightDesignsState.asStateFlow()
    fun setLightDesigns(value: List<LightDesign>) { lightDesignsState.value = value; prefs.edit { putString("light_designs", LightDesign.encodeAll(value)) } }
    /** The personal effect last turned on, so the "personal" mode can be switched to without picking one again. */
    private val lightLastState = MutableStateFlow(prefs.getLong("light_last_effect", 0L).takeIf { it > 0 })
    val lightLast: StateFlow<Long?> = lightLastState.asStateFlow()
    fun setLightLast(id: Long) { lightLastState.value = id; prefs.edit { putLong("light_last_effect", id) } }

    private val dashboardState = MutableStateFlow(DashboardLayout.decode(prefs.getString("dashboard", null)))
    val dashboard: StateFlow<DashboardLayout> = dashboardState.asStateFlow()
    fun setDashboard(value: DashboardLayout) {
        dashboardState.value = value
        prefs.edit { putString("dashboard", value.encode()) }
    }
}
