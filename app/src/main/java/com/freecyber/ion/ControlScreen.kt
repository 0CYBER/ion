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
                ExpandSliders(Icons.Rounded.BrightnessMedium,stringResource(R.string.control_brightness),
                    s?.let { "${it.lowBrightness}% · ${it.highBrightness}%" } ?: "—",settingsSafe,
                    s?.takeIf { it.lowBrightness in 0..100 && it.highBrightness in 0..100 }?.let {
                        listOf(SliderSpec(stringResource(R.string.control_low),it.lowBrightness,0..100) {v -> "$v%"},
                            SliderSpec(stringResource(R.string.control_high),it.highBrightness,0..100) {v -> "$v%"})
                    }) {v -> request(V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,v[0],v[1]))}
            } }
        }
        if(category==ControlCategory.SOUND) {
            item(key="sound",contentType="controls") { GroupColumn {
                SwitchRow(Icons.Rounded.VolumeOff,stringResource(R.string.quick_mute),s?.muted,enabled=settingsSafe) {request(V12Protocol.Write(V12Protocol.Control.MUTE,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.MusicNote,stringResource(R.string.control_sound_wave),s?.soundWave,enabled=settingsSafe) {request(V12Protocol.Write(V12Protocol.Control.SOUND_WAVE,if(it)1 else 0))}
                ExpandSliders(Icons.Rounded.VolumeUp,stringResource(R.string.control_volume),s?.let { "${it.volume}%" } ?: "—",settingsSafe,
                    s?.takeIf { it.volume in 0..100 }?.let { listOf(SliderSpec(stringResource(R.string.control_volume),it.volume,0..100) {v -> "$v%"}) }
                ) {v -> request(V12Protocol.Write(V12Protocol.Control.VOLUME,v[0]))}
                SoundButtons(safe) {n -> request(V12Protocol.Write(V12Protocol.Control.PLAY_SOUND,n))}
            } }
        }
        if(category==ControlCategory.RIDE) {
            if(ui.connected && !settingsStopped) item(key="stationary-notice",contentType="banner") {Banner(stringResource(R.string.stationary_controls_hint),Icons.Rounded.Lock,warning=true)}
            item(key="modes",contentType="controls") { GroupColumn {
                SwitchRow(Icons.Rounded.Terrain,stringResource(R.string.control_off_road),s?.classicMode,hint=stringResource(R.string.control_classic_hint),enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.CLASSIC,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.Spa,stringResource(R.string.control_advanced),s?.fancier,hint=stringResource(R.string.control_advanced_hint),enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.FANCIER,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.CallSplit,stringResource(R.string.control_split),s?.splitMode,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.SPLIT_MODE,if(it)1 else 0))}
                ExpandSliders(Icons.Rounded.SyncAlt,stringResource(R.string.control_split_tuning),s?.let { "${it.splitAccel} · ${it.splitBrake}" } ?: "—",settingsStopped,
                    s?.let { listOf(SliderSpec(stringResource(R.string.control_acceleration),it.splitAccel.coerceIn(0,100),0..100) {v -> "$v%"},
                        SliderSpec(stringResource(R.string.control_braking),it.splitBrake.coerceIn(0,100),0..100) {v -> "$v%"}) }
                ) {v -> request(V12Protocol.Write(V12Protocol.Control.SPLIT_ACCEL_BRAKE,v[0],v[1]))}
            } }
        }
        if(category==ControlCategory.PEDALS) {
            item(key="pedals",contentType="controls") { GroupColumn {
                ExpandSliders(Icons.Rounded.Tune,stringResource(R.string.control_sensitivity),s?.let { "${it.comfortable}%" } ?: "—",settingsStopped,
                    s?.let { listOf(SliderSpec(stringResource(R.string.control_sensitivity_label),it.comfortable.coerceIn(0,100),0..100) {v -> "$v%"}) }
                ) {v -> request(V12Protocol.Write(V12Protocol.Control.PEDAL_SENS,v[0]))}
                // The draft lives in tenths of a degree, so 0.1° steps survive (no integer truncation).
                ExpandSliders(Icons.Rounded.Straighten,stringResource(R.string.control_pedal_tilt),s?.let { "${it.pedalTilt.fmt(1)}°" } ?: "—",settingsStopped,
                    s?.let { listOf(SliderSpec(stringResource(R.string.control_tilt_label),(it.pedalTilt*10).roundToInt(),-100..100) {v -> "${(v/10.0).fmt(1)}°"}) }
                ) {v -> request(V12Protocol.Write(V12Protocol.Control.PEDAL_TILT,v[0]))}
                ExpandSliders(Icons.Rounded.NotificationsActive,stringResource(R.string.control_alarms),
                    s?.let { "${it.alarm1.roundToInt()} · ${it.alarm2.roundToInt()} ${stringResource(R.string.unit_kmh)}" } ?: "—",settingsStopped,
                    s?.let { listOf(SliderSpec(stringResource(R.string.control_alarm1),it.alarm1.roundToInt(),0..70) {v -> "$v ${stringResource(R.string.unit_kmh)}"},
                        SliderSpec(stringResource(R.string.control_alarm2),it.alarm2.roundToInt(),0..70) {v -> "$v ${stringResource(R.string.unit_kmh)}"}) }
                ) {v -> request(V12Protocol.Write(V12Protocol.Control.ALARMS,v[0],v[1]))}
            } }
        }
        if(category==ControlCategory.SERVICE) {
            item(key="service",contentType="controls") { GroupColumn {
                SwitchRow(Icons.Rounded.TouchApp,stringResource(R.string.control_handle),s?.handleEnabled,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.HANDLE_BUTTON,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.LocalShipping,stringResource(R.string.control_transport),s?.transport,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.TRANSPORT,if(it)1 else 0))}
                SwitchRow(Icons.Rounded.Lock,stringResource(R.string.control_lock),s?.locked,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.LOCK,if(it)1 else 0))}
                ExpandSliders(Icons.Rounded.Timer,stringResource(R.string.control_standby),s?.let { "${(it.standbySeconds/60f).roundToInt()} ${stringResource(R.string.unit_min)}" } ?: "—",settingsStopped,
                    s?.let { listOf(SliderSpec(stringResource(R.string.control_timer_label),(it.standbySeconds/60f).roundToInt(),1..30) {v -> "$v ${stringResource(R.string.unit_min)}"}) }
                ) {v -> request(V12Protocol.Write(V12Protocol.Control.STANDBY,v[0]))}
            } }
        }
        item(key="read-settings",contentType="action") { OutlinedButton(onClick={readSettings()},enabled=ui.connected && !ui.wheel.workPending,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.control_read_settings),style=MaterialTheme.typography.labelLarge)} }
    }
}

