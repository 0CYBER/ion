@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.text.TextAutoSize

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
        if(t==null) item(key="start",contentType="instrument") {Box(Modifier.fillMaxWidth().animateItem()) {StartRideCard(ui,connect)}}
        if(ui.reconnecting) item(key="reconnecting",contentType="banner") {Box(Modifier.fillMaxWidth().animateItem()) {Banner(stringResource(R.string.live_title_reconnecting),Icons.Rounded.Autorenew,warning=true)}}
        else if(ui.wheel.phase==Phase.ERROR) item(key="error",contentType="banner") {Box(Modifier.fillMaxWidth().animateItem()) {Banner(ui.wheel.message,error=true)}}
        ui.wheel.writeMessage?.let {message -> item(key="write-error",contentType="banner") {Box(Modifier.fillMaxWidth().animateItem()) {Banner(message,Icons.Rounded.ErrorOutline,error=true)}}}
        if(t?.firmwareUpdate==true) item(key="firmware",contentType="banner") {Box(Modifier.fillMaxWidth().animateItem()) {Banner(stringResource(R.string.firmware_busy),Icons.Rounded.Lock,warning=true)}}
        if(ui.staleConfirmed) item(key="stale",contentType="banner") {Box(Modifier.fillMaxWidth().animateItem()) {Banner(stringResource(R.string.telemetry_stale),Icons.Rounded.Update,warning=true)}}
        // Without any readings a wall of dashes helps nobody: only an unfinished session stays visible.
        val visibleRows=if(t!=null) rows else rows.flatten().filter {it.tile==DashTile.TRIP_SESSION && ui.trip.samples>0}.map {listOf(it)}
        if(visibleRows.isEmpty() && t!=null) item(key="dash-empty",contentType="note") {Box(Modifier.fillMaxWidth().animateItem()) {Banner(stringResource(R.string.dash_empty),Icons.Rounded.DashboardCustomize)}}
        items(visibleRows,key={row -> row.joinToString("+") {it.id}},contentType={row -> if(row.size==2) "pair" else "single"}) {row ->
            val env=TileEnv(ui,write,openRideMode,openTrips,finishTrip)
            // animateItem: banners above appear and leave smoothly instead of making the tiles jump.
            Box(Modifier.fillMaxWidth().animateItem()) {
                if(row.size==2) TilePair({m -> DashTileView(row[0],env,m)},{m -> DashTileView(row[1],env,m)})
                else DashTileView(row[0],env,Modifier.fillMaxWidth())
            }
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

/**
 * Half tiles grow with their contents; avoid intrinsics because speed uses BoxWithConstraints.
 * A plain [Layout] instead of BoxWithConstraints: a nested subcomposition inside a reused LazyColumn item
 * did not restart the tiles' infinite animations after the row scrolled off screen and back (frozen charge wave).
 */
@Composable internal fun TilePair(first: @Composable (Modifier)->Unit,second: (@Composable (Modifier)->Unit)?) {
    if(second==null) { first(Modifier.fillMaxWidth()); return }
    val fontScale=LocalDensity.current.fontScale
    androidx.compose.ui.layout.Layout(content={first(Modifier);second(Modifier)},modifier=Modifier.fillMaxWidth()) {measurables,constraints ->
        val gap=12.dp.roundToPx()
        val width=constraints.maxWidth
        val stacked=width<300.dp.roundToPx() || fontScale>1.2f
        val free=constraints.copy(minHeight=0,maxHeight=androidx.compose.ui.unit.Constraints.Infinity)
        if(stacked) {
            val placeables=measurables.map {it.measure(free.copy(minWidth=width,maxWidth=width))}
            val height=placeables.sumOf {it.height}+gap*(placeables.size-1).coerceAtLeast(0)
            layout(width,height) {
                var y=0
                placeables.forEach {it.placeRelative(0,y);y+=it.height+gap}
            }
        } else {
            val half=((width-gap)/2).coerceAtLeast(0)
            val widths=measurables.indices.map {i -> if(i==0) half else width-gap-half}
            val placeables=measurables.mapIndexed {i,m -> m.measure(free.copy(minWidth=widths[i],maxWidth=widths[i]))}
            val height=placeables.maxOf {it.height}
            layout(width,height) {
                placeables[0].placeRelative(0,0)
                placeables.getOrNull(1)?.placeRelative(half+gap,0)
            }
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
    slot.toggleTarget?.let { target ->
        val value=target.value(t,s)
        val needsSettings=target!=DashToggle.LOW_BEAM && target!=DashToggle.HIGH_BEAM
        QuickToggle(stringResource(target.titleRes),value,target.icon,
            ui.canTier(target.control.tier) && value!=null && (!needsSettings || ui.settingsFresh),modifier) {
            env.write(target.write(it,t))
        }
        return
    }
    when(slot.tile) {
        DashTile.SPEED -> SpeedInstrument(ui,modifier)
        DashTile.BATTERY -> BatteryTile(t?.battery,ui.connected && ui.fresh && t?.charging==true,modifier)
        DashTile.RANGE -> FigureTile(stringResource(R.string.metric_range),t?.rangeKm?.takeIf {it>0}?.roundToInt()?.toString() ?: "—",
            stringResource(R.string.unit_km),Icons.Rounded.Navigation,ion.blue,modifier)
        DashTile.PLAY_SOUND -> ActionTile(stringResource(R.string.tile_play_sound),Icons.Rounded.Campaign,ui.safeWrite,modifier) {
            // The rider picks the horn sound in the editor; 4 is the classic signal on the V12 HT.
            env.write(V12Protocol.Write(V12Protocol.Control.PLAY_SOUND,slot.param ?: DashTile.PLAY_SOUND.defaultParam ?: 4))
        }
        DashTile.TRIP_SESSION -> TripSessionCard(ui.trip,ui.gpsPoints,modifier,env.openTrips,env.finishTrip,ui.gpsStatus,ui.recordingFailed)
        DashTile.TRIP -> MetricTile(stringResource(R.string.tile_trip),t?.tripKm?.fmt(1),stringResource(R.string.unit_km),Icons.Rounded.Route,wide,modifier)
        DashTile.ODOMETER -> MetricTile(stringResource(R.string.metric_odometer),ui.odometerKm?.fmt(1),stringResource(R.string.unit_km),Icons.Rounded.Timeline,wide,modifier)
        DashTile.VOLTAGE -> MetricTile(stringResource(R.string.metric_voltage),t?.voltage?.fmt(1),stringResource(R.string.unit_volts),Icons.Rounded.ElectricBolt,wide,modifier)
        DashTile.CURRENT -> MetricTile(stringResource(R.string.metric_current),t?.current?.fmt(1),stringResource(R.string.unit_amps),Icons.Rounded.Waves,wide,modifier)
        DashTile.POWER -> MetricTile(stringResource(R.string.tile_power),t?.power?.takeIf {it.isFinite()}?.roundToInt()?.toString(),stringResource(R.string.unit_watts),Icons.Rounded.Bolt,wide,modifier)
        DashTile.MOTOR_TEMP -> MetricTile(stringResource(R.string.temperature_motor),t?.motorTemp?.takeIf {it in -50..180}?.toString(),stringResource(R.string.unit_celsius),Icons.Rounded.Thermostat,wide,modifier)
        DashTile.MOS_TEMP -> MetricTile(stringResource(R.string.temperature_mos),t?.mosTemp?.takeIf {it in -50..180}?.toString(),stringResource(R.string.unit_celsius),Icons.Rounded.Memory,wide,modifier)
        DashTile.PWM -> PwmTile(t?.pwm,modifier)
        DashTile.TILT -> MetricTile(stringResource(R.string.tile_tilt),t?.let {"${it.pitch.fmt(1)} / ${it.roll.fmt(1)}"},stringResource(R.string.unit_degrees),Icons.Rounded.ScreenRotation,wide,modifier)
        DashTile.MODE -> ModeTile(s,modifier,env.openRideMode)
        DashTile.CHART -> ChartTile(ui.chart,modifier)
        DashTile.TOGGLE, DashTile.QUICK_LIGHT, DashTile.QUICK_HIGH_BEAM, DashTile.QUICK_MUTE, DashTile.QUICK_LOCK,
        DashTile.QUICK_AUTO_LIGHT, DashTile.QUICK_SOUND_WAVE -> Unit // Rendered through toggleTarget.
        DashTile.MAX_SPEED -> MetricTile(stringResource(R.string.trip_max),ui.trip.takeIf {it.samples>0}?.maxSpeed?.fmt(1),stringResource(R.string.unit_kmh),Icons.Rounded.Speed,wide,modifier)
        DashTile.AVG_SPEED -> MetricTile(stringResource(R.string.trip_avg),ui.trip.takeIf {it.samples>0}?.avgMovingSpeed?.fmt(1),stringResource(R.string.unit_kmh),Icons.Rounded.AvTimer,wide,modifier)
        DashTile.ENERGY -> MetricTile(stringResource(R.string.tile_energy),ui.trip.takeIf {it.samples>0}?.let {(it.energyWh-it.regenWh).fmt(0)},stringResource(R.string.unit_wh),Icons.Rounded.Bolt,wide,modifier)
        DashTile.CONSUMPTION -> MetricTile(stringResource(R.string.tile_consumption),ui.trip.consumption?.fmt(1),stringResource(R.string.unit_wh_km),Icons.Rounded.Eco,wide,modifier)
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
    Surface(modifier.testTag("speed-instrument").semantics(mergeDescendants=true) {},color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(ui.wheel.deviceName ?: ui.saved?.name ?: stringResource(R.string.wheel_model),style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                Icon(if(ui.fresh) Icons.Rounded.Sensors else Icons.Rounded.Update,null,Modifier.size(24.dp))
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val value=ui.wheel.telemetry?.speed?.let {kotlin.math.abs(it)}?.fmt(1) ?: "—"
                val style=if(maxWidth<300.dp || LocalDensity.current.fontScale>1.2f) IonType.speedCompact else IonType.speed
                val fit=minOf(style.fontSize.value,maxWidth.value/(value.length*.65f*LocalDensity.current.fontScale)).sp
                Column {
                    Text(value,style=style.copy(fontSize=fit,lineHeight=fit*1.12f),maxLines=1)
                    Text(stringResource(R.string.speed_unit),style=MaterialTheme.typography.titleLarge)
                }
            }
            if(!ui.fresh) ReadingAgeText(ui.wheel.lastPacketAt,ui.now)
        }
    }
}

/** "Last readings · 12 s ago": only this caption observes the age clock, the speed figure itself does not tick. */
@Composable private fun ReadingAgeText(lastPacketAt: Long?,snapshotAt: Long) {
    val now by produceState(snapshotAt,snapshotAt,lastPacketAt) {
        if(lastPacketAt==null) return@produceState
        val anchor=android.os.SystemClock.elapsedRealtime()
        while(true) {
            value=snapshotAt+android.os.SystemClock.elapsedRealtime()-anchor
            kotlinx.coroutines.delay(1000)
        }
    }
    val text=when(val age=ReadingAge.of(lastPacketAt?.let {now-it})) {
        null -> stringResource(R.string.last_readings)
        is ReadingAge.Seconds -> stringResource(R.string.last_readings_seconds,age.amount)
        is ReadingAge.Minutes -> stringResource(R.string.last_readings_minutes,age.amount)
        is ReadingAge.Hours -> stringResource(R.string.last_readings_hours,age.amount)
    }
    Text(text,style=MaterialTheme.typography.bodySmall)
}

@Composable private fun BatteryTile(battery: Double?,charging: Boolean,modifier: Modifier) {
    val ion=ionColors()
    val valid=battery?.takeIf {it.isFinite() && it in 0.0..100.0}
    val progress=animateFloatAsState((valid?.toFloat() ?: 0f)/100f,ProgressIndicatorDefaults.ProgressAnimationSpec,label="battery-level")
    // Without a reading TalkBack read "Battery, dash, No data"; say it once.
    val missing=if(valid==null) "${stringResource(R.string.metric_battery)}, ${stringResource(R.string.no_data)}" else null
    Surface(modifier.semantics(mergeDescendants=true) {missing?.let {contentDescription=it}},color=if(valid!=null) ion.accent else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor=if(valid!=null) ion.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,shape=IonShapes.discovery) {
        // Same minimum height as the range tile it is usually paired with, so the pair reads as one row.
        Column(Modifier.fillMaxWidth().heightIn(min=156.dp).padding(20.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.metric_battery),style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                if(charging) Icon(Icons.Rounded.Bolt,stringResource(R.string.battery_charging),Modifier.size(20.dp))
            }
            Column(Modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(valid?.let {stringResource(R.string.battery_value,it.roundToInt())} ?: "—",style=IonType.figure,maxLines=1)
                if(valid!=null) BatteryWave({progress.value},charging,ion.onAccent,
                    Modifier.fillMaxWidth().testTag(if(charging) "battery-charge-wave" else "battery-level-wave"))
                else Text(stringResource(R.string.no_data),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Charge bar drawn by hand: the wave always ends at the real level, and while charging it flows towards
 * the full end. The phase is driven by a LaunchedEffect frame loop, which restarts cleanly every time the
 * tile (re)enters composition — the stock indeterminate indicator froze after the tile scrolled off screen,
 * and it also hid the actual level while charging.
 */
@Composable private fun BatteryWave(level: ()->Float,charging: Boolean,color: androidx.compose.ui.graphics.Color,modifier: Modifier) {
    val phase=remember {mutableFloatStateOf(0f)}
    if(charging) LaunchedEffect(Unit) {
        var last=0L
        while(true) androidx.compose.animation.core.withInfiniteAnimationFrameNanos {now ->
            if(last!=0L) phase.floatValue=(phase.floatValue+(now-last)/1_000_000_000f*.9f)%1f
            last=now
        }
    }
    // The wave calms down to a gentle static ripple when not charging; the glow pulse marks the charging tip.
    val amplitude by animateFloatAsState(if(charging) 1f else .55f,MaterialTheme.motionScheme.slowEffectsSpec(),label="battery-amplitude")
    val path=remember {androidx.compose.ui.graphics.Path()}
    androidx.compose.foundation.Canvas(modifier.height(14.dp).semantics {
        progressBarRangeInfo=androidx.compose.ui.semantics.ProgressBarRangeInfo(level().coerceIn(0f,1f),0f..1f)}) {
        val stroke=4.dp.toPx(); val mid=size.height/2; val gap=6.dp.toPx()
        val end=(size.width*level().coerceIn(0f,1f)).coerceAtLeast(stroke)
        val wavelength=28.dp.toPx(); val amp=3.dp.toPx()*amplitude
        val cap=androidx.compose.ui.graphics.StrokeCap.Round
        // Track: flat remainder after a small gap, like the Material wavy indicator.
        if(end+gap<size.width-stroke/2) drawLine(color.copy(alpha=.18f),androidx.compose.ui.geometry.Offset(end+gap,mid),
            androidx.compose.ui.geometry.Offset(size.width-stroke/2,mid),stroke,cap)
        path.reset()
        val start=stroke/2; val shift=phase.floatValue*wavelength
        var x=start
        path.moveTo(x,mid+amp*kotlin.math.sin(((x-shift)/wavelength)*2f*Math.PI.toFloat()))
        val step=2.dp.toPx()
        while(x<end) {
            x=minOf(x+step,end)
            // Taper the amplitude at both ends so the rounded caps sit on the axis.
            val taper=minOf(1f,(x-start)/wavelength*2f,(end-x)/wavelength*2f+.15f).coerceIn(0f,1f)
            path.lineTo(x,mid+amp*taper*kotlin.math.sin(((x-shift)/wavelength)*2f*Math.PI.toFloat()))
        }
        drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(stroke,cap=cap,join=androidx.compose.ui.graphics.StrokeJoin.Round))
        if(charging) {
            val pulse=.5f+.5f*kotlin.math.sin(phase.floatValue*2f*Math.PI.toFloat())
            drawCircle(color.copy(alpha=.18f+.22f*pulse),radius=stroke*(1.1f+.5f*pulse),center=androidx.compose.ui.geometry.Offset(end,mid))
        }
    }
}

@Composable private fun FigureTile(label: String,value: String,unit: String,icon: ImageVector,tone: IonTone,modifier: Modifier) {
    val spoken=if(value=="—") "$label, ${stringResource(R.string.no_data)}" else "$label, $value $unit"
    Surface(modifier.semantics(mergeDescendants=true) {contentDescription=spoken},color=tone.container,contentColor=tone.content,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().heightIn(min=156.dp).padding(20.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(label,style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                Icon(icon,null,Modifier.size(20.dp))
            }
            Column(Modifier.padding(top=8.dp)) {
                // Shrinks instead of clipping ("180," with the last digit cut off) when a value is long for a half tile.
                Text(value,style=IonType.figure,maxLines=1,softWrap=false,
                    autoSize=TextAutoSize.StepBased(minFontSize=20.sp,maxFontSize=IonType.figure.fontSize))
                Text(unit,style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Compact reading: a row when wide, a small card when half width. Read aloud as one item. */
@Composable internal fun MetricTile(label: String,value: String?,unit: String,icon: ImageVector,wide: Boolean,modifier: Modifier) {
    // TalkBack read a missing value as "dash"; announce "no data" instead.
    val spoken=if(value==null || value=="—") "$label, ${stringResource(R.string.no_data)}" else "$label, $value $unit"
    val readingStyle = if (wide) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
    val readingHeight = with(LocalDensity.current) { readingStyle.lineHeight.toDp() }
    Surface(modifier.semantics(mergeDescendants=true) {contentDescription=spoken},color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.l)) {
        if(wide) Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Text(label,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
            Text("${value ?: "—"}\u00A0$unit",style=readingStyle.copy(fontFeatureSettings="tnum"),
                fontFamily=IonSans,softWrap=false,maxLines=1,
                modifier=Modifier.heightIn(min=readingHeight),
                autoSize=TextAutoSize.StepBased(minFontSize=8.sp,maxFontSize=readingStyle.fontSize))
        } else Column(Modifier.fillMaxWidth().heightIn(min=96.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(icon,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
            }
            Row(Modifier.heightIn(min=readingHeight),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(value ?: "—",style=readingStyle.copy(fontFeatureSettings="tnum"),fontFamily=IonSans,softWrap=false,maxLines=1,
                    autoSize=TextAutoSize.StepBased(minFontSize=8.sp,maxFontSize=readingStyle.fontSize),
                    modifier=Modifier.weight(1f,fill=false))
                Text(unit,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap=false,maxLines=1,modifier=Modifier.padding(bottom=3.dp))
            }
        }
    }
}

/** Same shape, size and blue tone as the range tile it replaces by default; amber from 80 % load. */
@Composable private fun PwmTile(pwm: Double?,modifier: Modifier) {
    val ion=ionColors()
    val load=pwm?.takeIf {it.isFinite()}?.let {kotlin.math.abs(it).coerceIn(0.0,100.0)}
    val hot=load!=null && load>=80
    val spoken="${stringResource(R.string.tile_pwm)}, ${load?.let {"${it.roundToInt()}%"} ?: stringResource(R.string.no_data)}"
    val container by animateColorAsState(if(hot) ion.warning else ion.blue.container,MaterialTheme.motionScheme.fastEffectsSpec(),label="pwm-tone")
    val content=if(hot) ion.onWarning else ion.blue.content
    Surface(modifier.semantics(mergeDescendants=true) {contentDescription=spoken},color=container,contentColor=content,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().heightIn(min=156.dp).padding(20.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.tile_pwm),style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                Icon(Icons.Rounded.Equalizer,null,Modifier.size(20.dp))
            }
            Column(Modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(load?.let {"${it.roundToInt()}%"} ?: "—",style=IonType.figure,maxLines=1,softWrap=false,
                    autoSize=TextAutoSize.StepBased(minFontSize=20.sp,maxFontSize=IonType.figure.fontSize))
                if(load!=null) LinearProgressIndicator(progress={(load/100).toFloat()},modifier=Modifier.fillMaxWidth(),
                    color=content,trackColor=content.copy(alpha=.18f),drawStopIndicator={})
            }
        }
    }
}

@Composable private fun TripSessionCard(trip: TripStats,gpsPoints: Int,modifier: Modifier,openTrips: ()->Unit,finishTrip: ()->Unit,
    gpsStatus: GpsStatus = GpsStatus.OFF,recordingFailed: Boolean = false) {
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Insights,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.tile_trip_session),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                if(gpsPoints>0) Text(stringResource(R.string.trip_gps_points,gpsPoints),style=MaterialTheme.typography.labelMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                trip.startedAt?.let {Text(V12Protocol.formatDuration((trip.movingMs/1000)),style=MaterialTheme.typography.labelLarge,fontFamily=IonSans,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            if(trip.samples>0) Text(stringResource(when(gpsStatus) {
                GpsStatus.OFF -> R.string.gps_off
                GpsStatus.PAUSED -> R.string.gps_paused
                GpsStatus.PRECISE_REQUIRED -> R.string.gps_precise
                GpsStatus.DISABLED -> R.string.gps_disabled
                GpsStatus.WAITING -> R.string.gps_waiting
                GpsStatus.RECORDING -> R.string.gps_recording
                GpsStatus.ERROR -> R.string.gps_error
            }),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(recordingFailed) Banner(stringResource(R.string.trip_record_failed),Icons.Rounded.ErrorOutline,warning=true)
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
            // History has its own dock tab; only finishing the ride stays here.
            if(trip.distanceKm>=TripSummary.MIN_DISTANCE_KM) FilledTonalButton(onClick=finishTrip,modifier=Modifier.heightIn(min=48.dp)) {
                Icon(Icons.Rounded.Flag,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trip_finish))
            }
        }
    }
}

@Composable internal fun TripFigure(label: String,value: String,unit: String,modifier: Modifier) {
    val secondary=LocalContentColor.current.copy(alpha=0.78f)
    // One TalkBack stop per figure ("Max, 42.1, km/h"), as for the ride tiles.
    val spoken=if(value=="—") "$label, ${stringResource(R.string.no_data)}" else "$label, $value $unit"
    Column(modifier.semantics(mergeDescendants=true) {contentDescription=spoken},verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=secondary,maxLines=1)
        Text(value,style=MaterialTheme.typography.titleLarge.copy(fontFeatureSettings="tnum"),fontFamily=IonSans,maxLines=1)
        Text(unit,style=MaterialTheme.typography.labelSmall,color=secondary)
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
    val haptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(onClick={haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm);click()},enabled=enabled,modifier=modifier,
        color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.row)) {
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
    val haptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(modifier,color=color,shape=RoundedCornerShape(radius)) {
        Row(Modifier.fillMaxWidth().heightIn(min=88.dp).toggleable(value=checked,enabled=enabled && value!=null,role=Role.Switch,onValueChange={
                haptics.performHapticFeedback(if(it) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff)
                change(it)})
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
