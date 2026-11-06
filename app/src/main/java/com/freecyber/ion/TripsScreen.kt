@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** Ride log: lifetime totals, then every stored ride newest first, with CSV export. Tapping a ride opens it. */
@Composable internal fun TripsScreen(trips: List<TripSummary>,actions: Actions,openTrip: (TripSummary)->Unit,onBack: ()->Unit) {
    var confirmClear by rememberSaveable {mutableStateOf(false)}
    val totals=remember(trips) {TripTotals.of(trips)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {DetailHeading(stringResource(R.string.trips_title),stringResource(R.string.trips_hint),onBack)}
        if(trips.isEmpty()) {
            item(key="empty",contentType="note") {Banner(stringResource(R.string.trips_empty),Icons.Rounded.History)}
            return@LazyColumn
        }
        item(key="totals",contentType="totals") {TotalsCard(totals)}
        item(key="actions",contentType="actions") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick=actions.shareTrips,modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.IosShare,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_export))
                }
                TextButton(onClick={confirmClear=true},modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.DeleteSweep,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_clear))
                }
            }
        }
        items(trips,key={"${it.startedAt}-${it.distanceKm}"},contentType={"trip"}) {trip ->
            TripCard(trip,open={openTrip(trip)},delete={actions.deleteTrip(trip)})}
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text(stringResource(R.string.trips_clear_title))},
        text={Text(stringResource(R.string.trips_clear_message))},
        confirmButton={TextButton(onClick={confirmClear=false;actions.clearTrips()}) {Text(stringResource(R.string.trips_clear))}},
        dismissButton={TextButton(onClick={confirmClear=false}) {Text(stringResource(R.string.cancel))}})
}

@Composable private fun TotalsCard(t: TripTotals) {
    val ion=ionColors()
    Surface(color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.trips_total),style=MaterialTheme.typography.labelLarge)
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(t.distanceKm.fmt(1),style=MaterialTheme.typography.displayMedium,fontFamily=IonMono)
                Text(stringResource(R.string.unit_km),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=8.dp))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trips_count),t.count.toString(),"",Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_moving),V12Protocol.formatDuration(t.movingMs/1000),"",Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_max),t.maxSpeed.fmt(1),stringResource(R.string.unit_kmh),Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun TripCard(trip: TripSummary,open: ()->Unit,delete: ()->Unit) {
    val date=remember(trip.startedAt) {DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(trip.startedAt))}
    Surface(onClick=open,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
        Column(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=12.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date,style=MaterialTheme.typography.titleMedium)
                    Text(V12Protocol.formatDuration(trip.durationMs/1000),style=MaterialTheme.typography.labelLarge,fontFamily=IonMono,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=delete) {Icon(Icons.Rounded.DeleteOutline,stringResource(R.string.trips_delete))}
            }
            Row(Modifier.padding(end=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trip_distance),trip.distanceKm.fmt(2),stringResource(R.string.unit_km),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_max),trip.maxSpeed.fmt(1),stringResource(R.string.unit_kmh),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_avg),trip.avgSpeed.fmt(1),stringResource(R.string.unit_kmh),Modifier.weight(1f))
            }
            Row(Modifier.padding(end=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TripFigure(stringResource(R.string.trip_consumption),trip.consumption?.fmt(1) ?: "—",stringResource(R.string.unit_wh_km),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_battery_used),trip.batteryUsed?.fmt(0) ?: "—",stringResource(R.string.unit_percent),Modifier.weight(1f))
                TripFigure(stringResource(R.string.trip_max_temp),trip.maxTemp?.toString() ?: "—",stringResource(R.string.unit_celsius),Modifier.weight(1f))
            }
        }
    }
}
