package com.freecyber.ion

import android.app.Application
import android.content.ComponentName
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.content.Intent
import android.net.Uri
import java.io.File
import kotlinx.coroutines.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TripRecordingTest {
    @Test fun backgroundRideServiceDeclaresLocationType() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val info = context.packageManager.getServiceInfo(ComponentName(context, WheelService::class.java), 0)
        assertTrue("GPS in a pocket requires a location foreground service",
            info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
    }

    @Test fun exportIsAReadableFileAttachmentRatherThanText() {
        // AndroidX FileProvider canonical-path containment uses '/', while host File uses '\\'.
        // Exercise the real provider in TripExportDeviceTest on Android instead.
        org.junit.Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val context=ApplicationProvider.getApplicationContext<Application>()
        val file=File(context.cacheDir,"trip_exports/test.csv").apply { parentFile!!.mkdirs(); writeText("time,power\n0,1500\n") }
        val intent=TripExport.intent(context,file,"text/csv")
        assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
        val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content",uri.scheme)
        assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(file.readText(),context.contentResolver.openInputStream(uri)!!.bufferedReader().use {it.readText()})
    }

    @Test fun journalPreservesAllSamplesAndGpsAcrossRestartAndDeletesWithRide() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val journal=TripJournal(context,scope)
            val id=1700000000000L
            journal.delete(id)
            repeat(5000) { i ->
                journal.telemetry(id,"sample_$i")
                journal.point(id,TrackPoint(i*1000L,52.0+i/100000.0,21.0))
            }
            journal.awaitWrites()
            assertEquals(5001,journal.csv(id).readLines().size)
            assertEquals(TripCsv.HEADER,journal.csv(id).useLines {it.first()})
            // New store reads files even though the original session never called finishTrip/saveTrack.
            val restored=TripJournal(context,scope)
            assertEquals(5000,restored.track(id).size)
            assertEquals(4999000L,restored.track(id).last().t)
            restored.delete(id); restored.awaitWrites()
            assertFalse(restored.hasTelemetry(id)); assertFalse(restored.hasTrack(id))
            assertFalse(journal.failed.value)
        } finally {scope.cancel()}
    }

    @Test fun checkpointRecoveryKeepsTheAssociatedRouteAndTelemetry() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("ion_trips",0).edit().clear().commit()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val journal=TripJournal(context,scope)
            val trip=TripSummary(123456789,100000,80000,1.0,30.0,20.0,10.0,1.0,2.0,45)
            journal.telemetry(trip.startedAt,"sample")
            journal.point(trip.startedAt,TrackPoint(1000,52.0,21.0))
            journal.point(trip.startedAt,TrackPoint(2000,52.001,21.0))
            TripHistory(context,journal).checkpoint(trip)
            journal.awaitWrites()
            val restored=TripHistory(context,journal)
            restored.recoverPending(); restored.recoverPending()
            assertEquals(listOf(trip),restored.trips.value)
            assertEquals(2,restored.track(trip.startedAt).size)
            assertTrue(restored.hasTelemetry(trip.startedAt))
            restored.clear(); journal.awaitWrites()
            assertFalse(journal.hasTrack(trip.startedAt))
        } finally {scope.cancel()}
    }
}
