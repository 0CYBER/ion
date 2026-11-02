@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The everyday surface: one instrument, two key figures and two immediate accessory actions. */
@Composable internal fun RideScreen(ui: VelaUi,connect: ()->Unit,write: (V12Protocol.Write)->Unit,
    openWheel: ()->Unit,openSettings: ()->Unit) {
    val t=ui.wheel.telemetry
    val ion=ionColors()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {ScreenHeading(stringResource(R.string.ride_title),stringResource(R.string.open_connection),Icons.Rounded.Bluetooth,openWheel)}
        item(key="instrument",contentType="instrument") {
            if(t==null) StartRideCard(ui,connect) else SpeedInstrument(ui)
        }
        if(ui.wheel.phase==Phase.ERROR) item(key="error",contentType="banner") {Banner(ui.wheel.message,error=true)}
        ui.wheel.writeMessage?.let {message -> item(key="write-error",contentType="banner") {Banner(message,Icons.Rounded.ErrorOutline,error=true)}}
        if(t?.firmwareUpdate==true) item(key="firmware",contentType="banner") {Banner(stringResource(R.string.firmware_busy),Icons.Rounded.Lock,warning=true)}
        if(ui.staleConfirmed) item(key="stale",contentType="banner") {Banner(stringResource(R.string.telemetry_stale),Icons.Rounded.Update,warning=true)}
        if(t!=null) item(key="essentials",contentType="metrics") {
            MetricPair(first={m -> BatteryTile(t.battery,m)},second={m ->
                FigureTile(stringResource(R.string.metric_range),t.rangeKm.takeIf {it>0}?.fmt(1) ?: "—",
                    stringResource(R.string.unit_km),Icons.Rounded.Navigation,ion.blue,m)
            })
        }
        item(key="quick-title",contentType="section") {SectionHeader(stringResource(R.string.quick_controls))}
        item(key="quick",contentType="quick-controls") {
            MetricPair(first={m ->
                QuickToggle(stringResource(R.string.quick_light),t?.lowBeam,Icons.Rounded.Lightbulb,ui.safeWrite,m) {
                    write(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(it) 1 else 0,if(t?.highBeam==true) 1 else 0))
                }
            },second={m ->
                QuickToggle(stringResource(R.string.quick_mute),ui.wheel.settings?.muted,Icons.Rounded.VolumeOff,
                    ui.safeWrite && ui.settingsFresh,m) {write(V12Protocol.Write(V12Protocol.Control.MUTE,if(it) 1 else 0))}
            })
        }
        if(!ui.connected) item(key="quick-notice",contentType="note") {
            Text(stringResource(R.string.quick_offline),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=4.dp))
        }
        item(key="settings",contentType="link") {ActionRow(stringResource(R.string.wheel_settings),stringResource(R.string.wheel_settings_hint),Icons.Rounded.Tune,openSettings)}
        if(t!=null) {
            item(key="ride-data-title",contentType="section") {SectionHeader(stringResource(R.string.ride_details))}
            item(key="ride-data",contentType="readings") {
                val odo=ui.wheel.records[V12Protocol.Read.STATISTICS]?.frame?.let(V12Protocol::statistics)?.totalKm?.takeIf {it>0}
                GroupColumn {
                    ReadingRow(stringResource(R.string.metric_trip),t.tripKm.fmt(1),stringResource(R.string.unit_km),Icons.Rounded.Route)
                    ReadingRow(stringResource(R.string.metric_odometer),odo?.fmt(1) ?: "—",stringResource(R.string.unit_km),Icons.Rounded.Timeline)
                    ReadingRow(stringResource(R.string.metric_voltage),t.voltage.fmt(1),stringResource(R.string.unit_volts),Icons.Rounded.ElectricBolt)
                    ReadingRow(stringResource(R.string.metric_current),t.current.fmt(1),stringResource(R.string.unit_amps),Icons.Rounded.Waves)
                    ReadingRow(stringResource(R.string.temperature_motor),t.motorTemp.toString(),"°C",Icons.Rounded.Thermostat)
                    ReadingRow(stringResource(R.string.temperature_mos),t.mosTemp.toString(),"°C",Icons.Rounded.Memory)
                }
            }
        }
        item(key="connection",contentType="link") {
            ActionRow(stringResource(R.string.nav_wheel),stringResource(if(ui.connected) R.string.connection_active else R.string.connection_choose),Icons.Rounded.Bluetooth,openWheel)
        }
    }
}

