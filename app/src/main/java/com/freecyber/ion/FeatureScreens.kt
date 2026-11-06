@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.freecyber.ion

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Catalogue metadata for the editor; kept next to the editor so tiles stay pure render modules. */
internal val DashTile.titleRes: Int get() = when(this) {
    DashTile.SPEED -> R.string.tile_speed
    DashTile.BATTERY -> R.string.tile_battery
    DashTile.RANGE -> R.string.tile_range
    DashTile.QUICK_LIGHT -> R.string.tile_quick_light
    DashTile.QUICK_MUTE -> R.string.tile_quick_mute
    DashTile.TRIP_SESSION -> R.string.tile_trip_session
    DashTile.TRIP -> R.string.tile_trip
    DashTile.ODOMETER -> R.string.tile_odometer
    DashTile.VOLTAGE -> R.string.tile_voltage
    DashTile.CURRENT -> R.string.tile_current
    DashTile.POWER -> R.string.tile_power
    DashTile.MOTOR_TEMP -> R.string.tile_motor_temp
    DashTile.MOS_TEMP -> R.string.tile_mos_temp
    DashTile.PWM -> R.string.tile_pwm
    DashTile.TILT -> R.string.tile_tilt
    DashTile.QUICK_HIGH_BEAM -> R.string.tile_quick_high_beam
    DashTile.PLAY_SOUND -> R.string.tile_play_sound
    DashTile.QUICK_AUTO_LIGHT -> R.string.control_auto_light
    DashTile.QUICK_SOUND_WAVE -> R.string.control_sound_wave
    DashTile.MODE -> R.string.tile_mode
    DashTile.CHART -> R.string.tile_chart
    DashTile.TOGGLE -> R.string.tile_toggle
    DashTile.MAX_SPEED -> R.string.trip_max
    DashTile.AVG_SPEED -> R.string.trip_avg
    DashTile.ENERGY -> R.string.tile_energy
    DashTile.CONSUMPTION -> R.string.tile_consumption
}

internal val DashTile.icon: ImageVector get() = when(this) {
    DashTile.SPEED -> Icons.Rounded.Speed
    DashTile.BATTERY -> Icons.Rounded.BatteryFull
    DashTile.RANGE -> Icons.Rounded.Navigation
    DashTile.QUICK_LIGHT -> Icons.Rounded.Lightbulb
    DashTile.QUICK_MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
    DashTile.TRIP_SESSION -> Icons.Rounded.Timer
    DashTile.TRIP -> Icons.Rounded.Route
    DashTile.ODOMETER -> Icons.Rounded.Timeline
    DashTile.VOLTAGE -> Icons.Rounded.ElectricBolt
    DashTile.CURRENT -> Icons.Rounded.Waves
    DashTile.POWER -> Icons.Rounded.Bolt
    DashTile.MOTOR_TEMP -> Icons.Rounded.Thermostat
    DashTile.MOS_TEMP -> Icons.Rounded.Memory
    DashTile.PWM -> Icons.Rounded.Equalizer
    DashTile.TILT -> Icons.Rounded.ScreenRotation
    DashTile.QUICK_HIGH_BEAM -> Icons.Rounded.Highlight
    DashTile.PLAY_SOUND -> Icons.Rounded.Campaign
    DashTile.QUICK_AUTO_LIGHT -> Icons.Rounded.AutoAwesome
    DashTile.QUICK_SOUND_WAVE -> Icons.Rounded.MusicNote
    DashTile.MODE -> Icons.Rounded.Tune
    DashTile.CHART -> Icons.Rounded.ShowChart
    DashTile.TOGGLE -> Icons.Rounded.ToggleOn
    DashTile.MAX_SPEED -> Icons.Rounded.Speed
    DashTile.AVG_SPEED -> Icons.Rounded.AvTimer
    DashTile.ENERGY -> Icons.Rounded.Bolt
    DashTile.CONSUMPTION -> Icons.Rounded.Eco
}

