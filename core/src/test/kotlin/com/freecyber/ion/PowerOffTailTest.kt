package com.freecyber.ion

import com.freecyber.ion.TripRecorder.Feed
import org.junit.Assert.*
import org.junit.Test

class PowerOffTailTest {
    private fun t(v: Double, speed: Double = 0.0, battery: Double = 60.0, a: Double = 0.5) =
        V12Protocol.Telemetry(v, a, speed, battery, 40, 45, 0.0, 0.0, false, false, false, tripKm = 1.0)

    @Test fun collapseBeforeTheLinkDropsNeverReachesTheRide() {
        val r = TripRecorder()
        assertEquals(Feed.COUNTED, r.feed(t(92.0, speed = 20.0, a = 8.0), 0))
        assertEquals(Feed.COUNTED, r.feed(t(91.5), 200))
        assertEquals(Feed.HELD, r.feed(t(60.0, battery = 0.0), 400))
        assertEquals(Feed.HELD, r.feed(t(22.0, battery = 0.0), 600))
        val s = r.stats.value
        assertEquals(91.5, s.minVoltage!!, 1e-9)
        assertEquals(0.0, s.batteryUsed!!, 1e-9)
        assertEquals(2, s.samples)
    }

    @Test fun theWheelComingBackAfterAGapDropsTheHeldTail() {
        val r = TripRecorder()
        r.feed(t(92.0), 0)
        r.feed(t(40.0), 200)
        assertEquals(Feed.COUNTED, r.feed(t(91.0), 30_000))
        assertEquals(91.0, r.stats.value.minVoltage!!, 1e-9)
    }

    @Test fun aShortRealDipIsCountedOnceTheNextSampleArrives() {
        val r = TripRecorder()
        r.feed(t(92.0), 0)
        assertEquals(Feed.HELD, r.feed(t(74.0), 200))
        assertEquals(Feed.COUNTED_WITH_HELD, r.feed(t(90.0), 400))
        assertEquals(74.0, r.stats.value.minVoltage!!, 1e-9)
        assertEquals(3, r.stats.value.samples)
    }

    @Test fun sagWhileRidingIsNeverHeldAndALongLowSpellIsAccepted() {
        val r = TripRecorder()
        r.feed(t(92.0, speed = 30.0), 0)
        assertEquals(Feed.COUNTED, r.feed(t(70.0, speed = 30.0, a = 60.0), 200))
        val low = TripRecorder()
        low.feed(t(80.0), 0)
        (1..60).forEach { low.feed(t(60.0), it * 200L) }
        assertEquals(60.0, low.stats.value.minVoltage!!, 1e-9)
    }
}
