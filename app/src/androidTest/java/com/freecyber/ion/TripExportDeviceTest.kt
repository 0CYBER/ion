package com.freecyber.ion

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TripExportDeviceTest {
    @Test fun csvAndGpxAreReadableAttachmentsWithCorrectNamesAndTypes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        listOf("csv" to "text/csv", "gpx" to "application/gpx+xml").forEach { (ext,mime) ->
            val contents=if(ext=="csv") TripCsv.HEADER+"\n" else TrackCodec.gpx(
                listOf(TrackPoint(0,52.0,21.0),TrackPoint(1000,52.001,21.001)),1700000000000,"Ride")
            val file=File(context.cacheDir,"trip_exports/export-test.$ext").apply {parentFile!!.mkdirs();writeText(contents)}
            try {
                val send=TripExport.intent(context,file,mime)
                assertFalse(send.hasExtra(Intent.EXTRA_TEXT))
                assertEquals(mime,send.type)
                val uri=send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                assertEquals("content",uri.scheme)
                assertEquals(uri,send.clipData!!.getItemAt(0).uri)
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(contents,context.contentResolver.openInputStream(uri)!!.bufferedReader().use {it.readText()})
                context.contentResolver.query(uri,null,null,null,null)!!.use {cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(file.name,cursor.getString(cursor.getColumnIndexOrThrow("_display_name")))
                }
            } finally {file.delete()}
        }
    }
}
