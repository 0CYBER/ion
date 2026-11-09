package com.freecyber.ion

import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Native OSM tiles with disk cache, a route and a marker linked to the ride scrubber. */
@Composable internal fun TripMap(track: List<TrackPoint>, index: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val owner = LocalLifecycleOwner.current
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val markerColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val startTitle=stringResource(R.string.trip_map_start)
    val endTitle=stringResource(R.string.trip_map_end)
    val positionTitle=stringResource(R.string.trip_map_position)
    val route = remember(track) { Track.thin(track).map { GeoPoint(it.lat,it.lon) } }
    val map = remember(context,owner,track,startTitle,endTitle) {
        Configuration.getInstance().apply {
            userAgentValue = "Ion/com.freecyber.ion"
            osmdroidBasePath = File(context.cacheDir,"osm")
            osmdroidTileCache = File(context.cacheDir,"osm/tiles")
        }
        MapView(context).apply {
            // The lazy item can temporarily detach. AndroidView owns the final release.
            setDestroyMode(false)
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel=2.0; maxZoomLevel=19.0
            controller.setZoom(14.0)
            controller.setCenter(route.first())
            overlays.add(Polyline(this).apply { setPoints(route); outlinePaint.color=color; outlinePaint.strokeWidth=8f })
            // Native markers have info windows and remain visible when tiles are unavailable.
            overlays.add(Marker(this).apply { position=route.first(); title=startTitle; icon=mapDot(context,startColor); setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER) })
            overlays.add(Marker(this).apply { position=route.last(); title=endTitle; icon=mapDot(context,color); setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER) })
            setOnTouchListener { view,event ->
                view.parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
                false
            }
            // A posted task can run before this lazy item is laid out. osmdroid can loop
            // indefinitely if it computes the projection with non-positive content dimensions.
            doOnLayout { view ->
                val shortestSide = minOf(view.width, view.height)
                if (shortestSide > 0) {
                    val padding = (shortestSide / 8).coerceAtMost(48)
                    zoomToBoundingBox(BoundingBox.fromGeoPoints(route), false, padding, 18.0, null)
                }
            }
        }
    }
    val marker=remember(map) { Marker(map).apply {
        setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
        icon=mapDot(context,markerColor)
        title=positionTitle
        map.overlays.add(this)
    } }
    val observer = remember(map) {
        LifecycleEventObserver { _,event ->
            when(event) { Lifecycle.Event.ON_RESUME -> map.onResume(); Lifecycle.Event.ON_PAUSE -> map.onPause(); else -> Unit }
        }
    }
    Column {
        key(map) {
            AndroidView(
                factory = { map.also { owner.lifecycle.addObserver(observer) } },
                modifier = modifier.clipToBounds(),
                onRelease = {
                    owner.lifecycle.removeObserver(observer)
                    it.onPause()
                    it.onDetach()
                },
                update = {
                    (it.overlays.first() as Polyline).outlinePaint.color = color
                    marker.title = positionTitle
                    val point = track[index.coerceIn(0, track.lastIndex)]
                    marker.position = GeoPoint(point.lat, point.lon)
                    it.invalidate()
                },
            )
        }
        Text("© OpenStreetMap contributors",style=MaterialTheme.typography.labelSmall,
            modifier=Modifier.clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") })
        Text(stringResource(R.string.trip_map_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun mapDot(context: android.content.Context, color: Int) = android.graphics.drawable.GradientDrawable().apply {
    shape=android.graphics.drawable.GradientDrawable.OVAL
    setColor(color)
    val density=context.resources.displayMetrics.density
    setSize((16*density).toInt(),(16*density).toInt())
    setStroke((2*density).toInt(),android.graphics.Color.WHITE)
}