/** Static sample telemetry so the dashboard editor previews every tile with realistic figures offline. */
internal object EditorSample {
    val ui: VelaUi by lazy {
        val p=ByteArray(66)
        fun u16(i: Int,v: Int) {p[i]=v.toByte();p[i+1]=(v ushr 8).toByte()}
        u16(0,9680); u16(2,1250); u16(4,2340); u16(6,125); u16(8,4200); u16(22,1234); u16(24,7800); u16(26,4120)
        p[40]=220.toByte(); p[41]=226.toByte(); p[55]=1
        val s=ByteArray(42); s[0]=0x20
        fun set(i: Int,v: Int) {s[i]=v.toByte();s[i+1]=(v ushr 8).toByte()}
        set(9,5000); set(11,3000); set(13,4000); set(17,600); s[20]=50; s[21]=60; s[22]=40; s[26]=70; s[27]=80; s[39]=9
        VelaUi(WheelState(phase=Phase.CONNECTED,message="",verifiedHt=true,telemetry=V12Protocol.telemetry(V12Protocol.Frame(0x14,0x84,p)),
            settings=V12Protocol.settings(V12Protocol.Frame(0x14,0xa0,s)),settingsAt=9500,lastPacketAt=9500,packets=128),
            WheelDevice("AA:BB:CC:DD:EE:FF","V12HT", -55),10_000,
            trip=TripStats(startedAt=1_000,lastAt=2_000_000,samples=4000,distanceKm=12.34,maxSpeed=41.2,movingMs=1_800_000,
                energyWh=380.0,regenWh=30.0,startBattery=96.0,lastBattery=78.0,maxTemp=50,maxPower=2900.0))
    }
}

