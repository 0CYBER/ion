package com.freecyber.ion

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@RunWith(AndroidJUnit4::class)
class TripScreenDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun mapFitsEvenWhenTheViewportIsNarrowerThanTheOriginalPadding() {
        val track=listOf(TrackPoint(0,52.23,21.01),TrackPoint(1000,52.24,21.02))
        rule.runOnUiThread { rule.activity.setContent { IonTheme(false) {
            TripMap(track,1,Modifier.width(20.dp).height(300.dp))
        } } }
        rule.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
    }

    @Test fun detailRendersNativeMapAndExportsBelongToTheRide() {
        val trip=TripSummary(1700000000000,2400000,2100000,14.2,38.4,24.3,410.0,35.0,18.0,52,
            3200.0,650.0,85.5,35.0,82.4)
        val track=List(120) { i ->
            val angle=i/120.0*2*Math.PI
            TrackPoint(i*2000L,52.23+0.002*Math.sin(angle),21.01+0.003*Math.cos(angle),100.0,10.0)
        }
        var csv=0; var gpx=0
        rule.runOnUiThread { rule.activity.setContent { IonTheme(false) {
          Surface(Modifier.fillMaxSize()) {
            TripDetailScreen(trip,track,{gpx++},{},{},shareCsv={csv++},hasCsv=true)
          }
        } } }
        rule.onNodeWithText(rule.activity.getString(R.string.trip_max_power)).assertExists()
        screenshot("trip-stats")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("© OpenStreetMap contributors"))
        rule.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
        android.os.SystemClock.sleep(1500) // Let asynchronous map tiles reach the rendered window for visual QA.
        screenshot("trip-map")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(rule.activity.getString(R.string.trips_export)))
        rule.onNodeWithText(rule.activity.getString(R.string.trips_export)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.trips_export_gpx)).performClick()
        screenshot("trip-export")
        rule.runOnIdle {assertEquals(1,csv);assertEquals(1,gpx)}
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {
            val target=File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png")
            target.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        } finally {bitmap.recycle()}
    }
}
