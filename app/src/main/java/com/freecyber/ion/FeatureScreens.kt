@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    DashTile.MODE -> R.string.tile_mode
    DashTile.CHART -> R.string.tile_chart
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
    DashTile.MODE -> Icons.Rounded.Tune
    DashTile.CHART -> Icons.Rounded.ShowChart
}

/** Lets the rider choose, order and size every dashboard module. Changes apply immediately. */
@Composable internal fun DashboardEditorScreen(layout: DashboardLayout,change: (DashboardLayout)->Unit,onBack: ()->Unit) {
    val shown=layout.visible
    val hidden=layout.slots.filterNot {it.visible}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {DetailHeading(stringResource(R.string.dash_editor_title),stringResource(R.string.dash_editor_hint),onBack)}
        item(key="reset",contentType="button") {
            OutlinedButton(onClick={change(DashboardLayout.DEFAULT)},enabled=layout!=DashboardLayout.DEFAULT,modifier=Modifier.heightIn(min=48.dp)) {
                Icon(Icons.Rounded.RestartAlt,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(stringResource(R.string.dash_reset))
            }
        }
        item(key="shown-title",contentType="section") {SectionHeader(stringResource(R.string.dash_shown))}
        if(shown.isEmpty()) item(key="shown-empty",contentType="note") {Banner(stringResource(R.string.dash_empty))}
        items(shown,key={"s-"+it.tile.name},contentType={"slot"}) {slot ->
            val index=shown.indexOf(slot)
            EditorRow(slot,first=index==0,last=index==shown.lastIndex,
                up={change(layout.moveVisible(slot.tile,-1))},down={change(layout.moveVisible(slot.tile,1))},
                visibility={change(layout.toggle(slot.tile))},resize={change(layout.resize(slot.tile))})
        }
        if(hidden.isNotEmpty()) {
            item(key="hidden-title",contentType="section") {SectionHeader(stringResource(R.string.dash_hidden))}
            items(hidden,key={"h-"+it.tile.name},contentType={"slot"}) {slot ->
                EditorRow(slot,first=true,last=true,up=null,down=null,visibility={change(layout.toggle(slot.tile))},resize=null)
            }
        }
    }
}

@Composable private fun EditorRow(slot: TileSlot,first: Boolean,last: Boolean,up: (()->Unit)?,down: (()->Unit)?,visibility: ()->Unit,resize: (()->Unit)?) {
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start=16.dp,end=6.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Icon(slot.tile.icon,null,Modifier.size(24.dp),tint=if(slot.visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(stringResource(slot.tile.titleRes),style=MaterialTheme.typography.titleMedium)
                // The size label doubles as the size switch, which keeps the row to three icon buttons.
                if(slot.visible) {
                    val label=stringResource(if(slot.effectiveWide) R.string.dash_wide else R.string.dash_half)
                    if(resize!=null && slot.tile.canBeHalf) FilterChip(selected=slot.wide,onClick=resize,label={Text(label)},
                        leadingIcon={Icon(if(slot.wide) Icons.Rounded.ViewStream else Icons.Rounded.GridView,null,Modifier.size(18.dp))})
                    else Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(up!=null) IconButton(onClick=up,enabled=!first) {Icon(Icons.Rounded.ArrowUpward,stringResource(R.string.dash_move_up))}
            if(down!=null) IconButton(onClick=down,enabled=!last) {Icon(Icons.Rounded.ArrowDownward,stringResource(R.string.dash_move_down))}
            IconButton(onClick=visibility) {
                Icon(if(slot.visible) Icons.Rounded.VisibilityOff else Icons.Rounded.AddCircle,
                    stringResource(if(slot.visible) R.string.dash_hide else R.string.dash_show))
            }
        }
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
@Composable private fun ThresholdSlider(label: String,value: Int,range: IntRange,describe: @Composable (Int)->String,commit: (Int)->Unit) {
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
            valueRange=range.first.toFloat()..range.last.toFloat())
    }
    }
}
