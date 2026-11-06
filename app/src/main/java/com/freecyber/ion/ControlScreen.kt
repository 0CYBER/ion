@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable internal fun ControlScreen(ui: VelaUi,category: ControlCategory,readSettings: ()->Unit,request: (V12Protocol.Write)->Unit,onBack: ()->Unit) {
    val t=ui.wheel.telemetry; val s=ui.wheel.settings
    val safe=ui.safeWrite
    val stopped=ui.stoppedWrite
    val settingsSafe=safe && ui.settingsFresh && s!=null
    val settingsStopped=stopped && ui.settingsFresh && s!=null
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item(key="intro",contentType="intro") {DetailHeading(stringResource(category.title),stringResource(category.subtitle),onBack)}
        if(!ui.connected) item(key="offline",contentType="banner") { Banner(stringResource(R.string.wheel_offline),Icons.Rounded.BluetoothDisabled) }
        else if(t?.firmwareUpdate==true) item(key="firmware",contentType="banner") { Banner(stringResource(R.string.firmware_busy),Icons.Rounded.Lock,warning=true) }
        // Only failures are reported; a confirmed change is visible from the control itself.
        ui.wheel.writeMessage?.let { message -> item(key="write-error",contentType="banner") { Banner(message,Icons.Rounded.ErrorOutline,error=true) } }
        if(category==ControlCategory.LIGHTS) {
            item(key="lights",contentType="controls") { GroupColumn {
                SwitchRow(Icons.Rounded.Lightbulb,stringResource(R.string.quick_light),t?.lowBeam,enabled=safe) { request(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(it)1 else 0,if(t?.highBeam==true)1 else 0)) }
                SwitchRow(Icons.Rounded.Highlight,stringResource(R.string.control_high_beam),t?.highBeam,enabled=safe) { request(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(t?.lowBeam==true)1 else 0,if(it)1 else 0)) }
                SwitchRow(Icons.Rounded.AutoAwesome,stringResource(R.string.control_auto_light),s?.autoLight,enabled=settingsSafe) { request(V12Protocol.Write(V12Protocol.Control.AUTO_LIGHT,if(it)1 else 0)) }
                ExpandRow(Icons.Rounded.BrightnessMedium,stringResource(R.string.control_brightness),s?.let { "${it.lowBrightness}% · ${it.highBrightness}%" } ?: "—") {
                    if(s!=null && s.lowBrightness in 0..100 && s.highBrightness in 0..100) {
                        var low by remember(s.lowBrightness,s.highBrightness) { mutableFloatStateOf(s.lowBrightness.toFloat()) }
                        var high by remember(s.lowBrightness,s.highBrightness) { mutableFloatStateOf(s.highBrightness.toFloat()) }
                        SettingSlider(stringResource(R.string.control_low),low,"${low.roundToInt()}%",0f..100f,settingsSafe) {low=it}
                        SettingSlider(stringResource(R.string.control_high),high,"${high.roundToInt()}%",0f..100f,settingsSafe) {high=it}
                        ApplyButton(stringResource(R.string.common_apply),settingsSafe && (low.roundToInt()!=s.lowBrightness || high.roundToInt()!=s.highBrightness)) {request(V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,low.roundToInt(),high.roundToInt()))}
                    } else MissingSetting(stringResource(R.string.no_data))
                }
            } }
        }
        if(category==ControlCategory.SOUND) {
            item(key="sound",contentType="controls") { GroupColumn {
                SwitchRow(Icons.Rounded.VolumeOff,stringResource(R.string.quick_mute),s?.muted,enabled=settingsSafe) {request(V12Protocol.Write(V12Protocol.Control.MUTE,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.MusicNote,stringResource(R.string.control_sound_wave),s?.soundWave,enabled=settingsSafe) {request(V12Protocol.Write(V12Protocol.Control.SOUND_WAVE,if(it)1 else 0))}
                ExpandRow(Icons.Rounded.VolumeUp,stringResource(R.string.control_volume),s?.let { "${it.volume}%" } ?: "—") {
                    if(s!=null && s.volume in 0..100) {
                        var volume by remember(s.volume) { mutableFloatStateOf(s.volume.toFloat()) }
                        SettingSlider(stringResource(R.string.control_volume),volume,"${volume.roundToInt()}%",0f..100f,settingsSafe) {volume=it}
                        ApplyButton(stringResource(R.string.common_apply),settingsSafe && volume.roundToInt()!=s.volume) {request(V12Protocol.Write(V12Protocol.Control.VOLUME,volume.roundToInt()))}
                    } else MissingSetting(stringResource(R.string.no_data))
                }
                SoundButtons(safe) {n -> request(V12Protocol.Write(V12Protocol.Control.PLAY_SOUND,n))}
            } }
        }
        if(category==ControlCategory.RIDE) {
            if(ui.connected && !settingsStopped) item(key="stationary-notice",contentType="banner") {Banner(stringResource(R.string.stationary_controls_hint),Icons.Rounded.Lock,warning=true)}
            item(key="modes",contentType="controls") { GroupColumn {
                SwitchRow(Icons.Rounded.Terrain,stringResource(R.string.control_off_road),s?.classicMode,hint=stringResource(R.string.control_classic_hint),enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.CLASSIC,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.Spa,stringResource(R.string.control_advanced),s?.fancier,hint=stringResource(R.string.control_advanced_hint),enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.FANCIER,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.CallSplit,stringResource(R.string.control_split),s?.splitMode,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.SPLIT_MODE,if(it)1 else 0))}
                ExpandRow(Icons.Rounded.SyncAlt,stringResource(R.string.control_split_tuning),s?.let { "${it.splitAccel} · ${it.splitBrake}" } ?: "—") {
                    if(s!=null) {
                        var accel by remember(s.splitAccel) { mutableFloatStateOf(s.splitAccel.coerceIn(0,100).toFloat()) }
                        var brake by remember(s.splitBrake) { mutableFloatStateOf(s.splitBrake.coerceIn(0,100).toFloat()) }
                        SettingSlider(stringResource(R.string.control_acceleration),accel,"${accel.roundToInt()}%",0f..100f,settingsStopped) {accel=it}
                        SettingSlider(stringResource(R.string.control_braking),brake,"${brake.roundToInt()}%",0f..100f,settingsStopped) {brake=it}
                        ApplyButton(stringResource(R.string.common_apply),settingsStopped && (accel.roundToInt()!=s.splitAccel || brake.roundToInt()!=s.splitBrake)) {request(V12Protocol.Write(V12Protocol.Control.SPLIT_ACCEL_BRAKE,accel.roundToInt(),brake.roundToInt()))}
                    } else MissingSetting(stringResource(R.string.no_data))
                }
            } }
        }
        if(category==ControlCategory.PEDALS) {
            item(key="pedals",contentType="controls") { GroupColumn {
                ExpandRow(Icons.Rounded.Tune,stringResource(R.string.control_sensitivity),s?.let { "${it.comfortable}%" } ?: "—") {
                    if(s!=null) {
                        var sens by remember(s.comfortable) { mutableFloatStateOf(s.comfortable.coerceIn(0,100).toFloat()) }
                        SettingSlider(stringResource(R.string.control_sensitivity_label),sens,"${sens.roundToInt()}%",0f..100f,settingsStopped) {sens=it}
                        ApplyButton(stringResource(R.string.common_apply),settingsStopped && sens.roundToInt()!=s.comfortable) {request(V12Protocol.Write(V12Protocol.Control.PEDAL_SENS,sens.roundToInt()))}
                    } else MissingSetting(stringResource(R.string.no_data))
                }
                ExpandRow(Icons.Rounded.Straighten,stringResource(R.string.control_pedal_tilt),s?.let { "${it.pedalTilt.fmt(1)}°" } ?: "—") {
                    if(s!=null) {
                        var tilt by remember(s.pedalTilt) { mutableFloatStateOf(s.pedalTilt.coerceIn(-10.0,10.0).toFloat()) }
                        SettingSlider(stringResource(R.string.control_tilt_label),tilt,"${tilt.toDouble().fmt(1)}°",-10f..10f,settingsStopped) {tilt=it}
                        // Write carries tenths of a degree, so 0.1° steps survive (no integer truncation).
                        ApplyButton(stringResource(R.string.common_apply),settingsStopped && kotlin.math.abs(tilt-s.pedalTilt)>=0.05f) {request(V12Protocol.Write(V12Protocol.Control.PEDAL_TILT,(tilt*10).roundToInt()))}
                    } else MissingSetting(stringResource(R.string.no_data))
                }
                ExpandRow(Icons.Rounded.NotificationsActive,stringResource(R.string.control_alarms),s?.let { "${it.alarm1.roundToInt()} · ${it.alarm2.roundToInt()} ${stringResource(R.string.unit_kmh)}" } ?: "—") {
                    if(s!=null) {
                        var alarm1 by remember(s.alarm1) { mutableFloatStateOf(s.alarm1.coerceIn(0.0,70.0).toFloat()) }
                        var alarm2 by remember(s.alarm2) { mutableFloatStateOf(s.alarm2.coerceIn(0.0,70.0).toFloat()) }
                        SettingSlider(stringResource(R.string.control_alarm1),alarm1,"${alarm1.roundToInt()} ${stringResource(R.string.unit_kmh)}",0f..70f,settingsStopped) {alarm1=it}
                        SettingSlider(stringResource(R.string.control_alarm2),alarm2,"${alarm2.roundToInt()} ${stringResource(R.string.unit_kmh)}",0f..70f,settingsStopped) {alarm2=it}
                        ApplyButton(stringResource(R.string.common_apply),settingsStopped && (alarm1.roundToInt()!=s.alarm1.roundToInt() || alarm2.roundToInt()!=s.alarm2.roundToInt())) {request(V12Protocol.Write(V12Protocol.Control.ALARMS,alarm1.roundToInt(),alarm2.roundToInt()))}
                    } else MissingSetting(stringResource(R.string.no_data))
                }
            } }
        }
        if(category==ControlCategory.SERVICE) {
            item(key="service",contentType="controls") { GroupColumn {
                SwitchRow(Icons.Rounded.TouchApp,stringResource(R.string.control_handle),s?.handleEnabled,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.HANDLE_BUTTON,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.LocalShipping,stringResource(R.string.control_transport),s?.transport,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.TRANSPORT,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.Lock,stringResource(R.string.control_lock),s?.locked,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.LOCK,if(it)1 else 0))}
                ExpandRow(Icons.Rounded.Timer,stringResource(R.string.control_standby),s?.let { "${(it.standbySeconds/60f).roundToInt()} ${stringResource(R.string.unit_min)}" } ?: "—") {
                    if(s!=null) {
                        var minutes by remember(s.standbySeconds) { mutableFloatStateOf((s.standbySeconds/60f).coerceIn(1f,30f)) }
                        SettingSlider(stringResource(R.string.control_timer_label),minutes,"${minutes.roundToInt()} ${stringResource(R.string.unit_min)}",1f..30f,settingsStopped) {minutes=it}
                        ApplyButton(stringResource(R.string.common_apply),settingsStopped && minutes.roundToInt()*60!=s.standbySeconds) {request(V12Protocol.Write(V12Protocol.Control.STANDBY,minutes.roundToInt()))}
                    } else MissingSetting(stringResource(R.string.no_data))
                }
            } }
        }
        item(key="read-settings",contentType="action") { OutlinedButton(onClick={readSettings()},enabled=ui.connected && !ui.wheel.workPending,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.control_read_settings),style=MaterialTheme.typography.labelLarge)} }
    }
}

