@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Shape of item [index] of [count] in a segmented list built from separate lazy items. */
internal fun segmentShape(index: Int,count: Int): Shape {
    val outer=IonShapes.group; val inner=IonShapes.seam
    return when {
        count<=1 -> RoundedCornerShape(outer)
        index==0 -> RoundedCornerShape(outer,outer,inner,inner)
        index==count-1 -> RoundedCornerShape(inner,inner,outer,outer)
        else -> RoundedCornerShape(inner)
    }
}

/** Ride log: lifetime totals, then every stored ride newest first, grouped by month. Per-ride exports live in the
 * detail; the overflow menu exports every ride at once as a zip of CSVs, or restores such an archive. */
@Composable internal fun TripsScreen(trips: List<TripSummary>,actions: Actions,openTrip: (TripSummary)->Unit) {
    // Keyed by startedAt so the pending question survives rotation; one tap on the bin must not lose a ride.
    var pendingDelete by rememberSaveable {mutableStateOf<Long?>(null)}
    var menu by remember {mutableStateOf(false)}
    val totals=remember(trips) {TripTotals.of(trips)}
    val months=remember(trips) {TripMonths.group(trips,java.time.ZoneId.systemDefault())}
    IonPage(stringResource(R.string.nav_history),spacing=IonSpacing.seam,actions={
        if(trips.isNotEmpty()) Box {
            IconButton(onClick={menu=true},shapes=IconButtonDefaults.shapes()) {Icon(Icons.Rounded.MoreVert,stringResource(R.string.more_options))}
            DropdownMenu(menu,{menu=false},shape=RoundedCornerShape(IonShapes.l)) {
                DropdownMenuItem(text={Text(stringResource(R.string.trips_export_all))},leadingIcon={Icon(Icons.Rounded.IosShare,null)},
                    onClick={menu=false;actions.shareAllCsv()})
                DropdownMenuItem(text={Text(stringResource(R.string.trips_import))},leadingIcon={Icon(Icons.Rounded.FileOpen,null)},
                    onClick={menu=false;actions.importTrips()})
            }
        }
    }) {
        if(trips.isEmpty()) {
            item(key="empty",contentType="note") {EmptyHistory(actions.importTrips)}
            return@IonPage
        }
        item(key="totals",contentType="totals") {TotalsCard(totals)}
        months.forEach {month ->
            item(key="month-${month.month}",contentType="month") {Box(Modifier.animateItem().padding(top=IonSpacing.item)) {MonthHeader(month)}}
            item(key="month-gap-${month.month}",contentType="gap") {Spacer(Modifier.height(6.dp))}
            itemsIndexed(month.trips,key={_,it -> "${it.startedAt}-${it.distanceKm}"},contentType={_,_ -> "trip"}) {index,trip ->
                // animateItem: a deleted ride collapses away and the rest close the gap smoothly.
                TripRow(trip,segmentShape(index,month.trips.size),open={openTrip(trip)},delete={pendingDelete=trip.startedAt},modifier=Modifier.animateItem())
            }
        }
    }
    trips.firstOrNull {it.startedAt==pendingDelete}?.let {trip ->
        AlertDialog(onDismissRequest={pendingDelete=null},icon={Icon(Icons.Rounded.DeleteOutline,null)},
            title={Text(stringResource(R.string.trips_delete_title))},
            text={Text(stringResource(R.string.trips_delete_message))},
            confirmButton={TextButton(onClick={pendingDelete=null;actions.deleteTrip(trip)}) {Text(stringResource(R.string.trips_delete))}},
            dismissButton={TextButton(onClick={pendingDelete=null}) {Text(stringResource(R.string.cancel))}})
    }
}

