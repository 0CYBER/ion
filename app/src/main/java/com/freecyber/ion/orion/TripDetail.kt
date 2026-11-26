@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion.orion

import com.freecyber.ion.*
import com.freecyber.ion.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** Opens the detail for a trip id; leaves the screen when the trip was deleted while open. */
@Composable internal fun TripDetailRoute(startedAt: Long?,trips: List<TripSummary>,actions: Actions,onBack: ()->Unit) {
    val trip=startedAt?.let {at -> trips.firstOrNull {it.startedAt==at}}
    if(trip==null) { LaunchedEffect(startedAt) {onBack()}; return }
    var retry by remember {mutableIntStateOf(0)}
    var track by remember(startedAt) {mutableStateOf<List<TrackPoint>?>(null)}
    var failed by remember(startedAt) {mutableStateOf(false)}
    var hasCsv by remember(startedAt) {mutableStateOf(false)}
    var moments by remember(startedAt) {mutableStateOf(emptyList<Moment>())}
    LaunchedEffect(startedAt,retry) {
        failed=false
        try {
            val loaded=withContext(Dispatchers.IO) {
                Triple(actions.tripTrack(trip.startedAt),actions.tripHasTelemetry(trip.startedAt),actions.tripTimeline(trip.startedAt))
            }
            moments=loaded.third; hasCsv=loaded.second; track=loaded.first
        } catch(e: CancellationException) {throw e}
        catch(_: Exception) {failed=true}
    }
    val loaded=track
    if(loaded==null) {
        Column(Modifier.fillMaxSize().padding(PagePadding),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DetailHeading(rememberTripDate(trip.startedAt),V12Protocol.formatDuration(trip.durationMs/1000),onBack)
            if(failed) {
                Banner(stringResource(R.string.trip_load_failed),Icons.Rounded.ErrorOutline,warning=true)
                TextButton(onClick={retry++}) {Text(stringResource(R.string.trip_retry))}
            } else LoadingIndicator()
        }
    } else TripDetailScreen(trip,loaded,{actions.shareGpx(trip)},{actions.deleteTrip(trip)},onBack,
        shareCsv={actions.shareCsv(trip)},hasCsv=hasCsv,moments=moments)
}

/** One archived ride: a hero with the distance and pace, energy and load, the ride explorer (map, chart and the
 * figures at any moment of the ride), exports and delete. */
@Composable internal fun TripDetailScreen(trip: TripSummary,track: List<TrackPoint>,shareGpx: ()->Unit,delete: ()->Unit,onBack: ()->Unit,
    shareCsv: ()->Unit = {},hasCsv: Boolean = false,moments: List<Moment> = emptyList()) {
    var confirmDelete by rememberSaveable {mutableStateOf(false)}
    val data=remember(track,moments) {RideData(track,moments)}
    // The cursor is a time on the ride clock; it starts at the end, like the old scrubber.
    var cursor by rememberSaveable(data.duration) {mutableLongStateOf(data.duration)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {DetailHeading(rememberTripDay(trip.startedAt),tripSpan(trip),onBack)}
        item(key="hero",contentType="stats") {TripHero(trip)}
        item(key="load",contentType="stats") {TripLoadCard(trip)}
        item(key="route",contentType="route") {
            if(!data.hasMap && !data.hasChart) Banner(stringResource(R.string.trips_track_missing),Icons.Rounded.LocationOff)
            else RideExplorer(data,cursor) {cursor=it}
        }
        item(key="actions",contentType="action") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    FilledTonalButton(onClick=shareCsv,enabled=hasCsv,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Icon(Icons.Rounded.IosShare,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_export))
                    }
                    FilledTonalButton(onClick=shareGpx,enabled=track.size>=2,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Icon(Icons.Rounded.IosShare,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_export_gpx))
                    }
                    OutlinedIconButton(onClick={confirmDelete=true},modifier=Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.DeleteOutline,stringResource(R.string.trips_delete),tint=MaterialTheme.colorScheme.error)
                    }
                }
                if(!hasCsv) Text(stringResource(R.string.trip_csv_missing),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(stringResource(R.string.trips_delete_title))},
        text={Text(stringResource(R.string.trips_delete_message))},
        confirmButton={TextButton(onClick={confirmDelete=false;delete()}) {Text(stringResource(R.string.trips_delete))}},
        dismissButton={TextButton(onClick={confirmDelete=false}) {Text(stringResource(R.string.cancel))}})
}

@Composable private fun rememberTripDate(startedAt: Long): String =
    remember(startedAt) {DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(startedAt))}

/** "Tue, 24 Nov" (with the year only for past years): short enough for one line of the page title. */
@Composable private fun rememberTripDay(startedAt: Long): String {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return remember(startedAt,locale) {
        val thisYear=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val year=java.util.Calendar.getInstance().apply {timeInMillis=startedAt}.get(java.util.Calendar.YEAR)
        val pattern=android.text.format.DateFormat.getBestDateTimePattern(locale,if(year==thisYear) "EEEdMMM" else "dMMMyyyy")
        java.text.SimpleDateFormat(pattern,locale).format(Date(startedAt)).replaceFirstChar {it.uppercase(locale)}
    }
}

