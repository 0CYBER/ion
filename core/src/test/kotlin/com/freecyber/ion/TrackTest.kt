package com.freecyber.ion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TrackTest {
    private fun p(t: Long, lat: Double, lon: Double, alt: Double? = null, speed: Double? = null) =
        TrackPoint(t, lat, lon, alt, speed)

    @Test fun oneDegreeOfLatitudeIsAbout111Km() {
        val d = Track.metres(p(0, 50.0, 30.0), p(1, 51.0, 30.0))
        assertEquals(111_320.0, d, 200.0)
    }

    @Test fun cumulativeDistanceStartsAtZeroAndGrows() {
        val points = listOf(p(0, 50.0, 30.0), p(1_000, 50.0, 30.01), p(2_000, 50.0, 30.02))
        val cum = Track.cumulativeKm(points)
        assertEquals(3, cum.size)
        assertEquals(0.0, cum[0], 1e-9)
        assertTrue(cum[2] > cum[1] && cum[1] > 0.5)
    }

    @Test fun thinKeepsEndpointsAndCap() {
        val points = (0 until 5000).map { p(it.toLong(), 50.0 + it / 1e5, 30.0 + it / 1e5) }
        val thinned = Track.thin(points)
        assertEquals(Track.MAX_POINTS, thinned.size)
        assertEquals(points.first(), thinned.first())
        assertEquals(points.last(), thinned.last())
        // Below the cap the list is untouched.
        assertEquals(points.take(100), Track.thin(points.take(100)))
    }

    @Test fun codecRoundTripsPointsAndDeltas() {
        val points = listOf(
            p(0, 55.75124, 37.61842, 160.0, 0.0),
            p(1_200, 55.75135, 37.61890, 162.0, 5.4),
            p(2_400, 55.75170, 37.61910, 161.0, 7.2),
            p(3_000, 55.75170, 37.61910, 161.0, 0.0))
        val decoded = TrackCodec.decode(TrackCodec.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertEquals(a.t, b.t)
            assertEquals(a.lat, b.lat, 1e-5)
            assertEquals(a.lon, b.lon, 1e-5)
            assertEquals(a.alt, b.alt)
            assertEquals(a.speed, b.speed)
        }
    }

    @Test fun codecSurvivesMalformedInput() {
        assertTrue(TrackCodec.decode("").isEmpty())
        assertEquals(1, TrackCodec.decode("0,5500000,3760000,,").size)
        assertTrue(TrackCodec.decode("x,y,z").isEmpty())
        // An off-globe point is skipped; the next delta brings the track back and still decodes.
        val healed = TrackCodec.decode("0,5500000,3760000,,;1000,9000000,0,,;1000,-9000000,0,,")
        assertEquals(listOf(0L, 2000L), healed.map { it.t })
        assertEquals(55.0, healed.last().lat, 1e-9)
    }

    @Test fun gpxCarriesPointsAndEscapesName() {
        val gpx = TrackCodec.gpx(listOf(TrackPoint(0, 55.75124, 37.61842, 160.0, 3.0)), 1_762_400_000_000, "Ride <A>")
        assertTrue("ACTUAL:" + gpx, gpx.contains("""<trkpt lat="55.75124" lon="37.61842"><ele>160.0</ele><time>"""))
        assertTrue(gpx.contains("Ride &lt;A&gt;"))
        assertTrue(gpx.trim().endsWith("</gpx>"))
    }
}