/** Empty state: what will appear here and the one useful action, restoring a backup. */
@Composable private fun EmptyHistory(import: ()->Unit) {
    Column(Modifier.fillMaxWidth().padding(top=48.dp,start=24.dp,end=24.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        ShapeBadge(Icons.Rounded.History,size=112.dp,polygon=MaterialShapes.Cookie12Sided,
            containerColor=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer)
        Text(stringResource(R.string.trips_empty),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalButton(onClick=import,shapes=ButtonDefaults.shapes(),modifier=Modifier.heightIn(min=48.dp)) {
            Icon(Icons.Rounded.FileOpen,null,Modifier.size(ButtonDefaults.IconSize));Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.trips_import))
        }
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

/** Lifetime totals: the distance as the hero number, rides and time in motion beside it. */
@Composable private fun TotalsCard(t: TripTotals) {
    val scheme=MaterialTheme.colorScheme
    Surface(color=scheme.primaryContainer,contentColor=scheme.onPrimaryContainer,shape=IonShapes.card) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            val km=stringResource(R.string.unit_km)
            val total=stringResource(R.string.trips_total)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants=true) {contentDescription="$total, ${t.distanceKm.fmt(1)} $km"}) {
                    Text(total,style=MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(t.distanceKm.fmt(1),style=MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings="tnum"),maxLines=1,softWrap=false,
                            autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=androidx.compose.ui.unit.TextUnit(20f,androidx.compose.ui.unit.TextUnitType.Sp),
                                maxFontSize=MaterialTheme.typography.displaySmallEmphasized.fontSize),modifier=Modifier.weight(1f,fill=false))
                        Text(km,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=6.dp))
                    }
                }
                ShapeBadge(Icons.Rounded.Route,size=56.dp,polygon=MaterialShapes.Cookie9Sided,containerColor=scheme.primary,contentColor=scheme.onPrimary)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TotalChip(stringResource(R.string.trips_count),t.count.toString(),Icons.Rounded.TripOrigin,Modifier.weight(1f))
                TotalChip(stringResource(R.string.trip_moving),V12Protocol.formatDuration(t.movingMs/1000),Icons.Rounded.Timer,Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun TotalChip(label: String,value: String,icon: androidx.compose.ui.graphics.vector.ImageVector,modifier: Modifier) {
    Surface(modifier.semantics(mergeDescendants=true) {contentDescription="$label, $value"},color=MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha=.55f),
        shape=RoundedCornerShape(IonShapes.l)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Column {
                Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(value,style=MaterialTheme.typography.titleMediumEmphasized.copy(fontFeatureSettings="tnum"),color=MaterialTheme.colorScheme.onSurface,maxLines=1,
                    softWrap=false,autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=androidx.compose.ui.unit.TextUnit(10f,androidx.compose.ui.unit.TextUnitType.Sp),
                        maxFontSize=MaterialTheme.typography.titleMediumEmphasized.fontSize))
            }
        }
    }
}

/** One ride: the day in a badge, time and pace, distance at the end. Tap opens it; long press (or the TalkBack
 * action) offers deletion behind a confirmation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun TripRow(trip: TripSummary,shape: Shape,open: ()->Unit,delete: ()->Unit,modifier: Modifier=Modifier) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date=remember(trip.startedAt,locale) {DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,locale).format(Date(trip.startedAt))}
    val zoned=remember(trip.startedAt) {java.time.Instant.ofEpochMilli(trip.startedAt).atZone(java.time.ZoneId.systemDefault())}
    val day=zoned.dayOfMonth.toString()
    val weekday=remember(zoned,locale) {zoned.format(java.time.format.DateTimeFormatter.ofPattern("EEE",locale)).replaceFirstChar {it.titlecase(locale)}}
    val time=remember(trip.startedAt,locale) {DateFormat.getTimeInstance(DateFormat.SHORT,locale).format(Date(trip.startedAt))}
    val haptics=LocalHapticFeedback.current
    val deleteLabel=stringResource(R.string.trips_delete_one,date)
    val kmh=stringResource(R.string.unit_kmh)
    val km=stringResource(R.string.unit_km)
    Surface(modifier.fillMaxWidth(),shape=shape,color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp)
            .semantics {customActions=listOf(CustomAccessibilityAction(deleteLabel) {delete();true})}
            .combinedClickable(role=Role.Button,onClickLabel=stringResource(R.string.trips_details),
                onLongClick={haptics.performHapticFeedback(HapticFeedbackType.LongPress);delete()},onClick=open)
            .padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(Modifier.size(44.dp),shape=MaterialShapes.Cookie6Sided.toShape(),color=MaterialTheme.colorScheme.secondaryContainer,
                contentColor=MaterialTheme.colorScheme.onSecondaryContainer) {
                Box(contentAlignment=Alignment.Center) {Text(day,style=MaterialTheme.typography.titleMediumEmphasized.copy(fontFeatureSettings="tnum"))}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text("$weekday, $time",style=MaterialTheme.typography.titleMediumEmphasized,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${V12Protocol.formatDuration(trip.durationMs/1000)} · ${trip.avgSpeed.fmt(1)} $kmh",
                    style=MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings="tnum"),color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
            Text("${trip.distanceKm.fmt(2)} $km",style=MaterialTheme.typography.titleMediumEmphasized.copy(fontFeatureSettings="tnum"),
                color=MaterialTheme.colorScheme.primary,maxLines=1,softWrap=false)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
