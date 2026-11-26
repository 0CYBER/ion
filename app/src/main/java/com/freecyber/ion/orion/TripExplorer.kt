package com.freecyber.ion.orion

import com.freecyber.ion.*
import com.freecyber.ion.R

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

/** Chart series of the ride explorer; each is scaled to its own range so all fit one plot. */
internal enum class RideSeries(val label: Int) {
    SPEED(R.string.trip_point_speed), POWER(R.string.trip_series_power), BATTERY(R.string.trip_series_battery), PWM(R.string.trip_series_pwm)
}

/** Everything the explorer shows, loaded once per ride. [moments] is empty for rides recorded before the CSV log. */
internal class RideData(val track: List<TrackPoint>, val moments: List<Moment>) {
    val duration = TripTimeline.duration(moments, track)
    val cumulativeKm = Track.cumulativeKm(track)
    val hasMap = track.size >= 2
    val hasChart = moments.size >= 2 || track.count { it.speed != null } >= 2
    fun trackIndex(t: Long) = TripTimeline.nearest(track, t) { it.t }
    fun momentIndex(t: Long) = TripTimeline.nearest(moments, t) { it.t }
}

@Composable private fun seriesColor(s: RideSeries): Color = when (s) {
    RideSeries.SPEED -> MaterialTheme.colorScheme.primary
    RideSeries.POWER -> MaterialTheme.colorScheme.tertiary
    RideSeries.BATTERY -> ionColors().blue.content
    RideSeries.PWM -> MaterialTheme.colorScheme.error
}

/**
 * The ride explorer: route map, a chart you drag along the ride, and every figure at the cursor. The cursor is a
 * time on the session clock, shared by the map marker (nearest GPS fix) and the figures (nearest telemetry sample).
 * "Expand" opens the same explorer full screen.
 */
@Composable internal fun RideExplorer(data: RideData, cursor: Long, onCursor: (Long) -> Unit) {
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(listOf<String>()) }
    val visible = RideSeries.entries.filter { it.name !in hidden }
    val toggle: (RideSeries) -> Unit = { s -> hidden = if (s.name in hidden) hidden - s.name else hidden + s.name }
    val pick: (Long) -> Unit = onCursor
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(IonShapes.xl)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.trips_track), style = MaterialTheme.typography.titleMedium)
                    if (data.hasMap) Text(stringResource(R.string.trips_track_length, (data.cumulativeKm.lastOrNull() ?: 0.0).fmt(2)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (data.hasMap) FilledTonalIconButton(onClick = { fullScreen = true }, modifier = Modifier.size(48.dp).testTag("trip-expand")) {
                    Icon(Icons.Rounded.OpenInFull, stringResource(R.string.trip_expand))
                }
            }
            if (data.hasMap) TripMap(data.track, data.trackIndex(cursor).coerceAtLeast(0),
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(IonShapes.l)), pick = pick)
            else Banner(stringResource(R.string.trips_track_missing), Icons.Rounded.LocationOff)
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (data.hasChart) {
                    RideChart(data, visible, cursor, onCursor, Modifier.fillMaxWidth().height(140.dp))
                    SeriesLegend(visible, toggle)
                }
                MomentFigures(data, cursor)
            }
        }
    }
    if (fullScreen) Dialog(onDismissRequest = { fullScreen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    TripMap(data.track, data.trackIndex(cursor).coerceAtLeast(0), Modifier.fillMaxSize(), pick = pick)
                    FilledTonalIconButton(onClick = { fullScreen = false },
                        modifier = Modifier.statusBarsPadding().padding(12.dp).size(48.dp).testTag("trip-collapse")) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.trip_collapse))
                    }
                }
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (data.hasChart) {
                        RideChart(data, visible, cursor, onCursor, Modifier.fillMaxWidth().height(110.dp))
                        SeriesLegend(visible, toggle)
                    }
                    MomentFigures(data, cursor)
                }
            }
        }
    }
}

@Composable private fun SeriesLegend(visible: List<RideSeries>, toggle: (RideSeries) -> Unit) {
    // One scrolling row instead of two wrapped ones.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RideSeries.entries.forEach { s ->
            val on = s in visible
            FilterChip(selected = on, onClick = { toggle(s) }, label = { Text(stringResource(s.label)) },
                leadingIcon = { Box(Modifier.size(10.dp).background(seriesColor(s).copy(alpha = if (on) 1f else .35f), CircleShape)) })
        }
    }
}

