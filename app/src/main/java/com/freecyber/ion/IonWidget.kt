package com.freecyber.ion

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.launch

/** Home-screen widget: battery and state at a glance, live speed and figures when there is room,
 * one-tap connect/disconnect and headlight. Rendering reads only the persisted [WidgetSnapshot]. */
class IonWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(110.dp, 60.dp)
        private val MEDIUM = DpSize(200.dp, 110.dp)
        private val LARGE = DpSize(260.dp, 180.dp)
        internal val KEY = stringPreferencesKey("snapshot")
    }
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val snap = WidgetSnapshot.decode(currentState<Preferences>()[KEY])
            val canConnect = context.ionGraph.ble.hasPermissions()
            GlanceTheme { WidgetBody(context, snap, LocalSize.current, canConnect) }
        }
    }
}

class IonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IonWidget()
}

/** Pushes snapshots to every placed widget; callers throttle, this only skips identical content. */
object IonWidgetUpdater {
    const val MIN_INTERVAL_MS = 10_000L
    @Volatile private var lastEncoded: String? = null

    fun push(context: Context, s: WheelState, trip: TripStats, reconnecting: Boolean) {
        val graph = context.ionGraph
        val snap = WidgetSnapshot.from(s, trip, reconnecting, graph.saved.value != null, System.currentTimeMillis())
        val content = snap.copy(updatedAt = 0).encode()
        if (content == lastEncoded) return
        lastEncoded = content
        graph.scope.launch {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(IonWidget::class.java)
                for (id in ids) {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                        val merged = snap.carryOver(WidgetSnapshot.decode(prefs[IonWidget.KEY]))
                        prefs.toMutablePreferences().apply { this[IonWidget.KEY] = merged.encode() }
                    }
                    IonWidget().update(context, id)
                }
            }
        }
    }
}

@Composable internal fun WidgetBody(context: Context, snap: WidgetSnapshot, size: DpSize, canConnect: Boolean = true) {
    val colors = GlanceTheme.colors
    val live = snap.state == WidgetSnapshot.State.LIVE
    val open: Action = actionStartActivity<MainActivity>()
    Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(colors.widgetBackground).padding(12.dp).clickable(open),
        verticalAlignment = Alignment.Vertical.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(context.getString(R.string.wheel_model), style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight())
            val label = context.getString(stateLabel(snap.state))
            val seen = if (snap.isStale && snap.updatedAt > 0) android.text.format.DateUtils.formatSameDayTime(
                snap.updatedAt, System.currentTimeMillis(), java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) else null
            Text(if (seen != null) context.getString(R.string.widget_state_seen, label, seen) else label,
                style = TextStyle(color = if (live) colors.primary else colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Bottom) {
            Text(snap.battery?.let { "$it%" } ?: "—", style = TextStyle(color = if (snap.isStale) colors.onSurfaceVariant else colors.onSurface,
                fontSize = 30.sp, fontWeight = FontWeight.Bold))
            if (live && size.width >= 200.dp) {
                Spacer(GlanceModifier.width(12.dp))
                Text(context.getString(R.string.widget_speed, (snap.speed ?: 0.0).fmt(1)),
                    style = TextStyle(color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
        }
        if (size.height >= 110.dp) {
            snap.battery?.let {
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(it / 100f, GlanceModifier.fillMaxWidth().height(4.dp),
                    color = if (snap.isStale) colors.outline else colors.primary, backgroundColor = colors.surfaceVariant)
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(context.getString(R.string.widget_figures, snap.rangeKm?.fmt(1) ?: "—", snap.tripKm?.fmt(1) ?: "—",
                snap.temperature?.toString() ?: "—"), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
        }
        if (size.height >= 110.dp && snap.hasSavedWheel) {
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                val connectAction = if (live || snap.state == WidgetSnapshot.State.CONNECTING || snap.state == WidgetSnapshot.State.RECONNECTING)
                    WheelService.ACTION_DISCONNECT else WheelService.ACTION_CONNECT
                Button(context.getString(if (connectAction == WheelService.ACTION_CONNECT) R.string.connect else R.string.disconnect),
                    if (connectAction == WheelService.ACTION_CONNECT && !canConnect) open
                    else actionStartService(Intent(context, WheelService::class.java).setAction(connectAction), isForegroundService = true),
                    modifier = GlanceModifier.defaultWeight())
                if (live && size.width >= 200.dp) {
                    Spacer(GlanceModifier.width(8.dp))
                    Button(context.getString(if (snap.lowBeam) R.string.action_light_off else R.string.action_light_on),
                        actionStartService(Intent(context, WheelService::class.java).setAction(WheelService.ACTION_TOGGLE_LIGHT), isForegroundService = true),
                        modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

private fun stateLabel(state: WidgetSnapshot.State) = when (state) {
    WidgetSnapshot.State.LIVE -> R.string.status_live
    WidgetSnapshot.State.CONNECTING -> R.string.status_busy
    WidgetSnapshot.State.RECONNECTING -> R.string.widget_reconnecting
    WidgetSnapshot.State.ERROR -> R.string.widget_lost
    WidgetSnapshot.State.OFFLINE -> R.string.status_offline
}