@Composable private fun StartRideCard(ui: VelaUi,connect: ()->Unit) {
    val ion=ionColors()
    Surface(color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.wheel_model),style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                Icon(Icons.Rounded.TripOrigin,null,Modifier.size(32.dp))
            }
            Text(stringResource(if(ui.saved!=null) R.string.ride_ready else R.string.ride_start),style=MaterialTheme.typography.headlineLarge)
            Text(ui.saved?.name ?: stringResource(R.string.ride_start_hint),style=MaterialTheme.typography.bodyLarge)
            Button(onClick=connect,enabled=!ui.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
                colors=ButtonDefaults.buttonColors(containerColor=ion.accent,contentColor=ion.onAccent)) {
                if(ui.busy) LoadingIndicator(Modifier.size(24.dp),color=ion.onAccent)
                else Icon(Icons.Rounded.Bluetooth,null,Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if(ui.busy) R.string.connection_busy else if(ui.saved!=null) R.string.connect else R.string.connection_find))
            }
        }
    }
}

@Composable private fun SpeedInstrument(ui: VelaUi) {
    val ion=ionColors()
    Surface(color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.wheel_model),style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                Icon(if(ui.fresh) Icons.Rounded.Sensors else Icons.Rounded.Update,null,Modifier.size(24.dp))
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val value=ui.wheel.telemetry?.speed?.fmt(1) ?: "—"
                val style=if(maxWidth<300.dp || LocalDensity.current.fontScale>1.2f) IonType.speedCompact else IonType.speed
                val fit=minOf(style.fontSize.value,maxWidth.value/(value.length*.65f*LocalDensity.current.fontScale)).sp
                Column {
                    Text(value,style=style.copy(fontSize=fit,lineHeight=fit*1.12f),maxLines=1)
                    Text(stringResource(R.string.speed_unit),style=MaterialTheme.typography.titleLarge)
                }
            }
            Text(stringResource(if(ui.fresh) R.string.live_telemetry else R.string.last_readings),style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun BatteryTile(battery: Double?,modifier: Modifier) {
    val ion=ionColors()
    val valid=battery?.takeIf {it.isFinite() && it in 0.0..100.0}
    Surface(modifier,color=if(valid!=null) ion.accent else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor=if(valid!=null) ion.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,shape=IonShapes.discovery) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.metric_battery),style=MaterialTheme.typography.labelLarge)
            Text(valid?.let {stringResource(R.string.battery_value,it.roundToInt())} ?: "—",style=IonType.figure)
            if(valid!=null) LinearWavyProgressIndicator(progress={valid.toFloat()/100f},modifier=Modifier.fillMaxWidth(),color=ion.onAccent,trackColor=ion.onAccent.copy(alpha=.15f))
            else Text(stringResource(R.string.no_data),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun FigureTile(label: String,value: String,unit: String,icon: ImageVector,tone: IonTone,modifier: Modifier) {
    Surface(modifier,color=tone.container,contentColor=tone.content,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().heightIn(min=156.dp).padding(20.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(label,style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                Icon(icon,null,Modifier.size(20.dp))
            }
            Column(Modifier.padding(top=8.dp)) {
                Text(value,style=IonType.figure,maxLines=1)
                Text(unit,style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable private fun QuickToggle(title: String,value: Boolean?,icon: ImageVector,enabled: Boolean,modifier: Modifier,change: (Boolean)->Unit) {
    val checked=value==true
    val color by animateColorAsState(if(checked && enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.fastEffectsSpec(),label="quick-color")
    val radius by animateDpAsState(if(checked && enabled) 32.dp else 20.dp,MaterialTheme.motionScheme.fastSpatialSpec(),label="quick-shape")
    Surface(modifier,color=color,shape=RoundedCornerShape(radius)) {
        Row(Modifier.fillMaxWidth().heightIn(min=88.dp).toggleable(value=checked,enabled=enabled && value!=null,role=Role.Switch,onValueChange=change)
            .padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(stringResource(if(value==null) R.string.no_data else if(checked) R.string.ion_state_on else R.string.ion_state_off),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun ReadingRow(label: String,value: String,unit: String,icon: ImageVector) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Text(label,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
            Text("$value $unit",style=MaterialTheme.typography.titleMedium,fontFamily=IonMono)
        }
    }
}
