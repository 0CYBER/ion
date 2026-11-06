@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/** Opens the detail for a trip id; leaves the screen when the trip was deleted while open. */
@Composable internal fun TripDetailRoute(startedAt: Long?,trips: List<TripSummary>,actions: Actions,onBack: ()->Unit) {
    val trip=startedAt?.let {at -> trips.firstOrNull {it.startedAt==at}}
    if(trip==null) { LaunchedEffect(startedAt) {onBack()}; return }
    TripDetailScreen(trip,actions.tripTrack(trip.startedAt),{actions.deleteTrip(trip)},onBack)
}

/** One archived ride: the whole ride's stats, its GPS route with a scrubber showing the stats at
 * any moment of the ride, and delete. */
@Composable internal fun TripDetailScreen(trip: TripSummary,track: List<TrackPoint>,delete: ()->Unit,onBack: ()->Unit) {
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
                    TrackCanvas(track,idx,Modifier.fillMaxWidth().height(240.dp))
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
        item(key="delete",contentType="action") {
            TextButton(onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Icon(Icons.Rounded.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_delete))
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
                Text(trip.distanceKm.fmt(2),style=MaterialTheme.typography.displayMedium,fontFamily=IonMono)
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
        }
    }
}

/** The route drawn to scale (metre projection at the track's mid latitude) with start and scrubber dots. */
@Composable private fun TrackCanvas(track: List<TrackPoint>,marker: Int,modifier: Modifier) {
    val line=MaterialTheme.colorScheme.primary
    val start=MaterialTheme.colorScheme.tertiary
    val dot=MaterialTheme.colorScheme.onSurface
    val minLat=track.minOf {it.lat}; val maxLat=track.maxOf {it.lat}
    val minLon=track.minOf {it.lon}; val maxLon=track.maxOf {it.lon}
    val mPerLon=111_320.0*cos(Math.toRadians((minLat+maxLat)/2))
    val widthM=((maxLon-minLon)*mPerLon).coerceAtLeast(1.0)
    val heightM=((maxLat-minLat)*111_320.0).coerceAtLeast(1.0)
    Canvas(modifier) {
        val pad=min(size.width,size.height)*0.08f
        val availW=(size.width-2*pad).coerceAtLeast(1f); val availH=(size.height-2*pad).coerceAtLeast(1f)
        val scale=min((availW/widthM).toFloat(),(availH/heightM).toFloat())
        fun xy(p: TrackPoint): Offset {
            val x=pad+((p.lon-minLon)*mPerLon*scale).toFloat()+(availW-(widthM*scale).toFloat())/2
            val y=pad+((maxLat-p.lat)*111_320.0*scale).toFloat()+(availH-(heightM*scale).toFloat())/2
            return Offset(x,y)
        }
        val path=Path()
        track.forEachIndexed {i,p ->
            val o=xy(p); if(i==0) path.moveTo(o.x,o.y) else path.lineTo(o.x,o.y)
        }
        drawPath(path,line,style=Stroke(width=6f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawCircle(start,9f,xy(track.first()))
        drawCircle(dot,11f,xy(track[marker.coerceIn(0,track.lastIndex)]))
        drawCircle(line,6f,xy(track[marker.coerceIn(0,track.lastIndex)]))
    }
}
