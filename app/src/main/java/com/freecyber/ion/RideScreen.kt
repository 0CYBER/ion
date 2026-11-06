@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The everyday surface, assembled from the rider's own [DashboardLayout]: every tile is an independent module. */
@Composable internal fun RideScreen(ui: VelaUi,layout: DashboardLayout,connect: ()->Unit,write: (V12Protocol.Write)->Unit,
    openWheel: ()->Unit,openSettings: ()->Unit,editDashboard: ()->Unit,openRideMode: ()->Unit,
    openTrips: ()->Unit={},finishTrip: ()->Unit={}) {
    val t=ui.wheel.telemetry
    val rows=remember(layout) {layout.rows()}
    @Composable fun list(rows: List<List<TileSlot>>,modifier: Modifier) = LazyColumn(modifier,contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {
            ScreenHeading(stringResource(R.string.ride_title),stringResource(R.string.open_connection),Icons.Rounded.Bluetooth,openWheel,
                secondaryLabel=stringResource(R.string.dash_edit),secondaryIcon=Icons.Rounded.DashboardCustomize,onSecondary=editDashboard)
        }
        if(t==null) item(key="start",contentType="instrument") {StartRideCard(ui,connect)}
        if(ui.reconnecting) item(key="reconnecting",contentType="banner") {Banner(stringResource(R.string.live_title_reconnecting),Icons.Rounded.Autorenew,warning=true)}
        else if(ui.wheel.phase==Phase.ERROR) item(key="error",contentType="banner") {Banner(ui.wheel.message,error=true)}
        ui.wheel.writeMessage?.let {message -> item(key="write-error",contentType="banner") {Banner(message,Icons.Rounded.ErrorOutline,error=true)}}
        if(t?.firmwareUpdate==true) item(key="firmware",contentType="banner") {Banner(stringResource(R.string.firmware_busy),Icons.Rounded.Lock,warning=true)}
        if(ui.staleConfirmed) item(key="stale",contentType="banner") {Banner(stringResource(R.string.telemetry_stale),Icons.Rounded.Update,warning=true)}
        // Without any readings a wall of dashes helps nobody: only an unfinished session stays visible.
        val visibleRows=if(t!=null) rows else rows.flatten().filter {it.tile==DashTile.TRIP_SESSION && ui.trip.samples>0}.map {listOf(it)}
        if(visibleRows.isEmpty() && t!=null) item(key="dash-empty",contentType="note") {Banner(stringResource(R.string.dash_empty),Icons.Rounded.DashboardCustomize)}
        items(visibleRows,key={row -> row.joinToString("+") {it.tile.name}},contentType={row -> if(row.size==2) "pair" else "single"}) {row ->
            val env=TileEnv(ui,write,openRideMode,openTrips,finishTrip)
            if(row.size==2) TilePair({m -> DashTileView(row[0],env,m)},{m -> DashTileView(row[1],env,m)})
            else DashTileView(row[0],env,Modifier.fillMaxWidth())
        }
        item(key="trips",contentType="link") {ActionRow(stringResource(R.string.trips_title),stringResource(R.string.trips_hint),Icons.Rounded.History,openTrips)}
        item(key="settings",contentType="link") {ActionRow(stringResource(R.string.wheel_settings),stringResource(R.string.wheel_settings_hint),Icons.Rounded.Tune,openSettings)}
        item(key="connection",contentType="link") {
            ActionRow(stringResource(R.string.nav_wheel),stringResource(if(ui.connected) R.string.connection_active else R.string.connection_choose),Icons.Rounded.Bluetooth,openWheel)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape: the speed stays in view on the left while the other tiles scroll on the right.
        val split=t!=null && maxWidth>=560.dp && maxWidth>maxHeight*1.2f && rows.any {row -> row.any {it.tile==DashTile.SPEED}}
        if(split) Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(.42f).fillMaxHeight().padding(start=20.dp,top=12.dp,bottom=20.dp),contentAlignment=Alignment.Center) {
                SpeedInstrument(ui,Modifier.fillMaxWidth())
            }
            list(rows.map {row -> row.filter {it.tile!=DashTile.SPEED}}.filter {it.isNotEmpty()},Modifier.weight(.58f).fillMaxHeight())
        } else list(rows,Modifier.fillMaxSize())
    }
}