/** Background link, live notification and alert thresholds in one place. */
@Composable internal fun BackgroundScreen(prefs: PrefsUi,actions: Actions,onBack: ()->Unit) {
    val bg=prefs.background; val al=prefs.alerts
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {DetailHeading(stringResource(R.string.bg_title),stringResource(R.string.bg_hint),onBack)}
        if(!prefs.notificationsAllowed) item(key="permission",contentType="banner") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Banner(stringResource(R.string.notif_permission_hint),Icons.Rounded.NotificationsOff,warning=true)
                ApplyButton(stringResource(R.string.notif_permission),true,actions.requestNotifications)
            }
        }
        if(!prefs.batteryUnrestricted && (bg.keepAlive || bg.autoReconnect)) item(key="battery",contentType="banner") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Banner(stringResource(R.string.battery_unrestricted_hint),Icons.Rounded.BatterySaver,warning=true)
                ApplyButton(stringResource(R.string.battery_unrestricted),true,actions.requestBatteryUnrestricted)
            }
        }
        if(!prefs.locationAllowed && bg.gpsTrack) item(key="location",contentType="banner") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Banner(stringResource(R.string.location_permission_hint),Icons.Rounded.LocationOff,warning=true)
                ApplyButton(stringResource(R.string.location_permission),true,actions.requestLocation)
            }
        }
        item(key="bg-title",contentType="section") {SectionHeader(stringResource(R.string.bg_section))}
        item(key="bg",contentType="group") {
            GroupColumn {
                SwitchRow(Icons.Rounded.Sync,stringResource(R.string.bg_keep_alive),bg.keepAlive,stringResource(R.string.bg_keep_alive_hint),true) {
                    actions.setBackground(bg.copy(keepAlive=it))}
                SwitchRow(Icons.Rounded.BluetoothSearching,stringResource(R.string.bg_connect_launch),bg.connectOnLaunch,stringResource(R.string.bg_connect_launch_hint),true) {
                    actions.setBackground(bg.copy(connectOnLaunch=it))}
                SwitchRow(Icons.Rounded.Autorenew,stringResource(R.string.bg_reconnect),bg.autoReconnect,stringResource(R.string.bg_reconnect_hint),true) {
                    actions.setBackground(bg.copy(autoReconnect=it))}
                SwitchRow(Icons.Rounded.Notifications,stringResource(R.string.bg_live),bg.liveNotification,stringResource(R.string.bg_live_hint),true) {
                    actions.setBackground(bg.copy(liveNotification=it))}
                SwitchRow(Icons.Rounded.LightMode,stringResource(R.string.bg_screen_on),bg.keepScreenOn,stringResource(R.string.bg_screen_on_hint),true) {
                    actions.setBackground(bg.copy(keepScreenOn=it))}
                SwitchRow(Icons.Rounded.LocationOn,stringResource(R.string.bg_gps),bg.gpsTrack,stringResource(R.string.bg_gps_hint),true) {
                    actions.setBackground(bg.copy(gpsTrack=it))}
            }
        }
        item(key="alerts-title",contentType="section") {SectionHeader(stringResource(R.string.alerts_section))}
        item(key="alerts",contentType="group") {
            GroupColumn {
                ThresholdSlider(stringResource(R.string.alert_setting_battery),al.batteryLowPercent,AlertConfig.BATTERY_RANGE,
                    {stringResource(R.string.alert_below,it)}) {actions.setAlerts(al.copy(batteryLowPercent=it))}
                ThresholdSlider(stringResource(R.string.alert_setting_temp),al.temperatureHighC,AlertConfig.TEMPERATURE_RANGE,
                    {stringResource(R.string.alert_above_c,it)}) {actions.setAlerts(al.copy(temperatureHighC=it))}
                ThresholdSlider(stringResource(R.string.alert_setting_speed),al.speedHighKmh,AlertConfig.SPEED_RANGE,
                    {stringResource(R.string.alert_above_kmh,it)}) {actions.setAlerts(al.copy(speedHighKmh=it))}
                ThresholdSlider(stringResource(R.string.alert_setting_charge),al.chargeTargetPercent,AlertConfig.CHARGE_RANGE,
                    {stringResource(R.string.alert_at_percent,it)},AlertConfig.CHARGE_RANGE.last/AlertConfig.CHARGE_STEP-1) {actions.setAlerts(al.copy(chargeTargetPercent=it))}
                SwitchRow(Icons.Rounded.LinkOff,stringResource(R.string.alert_setting_disconnect),al.disconnect,stringResource(R.string.alert_setting_disconnect_hint),true) {
                    actions.setAlerts(al.copy(disconnect=it))}
                SwitchRow(Icons.Rounded.ReportProblem,stringResource(R.string.alert_setting_errors),al.wheelErrors,stringResource(R.string.alert_setting_errors_hint),true) {
                    actions.setAlerts(al.copy(wheelErrors=it))}
                SwitchRow(Icons.Rounded.RecordVoiceOver,stringResource(R.string.alert_setting_voice),al.voice,stringResource(R.string.alert_setting_voice_hint),true) {
                    actions.setAlerts(al.copy(voice=it))}
                SwitchRow(Icons.Rounded.BatteryChargingFull,stringResource(R.string.alert_setting_voice_battery),al.voiceBattery,stringResource(R.string.alert_setting_voice_battery_hint),true) {
                    actions.setAlerts(al.copy(voiceBattery=it))}
            }
        }
    }
}

/** Slider that only commits on release, so dragging never spams preference writes. */
@Composable private fun ThresholdSlider(label: String,value: Int,range: IntRange,describe: @Composable (Int)->String,steps: Int=0,commit: (Int)->Unit) {
    var draft by remember(value) {mutableFloatStateOf(value.toFloat())}
    val shown=draft.roundToInt()
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xs)) {
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            Text(if(shown==0) stringResource(R.string.alert_off) else describe(shown),style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.primary)
        }
        Slider(value=draft,onValueChange={draft=it},onValueChangeFinished={commit(draft.roundToInt())},
            valueRange=range.first.toFloat()..range.last.toFloat(),steps=steps)
    }
    }
}
