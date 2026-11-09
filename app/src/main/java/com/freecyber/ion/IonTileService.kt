package com.freecyber.ion

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Quick Settings tile: one tap connects to the saved wheel or ends the session; the subtitle mirrors the link. */
class IonTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val graph = ionGraph
        job?.cancel()
        job = graph.scope.launch {
            combine(graph.ble.state, graph.monitor.reconnecting, graph.saved) { s, retry, saved -> Triple(s, retry, saved) }
                .collect { (s, retry, saved) -> render(s, retry, saved != null) }
        }
    }

    override fun onStopListening() { job?.cancel(); job = null; super.onStopListening() }

    override fun onClick() {
        super.onClick()
        val graph = ionGraph
        val s = graph.ble.state.value
        val busy = s.phase !in listOf(Phase.OFFLINE, Phase.ERROR, Phase.SCANNING) || graph.monitor.reconnecting.value
        val saved = graph.saved.value
        when {
            busy -> graph.monitor.userDisconnect()
            saved != null && graph.ble.hasPermissions() -> {
                graph.monitor.userConnect(saved)
                WheelService.ensureRunning(this)
            }
            else -> openApp()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(s: WheelState, reconnecting: Boolean, hasSaved: Boolean) {
        val tile = qsTile ?: return
        val live = s.verifiedHt && s.phase == Phase.CONNECTED
        val connecting = !live && (reconnecting || s.phase in listOf(Phase.CONNECTING, Phase.DISCOVERING, Phase.IDENTIFYING))
        tile.state = when {
            live || connecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.qs_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ion)
        val subtitle = when {
            live -> s.telemetry?.let { t ->
                val battery = t.battery.takeIf { it.isFinite() && it in 0.0..100.0 }?.roundToInt()
                if (battery != null) getString(R.string.qs_live, battery, kotlin.math.abs(t.speed).fmt(0)) else getString(R.string.status_connected_short)
            } ?: getString(R.string.status_connected_short)
            reconnecting -> getString(R.string.status_reconnecting)
            connecting -> getString(R.string.qs_connecting)
            hasSaved -> getString(R.string.qs_tap_connect)
            else -> getString(R.string.qs_no_wheel)
        }
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
        tile.contentDescription = "${tile.label}: $subtitle"
        tile.updateTile()
    }
}