/** Two half tiles side by side with equal height; stacks them when the width or font scale leaves no room. */
@Composable private fun TilePair(first: @Composable (Modifier)->Unit,second: @Composable (Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<300.dp || LocalDensity.current.fontScale>1.2f)
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {first(Modifier.fillMaxWidth());second(Modifier.fillMaxWidth())}
        else Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f).fillMaxHeight());second(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Everything a tile may need; tiles never reach outside this. */
@Immutable internal class TileEnv(val ui: VelaUi,val write: (V12Protocol.Write)->Unit,val openRideMode: ()->Unit,
    val openTrips: ()->Unit={},val finishTrip: ()->Unit={})

@Composable internal fun DashTileView(slot: TileSlot,env: TileEnv,modifier: Modifier) {
    val ui=env.ui; val t=ui.wheel.telemetry; val s=ui.wheel.settings
    val ion=ionColors()
    val wide=slot.effectiveWide
    when(slot.tile) {
        DashTile.SPEED -> SpeedInstrument(ui,modifier)
        DashTile.BATTERY -> BatteryTile(t?.battery,modifier)
        DashTile.RANGE -> FigureTile(stringResource(R.string.metric_range),t?.rangeKm?.takeIf {it>0}?.fmt(1) ?: "—",
            stringResource(R.string.unit_km),Icons.Rounded.Navigation,ion.blue,modifier)
        DashTile.QUICK_LIGHT -> QuickToggle(stringResource(R.string.quick_light),t?.lowBeam,Icons.Rounded.Lightbulb,ui.safeWrite,modifier) {
            env.write(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(it) 1 else 0,if(t?.highBeam==true) 1 else 0))
        }
        DashTile.QUICK_HIGH_BEAM -> QuickToggle(stringResource(R.string.control_high_beam),t?.highBeam,Icons.Rounded.Highlight,ui.safeWrite,modifier) {
            env.write(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(t?.lowBeam==true) 1 else 0,if(it) 1 else 0))
        }
        DashTile.QUICK_MUTE -> QuickToggle(stringResource(R.string.quick_mute),s?.muted,Icons.AutoMirrored.Rounded.VolumeOff,
            ui.safeWrite && ui.settingsFresh,modifier) {env.write(V12Protocol.Write(V12Protocol.Control.MUTE,if(it) 1 else 0))}
        DashTile.PLAY_SOUND -> ActionTile(stringResource(R.string.tile_play_sound),Icons.Rounded.Campaign,ui.safeWrite,modifier) {
            env.write(V12Protocol.Write(V12Protocol.Control.PLAY_SOUND,1))
        }
        DashTile.TRIP_SESSION -> TripSessionCard(ui.trip,modifier,env.openTrips,env.finishTrip)
        DashTile.TRIP -> MetricTile(stringResource(R.string.tile_trip),t?.tripKm?.fmt(1),stringResource(R.string.unit_km),Icons.Rounded.Route,wide,modifier)
        DashTile.ODOMETER -> MetricTile(stringResource(R.string.metric_odometer),ui.odometerKm?.fmt(1),stringResource(R.string.unit_km),Icons.Rounded.Timeline,wide,modifier)
        DashTile.VOLTAGE -> MetricTile(stringResource(R.string.metric_voltage),t?.voltage?.fmt(1),stringResource(R.string.unit_volts),Icons.Rounded.ElectricBolt,wide,modifier)
        DashTile.CURRENT -> MetricTile(stringResource(R.string.metric_current),t?.current?.fmt(1),stringResource(R.string.unit_amps),Icons.Rounded.Waves,wide,modifier)
        DashTile.POWER -> MetricTile(stringResource(R.string.tile_power),t?.power?.roundToInt()?.toString(),stringResource(R.string.unit_watts),Icons.Rounded.Bolt,wide,modifier)
        DashTile.MOTOR_TEMP -> MetricTile(stringResource(R.string.temperature_motor),t?.motorTemp?.takeIf {it in -50..180}?.toString(),stringResource(R.string.unit_celsius),Icons.Rounded.Thermostat,wide,modifier)
        DashTile.MOS_TEMP -> MetricTile(stringResource(R.string.temperature_mos),t?.mosTemp?.takeIf {it in -50..180}?.toString(),stringResource(R.string.unit_celsius),Icons.Rounded.Memory,wide,modifier)
        DashTile.PWM -> PwmTile(t?.pwm,modifier)
        DashTile.TILT -> MetricTile(stringResource(R.string.tile_tilt),t?.let {"${it.pitch.fmt(1)} / ${it.roll.fmt(1)}"},stringResource(R.string.unit_degrees),Icons.Rounded.ScreenRotation,wide,modifier)
        DashTile.MODE -> ModeTile(s,modifier,env.openRideMode)
        DashTile.CHART -> ChartTile(ui.chart,modifier)
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
            val busy=ui.busy || ui.reconnecting
            Button(onClick=connect,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
                colors=ButtonDefaults.buttonColors(containerColor=ion.accent,contentColor=ion.onAccent)) {
                if(busy) LoadingIndicator(Modifier.size(24.dp),color=ion.onAccent)
                else Icon(Icons.Rounded.Bluetooth,null,Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if(ui.reconnecting) R.string.status_reconnecting else if(busy) R.string.connection_busy else if(ui.saved!=null) R.string.connect else R.string.connection_find))
            }
        }
    }
}