/** "10:55–11:16 · 0:21:23". */
@Composable private fun tripSpan(trip: TripSummary): String {
    val context=androidx.compose.ui.platform.LocalContext.current
    return remember(trip.startedAt,trip.durationMs) {
        val time=android.text.format.DateFormat.getTimeFormat(context)
        "${time.format(Date(trip.startedAt))}–${time.format(Date(trip.startedAt+trip.durationMs))} · ${V12Protocol.formatDuration(trip.durationMs/1000)}"
    }
}

/** Distance and pace: the numbers a ride is remembered by. */
@Composable private fun TripHero(trip: TripSummary) {
    val ion=ionColors()
    Surface(color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(trip.distanceKm.fmt(2),style=MaterialTheme.typography.displayMedium.copy(fontFeatureSettings="tnum"),fontFamily=IonSans)
                Text(stringResource(R.string.unit_km),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=8.dp))
            }
            val kmh=stringResource(R.string.unit_kmh)
            FigureGrid(listOf(
                Figure(stringResource(R.string.trip_moving),V12Protocol.formatDuration(trip.movingMs/1000),""),
                Figure(stringResource(R.string.trip_stopped),V12Protocol.formatDuration((trip.durationMs-trip.movingMs).coerceAtLeast(0)/1000),""),
                Figure(stringResource(R.string.trip_avg),trip.avgSpeed.fmt(1),kmh),
                Figure(stringResource(R.string.trip_max),trip.maxSpeed.fmt(1),kmh),
            ))
        }
    }
}

/** Energy, power and the wheel's limits over the whole ride. */
@Composable private fun TripLoadCard(trip: TripSummary) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.trip_load_section),style=MaterialTheme.typography.titleMedium)
            val wh=stringResource(R.string.unit_wh);val w=stringResource(R.string.unit_watts);val pct=stringResource(R.string.unit_percent)
            FigureGrid(listOf(
                Figure(stringResource(R.string.trip_energy),(trip.energyWh-trip.regenWh).fmt(0),wh),
                Figure(stringResource(R.string.trip_consumption),trip.consumption?.fmt(1) ?: "—",stringResource(R.string.unit_wh_km)),
                Figure(stringResource(R.string.trip_battery_used),trip.batteryUsed?.fmt(0) ?: "—",pct),
                Figure(stringResource(R.string.trip_regen),trip.regenWh.fmt(1),wh),
                Figure(stringResource(R.string.trip_avg_power),trip.avgPower?.fmt(0) ?: "—",w),
                Figure(stringResource(R.string.trip_max_power),trip.maxPower?.fmt(0) ?: "—",w),
                Figure(stringResource(R.string.trip_max_current),trip.maxCurrent?.fmt(1) ?: "—",stringResource(R.string.unit_amps)),
                Figure(stringResource(R.string.trip_max_pwm),trip.maxPwm?.fmt(1) ?: "—",pct),
                Figure(stringResource(R.string.trip_max_temp),trip.maxTemp?.toString() ?: "—",stringResource(R.string.unit_celsius)),
                Figure(stringResource(R.string.trip_min_voltage),trip.minVoltage?.fmt(1) ?: "—",stringResource(R.string.unit_volts)),
            ))
        }
    }
}

internal data class Figure(val label: String,val value: String,val unit: String)

/** Ride figures four to a row (three on narrow screens or with large text). Each is a one-line label over the value
 * with its unit inline; both shrink to fit, so "1:23:45" or "Мин. напряж." never wrap or clip. */
@Composable internal fun FigureGrid(figures: List<Figure>) {
    val config=androidx.compose.ui.platform.LocalConfiguration.current
    val columns=if(config.screenWidthDp<340 || config.fontScale>1.3f) 3 else 4
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        figures.chunked(columns).forEach {row ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach {GridFigure(it,Modifier.weight(1f))}
                repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
            }
        }
    }
}

@Composable private fun GridFigure(f: Figure,modifier: Modifier) {
    val secondary=LocalContentColor.current.copy(alpha=0.72f)
    val spoken=if(f.value=="—") "${f.label}, ${stringResource(R.string.no_data)}" else "${f.label}, ${f.value} ${f.unit}"
    Column(modifier.semantics(mergeDescendants=true) {contentDescription=spoken},verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(f.label,style=MaterialTheme.typography.labelMedium,color=secondary,maxLines=1,
            autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=9.sp,maxFontSize=MaterialTheme.typography.labelMedium.fontSize))
        val value=MaterialTheme.typography.titleMedium.copy(fontFeatureSettings="tnum",fontFamily=IonSans)
        val unit=MaterialTheme.typography.labelSmall
        Text(androidx.compose.ui.text.buildAnnotatedString {
                append(f.value)
                if(f.unit.isNotEmpty() && f.value!="—") {
                    append("\u00a0")
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontSize=unit.fontSize,color=secondary)); append(f.unit); pop()
                }
            },style=value,maxLines=1,
            autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=value.fontSize))
    }
}