/** Drag or tap anywhere on the plot to move the cursor; TalkBack adjusts it like a slider. */
@Composable private fun RideChart(data: RideData, visible: List<RideSeries>, cursor: Long, onCursor: (Long) -> Unit, modifier: Modifier) {
    val colors = RideSeries.entries.associateWith { seriesColor(it) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val span = data.duration.coerceAtLeast(1)
    val label = stringResource(R.string.trip_chart_cursor)
    val state = V12Protocol.formatDuration(cursor / 1000)
    val latestCursor by rememberUpdatedState(cursor)
    val move by rememberUpdatedState(onCursor)
    Box(modifier
        .testTag("trip-chart")
        .semantics {
            contentDescription = label; stateDescription = state
            progressBarRangeInfo = ProgressBarRangeInfo(cursor.toFloat(), 0f..span.toFloat())
            setProgress { v -> move(v.toLong().coerceIn(0, span)); true }
        }
        .pointerInput(span) { detectTapGestures { move((it.x / size.width * span).toLong().coerceIn(0, span)) } }
        .pointerInput(span) {
            detectHorizontalDragGestures { change, _ -> change.consume(); move((change.position.x / size.width * span).toLong().coerceIn(0, span)) }
        }
        .drawWithCache {
            fun x(t: Long) = t.toFloat() / span * size.width
            val h = size.height
            // Each series in its own scale: speed and power to their peaks, battery and PWM as 0–100 %.
            fun path(points: List<Pair<Long, Double>>, top: Double): Path? {
                if (points.size < 2 || top <= 0) return null
                return Path().apply {
                    points.forEachIndexed { i, (t, v) ->
                        val y = h - (v / top).coerceIn(0.0, 1.0).toFloat() * h
                        if (i == 0) moveTo(x(t), y) else lineTo(x(t), y)
                    }
                }
            }
            val m = data.moments
            val speed = if (m.size >= 2) m.map { it.t to abs(it.speed) } else data.track.mapNotNull { p -> p.speed?.let { p.t to it * 3.6 } }
            val speedTop = (speed.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(10.0) * 1.1
            val power = m.mapNotNull { s -> s.power?.let { s.t to it.coerceAtLeast(0.0) } }
            val paths = mapOf(
                RideSeries.SPEED to path(speed, speedTop),
                RideSeries.POWER to path(power, (power.maxOfOrNull { it.second } ?: 0.0) * 1.1),
                RideSeries.BATTERY to path(m.mapNotNull { s -> s.battery?.let { s.t to it } }, 100.0),
                RideSeries.PWM to path(m.mapNotNull { s -> s.pwm?.let { s.t to abs(it) } }, 100.0),
            )
            val area = paths[RideSeries.SPEED]?.let { line ->
                Path().apply { addPath(line); lineTo(x(speed.last().first), h); lineTo(x(speed.first().first), h); close() }
            }
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val thick = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val hair = 1.dp.toPx(); val cursorWidth = 2.dp.toPx(); val knob = 5.dp.toPx()
            onDrawBehind {
                for (i in 1..3) drawLine(grid.copy(alpha = .5f), Offset(0f, h * i / 4), Offset(size.width, h * i / 4), hair)
                if (RideSeries.SPEED in visible) area?.let { drawPath(it, colors.getValue(RideSeries.SPEED).copy(alpha = .16f)) }
                // Speed last, so the main line sits on top.
                listOf(RideSeries.BATTERY, RideSeries.PWM, RideSeries.POWER, RideSeries.SPEED).filter { it in visible }.forEach { s ->
                    paths[s]?.let { drawPath(it, colors.getValue(s), style = if (s == RideSeries.SPEED) thick else stroke) }
                }
                val cx = x(latestCursor)
                drawLine(cursorColor, Offset(cx, 0f), Offset(cx, h), cursorWidth)
                drawCircle(cursorColor, knob, Offset(cx, knob))
            }
        })
}

/** The figures at the cursor, four to a row, from the nearest telemetry sample and GPS fix. */
@Composable private fun MomentFigures(data: RideData, cursor: Long) {
    val ti = data.trackIndex(cursor)
    val m = data.moments.getOrNull(data.momentIndex(cursor)); val p = data.track.getOrNull(ti)
    val dash = "—"
    val kmh = stringResource(R.string.unit_kmh); val pct = stringResource(R.string.unit_percent); val c = stringResource(R.string.unit_celsius)
    val distance = m?.distanceKm ?: data.cumulativeKm.getOrNull(ti)
    FigureGrid(listOf(
        Figure(stringResource(R.string.trip_point_time), V12Protocol.formatDuration(cursor / 1000), ""),
        Figure(stringResource(R.string.trip_point_speed), m?.speed?.let { abs(it).fmt(1) } ?: p?.speed?.let { (it * 3.6).fmt(1) } ?: dash, kmh),
        Figure(stringResource(R.string.trip_point_gps_speed), p?.speed?.let { (it * 3.6).fmt(1) } ?: dash, kmh),
        Figure(stringResource(R.string.trip_point_distance), distance?.fmt(2) ?: dash, stringResource(R.string.unit_km)),
        Figure(stringResource(R.string.trip_series_battery), m?.battery?.fmt(0) ?: dash, pct),
        Figure(stringResource(R.string.trip_point_voltage), m?.voltage?.fmt(1) ?: dash, stringResource(R.string.unit_volts)),
        Figure(stringResource(R.string.trip_point_current), m?.current?.fmt(1) ?: dash, stringResource(R.string.unit_amps)),
        Figure(stringResource(R.string.trip_series_power), m?.power?.fmt(0) ?: dash, stringResource(R.string.unit_watts)),
        Figure(stringResource(R.string.trip_series_pwm), m?.pwm?.let { abs(it).fmt(1) } ?: dash, pct),
        Figure(stringResource(R.string.trip_point_mos), m?.mosTemp?.toString() ?: dash, c),
        Figure(stringResource(R.string.trip_point_motor), m?.motorTemp?.toString() ?: dash, c),
        Figure(stringResource(R.string.trip_point_alt), p?.alt?.fmt(0) ?: dash, stringResource(R.string.unit_m)),
    ))
}