@Composable private fun SpeedInstrument(ui: VelaUi,modifier: Modifier) {
    val ion=ionColors()
    Surface(modifier,color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
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

/** Compact reading: a row when wide, a small card when half width. */
@Composable private fun MetricTile(label: String,value: String?,unit: String,icon: ImageVector,wide: Boolean,modifier: Modifier) {
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.l)) {
        if(wide) Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Text(label,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
            Text("${value ?: "—"} $unit",style=MaterialTheme.typography.titleMedium,fontFamily=IonMono)
        } else Column(Modifier.fillMaxWidth().heightIn(min=96.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(icon,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
            }
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(value ?: "—",style=MaterialTheme.typography.headlineSmall,fontFamily=IonMono,maxLines=1)
                Text(unit,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=3.dp))
            }
        }
    }
}

@Composable private fun PwmTile(pwm: Double?,modifier: Modifier) {
    val ion=ionColors()
    val load=pwm?.takeIf {it.isFinite()}?.let {kotlin.math.abs(it).coerceIn(0.0,100.0)}
    val hot=load!=null && load>=80
    Surface(modifier,color=if(hot) ion.warning else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor=if(hot) ion.onWarning else MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(IonShapes.l)) {
        Column(Modifier.fillMaxWidth().heightIn(min=96.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tile_pwm),style=MaterialTheme.typography.labelLarge)
            Text(load?.let {"${it.roundToInt()}%"} ?: "—",style=MaterialTheme.typography.headlineSmall,fontFamily=IonMono)
            if(load!=null) LinearProgressIndicator(progress={(load/100).toFloat()},modifier=Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun TripSessionCard(trip: TripStats,modifier: Modifier,openTrips: ()->Unit,finishTrip: ()->Unit) {
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Insights,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.tile_trip_session),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                trip.startedAt?.let {Text(V12Protocol.formatDuration((trip.movingMs/1000)),style=MaterialTheme.typography.labelLarge,fontFamily=IonMono,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            if(trip.samples==0) Text(stringResource(R.string.trip_empty),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TripFigure(stringResource(R.string.trip_distance),trip.distanceKm.fmt(2),stringResource(R.string.unit_km),Modifier.weight(1f))
                    TripFigure(stringResource(R.string.trip_max),trip.maxSpeed.fmt(1),stringResource(R.string.unit_kmh),Modifier.weight(1f))
                    TripFigure(stringResource(R.string.trip_avg),trip.avgMovingSpeed.fmt(1),stringResource(R.string.unit_kmh),Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TripFigure(stringResource(R.string.trip_energy),(trip.energyWh-trip.regenWh).fmt(0),stringResource(R.string.unit_wh),Modifier.weight(1f))
                    TripFigure(stringResource(R.string.trip_consumption),trip.consumption?.fmt(1) ?: "—",stringResource(R.string.unit_wh_km),Modifier.weight(1f))
                    TripFigure(stringResource(R.string.trip_battery_used),trip.batteryUsed?.fmt(0) ?: "—",stringResource(R.string.unit_percent),Modifier.weight(1f))
                }
            }
            // FlowRow: at large font sizes the second button moves to its own line instead of being squeezed letter by letter.
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick=openTrips,modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.History,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_title))
                }
                if(trip.distanceKm>=TripSummary.MIN_DISTANCE_KM) TextButton(onClick=finishTrip,modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.Flag,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trip_finish))
                }
            }
        }
    }
}

@Composable internal fun TripFigure(label: String,value: String,unit: String,modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        Text(value,style=MaterialTheme.typography.titleLarge,fontFamily=IonMono,maxLines=1)
        Text(unit,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ModeTile(s: V12Protocol.Settings?,modifier: Modifier,open: ()->Unit) {
    Surface(onClick=open,modifier=modifier,color=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer,shape=RoundedCornerShape(IonShapes.l)) {
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Terrain,null,Modifier.size(24.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.tile_mode),style=MaterialTheme.typography.labelLarge)
                Text(s?.let {stringResource(if(it.classicMode) R.string.control_off_road else R.string.mode_comfort)+" · "+
                    stringResource(if(it.fancier) R.string.control_advanced else R.string.mode_normal)} ?: stringResource(R.string.no_data),
                    style=MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Rounded.ChevronRight,null)
        }
    }
}

@Composable private fun ActionTile(title: String,icon: ImageVector,enabled: Boolean,modifier: Modifier,click: ()->Unit) {
    Surface(onClick=click,enabled=enabled,modifier=modifier,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.row)) {
        Row(Modifier.fillMaxWidth().heightIn(min=88.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(26.dp),tint=if(enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
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
