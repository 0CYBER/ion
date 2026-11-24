@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import java.text.DateFormat
import java.util.Date

/** Ride log: lifetime totals, then every stored ride newest first. Per-ride exports live in the detail; the log
 * itself exports every ride at once as a zip of CSVs. */
@Composable internal fun TripsScreen(trips: List<TripSummary>,actions: Actions,openTrip: (TripSummary)->Unit) {
    // Keyed by startedAt so the pending question survives rotation; one tap on the bin must not lose a ride.
    var pendingDelete by rememberSaveable {mutableStateOf<Long?>(null)}
    val totals=remember(trips) {TripTotals.of(trips)}
    val months=remember(trips) {TripMonths.group(trips,java.time.ZoneId.systemDefault())}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {
            // A dock tab: just the page title, like the other tabs.
            Text(stringResource(R.string.nav_history),style=IonType.page,color=MaterialTheme.colorScheme.onBackground,
                modifier=Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp).semantics {heading()})
        }
        if(trips.isEmpty()) {
            item(key="empty",contentType="note") {Banner(stringResource(R.string.trips_empty),Icons.Rounded.History)}
            // A fresh install (or a new phone) restores its rides from here.
            item(key="import",contentType="actions") {ImportButton(actions.importTrips)}
            return@LazyColumn
        }
        item(key="totals",contentType="totals") {TotalsCard(totals)}
        item(key="actions",contentType="actions") {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick=actions.shareAllCsv,modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.IosShare,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_export_all))
                }
                ImportButton(actions.importTrips)
            }
        }
        months.forEach {month ->
            item(key="month-${month.month}",contentType="month") {Box(Modifier.animateItem()) {MonthHeader(month)}}
            items(month.trips,key={"${it.startedAt}-${it.distanceKm}"},contentType={"trip"}) {trip ->
                // animateItem: a deleted ride collapses away and the rest close the gap smoothly.
                TripCard(trip,open={openTrip(trip)},delete={pendingDelete=trip.startedAt},modifier=Modifier.animateItem())}
        }
    }
    trips.firstOrNull {it.startedAt==pendingDelete}?.let {trip ->
        AlertDialog(onDismissRequest={pendingDelete=null},title={Text(stringResource(R.string.trips_delete_title))},
            text={Text(stringResource(R.string.trips_delete_message))},
            confirmButton={TextButton(onClick={pendingDelete=null;actions.deleteTrip(trip)}) {Text(stringResource(R.string.trips_delete))}},
            dismissButton={TextButton(onClick={pendingDelete=null}) {Text(stringResource(R.string.cancel))}})
    }
}

@Composable private fun ImportButton(onClick: ()->Unit) {
    TextButton(onClick=onClick,modifier=Modifier.heightIn(min=48.dp)) {
        Icon(Icons.Rounded.FileOpen,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.trips_import))
    }
}

/** "November 2026 · 42.1 km": a long log stays scannable and each month shows how much was ridden. */
@Composable private fun MonthHeader(month: TripMonth) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val name=remember(month.month,locale) {
        month.month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy",locale)).replaceFirstChar {it.titlecase(locale)}
    }
    SectionHeader(stringResource(R.string.trips_month,name,month.distanceKm.fmt(1)))
}

@Composable private fun TotalsCard(t: TripTotals) {
    val ion=ionColors()
    val km=stringResource(R.string.unit_km)
    Surface(color=ion.hero,contentColor=ion.onHero,shape=IonShapes.hero) {
        // One compact row: the lifetime distance, then how many rides and how long in motion. Every value is one line
        // that shrinks to fit, so "1234,5 km" or "123:45:06" never wraps or clips.
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=18.dp),verticalAlignment=Alignment.Bottom,
            horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            TotalFigure(stringResource(R.string.trips_total),t.distanceKm.fmt(1),km,MaterialTheme.typography.headlineLarge,Modifier.weight(1.25f))
            TotalFigure(stringResource(R.string.trips_count),t.count.toString(),"",MaterialTheme.typography.titleLarge,Modifier.weight(0.7f))
            TotalFigure(stringResource(R.string.trip_moving),V12Protocol.formatDuration(t.movingMs/1000),"",MaterialTheme.typography.titleLarge,Modifier.weight(1.05f))
        }
    }
}

@Composable private fun TotalFigure(label: String,value: String,unit: String,style: androidx.compose.ui.text.TextStyle,modifier: Modifier) {
    val secondary=LocalContentColor.current.copy(alpha=0.78f)
    Column(modifier.semantics(mergeDescendants=true) {contentDescription="$label, $value $unit"},verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=secondary,maxLines=1,
            autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=9.sp,maxFontSize=MaterialTheme.typography.labelMedium.fontSize))
        val valueStyle=style.copy(fontFeatureSettings="tnum",fontFamily=IonSans)
        Text(androidx.compose.ui.text.buildAnnotatedString {
                append(value)
                if(unit.isNotEmpty()) {
                    append("\u00a0")
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontSize=MaterialTheme.typography.titleMedium.fontSize)); append(unit); pop()
                }
            },style=valueStyle,maxLines=1,softWrap=false,
            autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=12.sp,maxFontSize=valueStyle.fontSize))
    }
}

@Composable private fun TripCard(trip: TripSummary,open: ()->Unit,delete: ()->Unit,modifier: Modifier=Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = remember(trip.startedAt, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(trip.startedAt))
    }
    val expansion = stringResource(if (expanded) R.string.trips_expanded else R.string.trips_collapsed)
    val motion = MaterialTheme.motionScheme
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, motion.fastSpatialSpec(), label = "trip-chevron")
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(IonShapes.xl)) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .semantics { stateDescription = expansion }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(date, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${trip.distanceKm.fmt(2)}\u00A0${stringResource(R.string.unit_km)}",
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(arrow))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TripFigure(stringResource(R.string.trip_duration), V12Protocol.formatDuration(trip.durationMs / 1000), "", Modifier.weight(1f))
                        TripFigure(stringResource(R.string.trip_max), trip.maxSpeed.fmt(1), stringResource(R.string.unit_kmh), Modifier.weight(1f))
                        TripFigure(stringResource(R.string.trip_avg), trip.avgSpeed.fmt(1), stringResource(R.string.unit_kmh), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = open) { Text(stringResource(R.string.trips_details)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.trips_delete_one, date)) }
                    }
                }
            }
        }
    }
}
