@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

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
import androidx.compose.ui.unit.dp
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
    LaunchedEffect(startedAt,retry) {
        failed=false
        try {
            val loaded=withContext(Dispatchers.IO) { actions.tripTrack(trip.startedAt) to actions.tripHasTelemetry(trip.startedAt) }
            track=loaded.first; hasCsv=loaded.second
        } catch(e: CancellationException) {throw e}
        catch(_: java.io.IOException) {failed=true}
    }
    if(track==null) {
        Column(Modifier.fillMaxSize().padding(PagePadding),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DetailHeading(stringResource(R.string.trips_title),"",onBack)
            if(failed) {
                Banner(stringResource(R.string.trip_load_failed),Icons.Rounded.ErrorOutline,warning=true)
                TextButton(onClick={retry++}) {Text(stringResource(R.string.trip_retry))}
            } else CircularProgressIndicator()
        }
    } else TripDetailScreen(trip,track!!,{actions.shareGpx(trip)},{actions.deleteTrip(trip)},onBack,
        shareCsv={actions.shareCsv(trip)},hasCsv=hasCsv)
}

/** One archived ride: the whole ride's stats, its GPS route with a scrubber showing the stats at
 * any moment of the ride, GPX export and delete. */
@Composable internal fun TripDetailScreen(trip: TripSummary,track: List<TrackPoint>,shareGpx: ()->Unit,delete: ()->Unit,onBack: ()->Unit,
    shareCsv: ()->Unit = {},hasCsv: Boolean = false) {
    var confirmDelete by rememberSaveable {mutableStateOf(false)}
    val date=remember(trip.startedAt) {DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(trip.startedAt))}
    val cum=remember(track) {Track.cumulativeKm(track)}
    var pick by rememberSaveable(track.size) {mutableFloatStateOf((track.size-1).coerceAtLeast(0).toFloat())}
    val idx=pick.roundToInt().coerceIn(0,(track.size-1).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {DetailHeading(date,V12Protocol.formatDuration(trip.durationMs/1000),onBack)}
        item(key="stats",contentType="stats") {TripStatsCard(trip)}
        item(key="route",contentType="route") {
            if(track.size<2) Banner(stringResource(R.string.trips_track_missing),Icons.Rounded.LocationOff)
            else Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.trips_track),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.trips_track_length,Track.distanceKm(track).fmt(2)),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TripMap(track,idx,Modifier.fillMaxWidth().height(300.dp))
                    Slider(value=pick,onValueChange={pick=it},valueRange=0f..(track.size-1).coerceAtLeast(1).toFloat())
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        TripFigure(stringResource(R.string.trip_point_time),V12Protocol.formatDuration(track[idx].t/1000),"",Modifier.weight(1f))
                        TripFigure(stringResource(R.string.trip_point_speed),track[idx].speed?.let {(it*3.6).fmt(1)} ?: "—",
                            stringResource(R.string.unit_kmh),Modifier.weight(1f))
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        TripFigure(stringResource(R.string.trip_point_distance),cum[idx].fmt(2),stringResource(R.string.unit_km),Modifier.weight(1f))
                        TripFigure(stringResource(R.string.trip_point_alt),track[idx].alt?.fmt(0) ?: "—",stringResource(R.string.unit_m),Modifier.weight(1f))
                    }
                }
            }
        }
        item(key="actions",contentType="action") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
              Text(stringResource(R.string.trip_export_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
              if(!hasCsv) Text(stringResource(R.string.trip_csv_missing),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
              Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick=shareCsv,enabled=hasCsv,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.IosShare,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_export))
                }
                FilledTonalButton(onClick=shareGpx,enabled=track.size>=2,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.IosShare,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_export_gpx))
                }
              }
                TextButton(onClick={confirmDelete=true},modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_delete))
                }
            }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(stringResource(R.string.trips_delete_title))},
        text={Text(stringResource(R.string.trips_delete_message))},
        confirmButton={TextButton(onClick={confirmDelete=false;delete()}) {Text(stringResource(R.string.trips_delete))}},
        dismissButton={TextButton(onClick={confirmDelete=false}) {Text(stringResource(R.string.cancel))}})
}

@Composable private fun TripStatsCard(trip: TripSummary) {
    val ion=ionColors()
    Surface(color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.trip_distance),style=MaterialTheme.typography.labelLarge)
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(trip.distanceKm.fmt(2),style=MaterialTheme.typography.displayMedium,fontFamily=IonSans)
                Text(stringResource(R.string.unit_km),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=8.dp))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trip_moving),V12Protocol.formatDuration(trip.movingMs/1000),"",Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_max),trip.maxSpeed.fmt(1),stringResource(R.string.unit_kmh),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_avg),trip.avgSpeed.fmt(1),stringResource(R.string.unit_kmh),Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trip_energy),(trip.energyWh-trip.regenWh).fmt(0),stringResource(R.string.unit_wh),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_consumption),trip.consumption?.fmt(1) ?: "—",stringResource(R.string.unit_wh_km),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_battery_used),trip.batteryUsed?.fmt(0) ?: "—",stringResource(R.string.unit_percent),Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trip_max_power),trip.maxPower?.fmt(0) ?: "—",stringResource(R.string.unit_watts),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_avg_power),trip.avgPower?.fmt(0) ?: "—",stringResource(R.string.unit_watts),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_max_pwm),trip.maxPwm?.fmt(1) ?: "—",stringResource(R.string.unit_percent),Modifier.weight(1f))
            }
            Text(stringResource(R.string.trip_power_hint),style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trip_max_current),trip.maxCurrent?.fmt(1) ?: "—",stringResource(R.string.unit_amps),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_min_voltage),trip.minVoltage?.fmt(1) ?: "—",stringResource(R.string.unit_volts),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_max_temp),trip.maxTemp?.toString() ?: "—",stringResource(R.string.unit_celsius),Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trip_regen),trip.regenWh.fmt(1),stringResource(R.string.unit_wh),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_stopped),V12Protocol.formatDuration((trip.durationMs-trip.movingMs).coerceAtLeast(0)/1000),"",Modifier.weight(1f))
            }
        }
    }
}
