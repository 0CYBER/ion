package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TripLoggingTest {
    private fun telemetry(current: Double = 10.0, pwm: Double = 75.0) =
        V12Protocol.Telemetry(100.0,current,36.0,80.0,45,50,1.2,-2.4,false,true,false,pwm=pwm)

    @Test fun frozenWheelTripCounterDoesNotDiscardDistance() {
        val r=TripRecorder()
        repeat(101) { r.feed(telemetry(),it*1000L) }
        assertEquals(1.0,r.stats.value.distanceKm,1e-6)
        r.feed(telemetry().copy(tripKm=0.01),101000)
        assertEquals(1.01,r.stats.value.distanceKm,1e-6)
    }

    @Test fun quantizedCounterReconcilesSpeedFallbackInsteadOfCountingItTwice() {
        val r=TripRecorder()
        r.feed(telemetry().copy(tripKm=1.0),0)
        r.feed(telemetry().copy(tripKm=1.0),1000)
        r.feed(telemetry().copy(tripKm=1.02),2000)
        assertEquals(0.02,r.stats.value.distanceKm,1e-9)
    }

    @Test fun powerIsTimeWeightedAndReconnectGapsDoNotInventEnergy() {
        val r=TripRecorder()
        r.feed(telemetry(),0)
        r.feed(telemetry(20.0,85.0),1000)
        r.feed(telemetry(10.0,70.0),4000)
        r.feed(telemetry(30.0,90.0),60000)
        assertEquals(1250.0,r.stats.value.avgPower!!,1e-6)
        assertEquals(3000.0,r.stats.value.maxPower,0.0)
        assertEquals(90.0,r.stats.value.maxPwm,0.0)
        assertEquals(30.0,r.stats.value.maxCurrent,0.0)
        assertEquals(4000L,r.stats.value.measuredMs)
        val trip=TripSummary.from(r.stats.value.copy(distanceKm=1.0),100000,60000)!!
        assertEquals(trip,TripSummary.decode(trip.encode()))
    }

    @Test fun oldSummariesDoNotPretendToHavePowerMeasurements() {
        val old=TripSummary.decode("100;200;100;1.0;30;20;10;1;5;45")!!
        assertNull(old.maxPower); assertNull(old.avgPower); assertNull(old.maxPwm)
    }

    @Test fun csvContainsRealTelemetryAndOnlyFreshGpsRegardlessOfLocale() {
        val original=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            val point=TrackPoint(1000,52.1,21.2,100.0,10.0)
            val row=TripCsv.row(1700000000000,2000,telemetry(),1.5,point).split(',')
            assertEquals(TripCsv.HEADER.split(',').size,row.size)
            assertEquals("1000.0",row[5]); assertEquals("75.0",row[6])
            assertEquals("1.2",row[10]); assertEquals("52.1",row[22])
            assertTrue(row[0].endsWith("Z"))
            val stale=TripCsv.row(0,20000,telemetry(),1.5,point).split(',')
            assertTrue(stale.drop(22).all {it.isEmpty()})
        } finally {Locale.setDefault(original)}
    }

    @Test fun gpsRejectsStaleOutOfOrderAndImpossibleFixes() {
        assertTrue(GpsFix.valid(52.0,21.0,10.0,2000,3000,1000))
        assertFalse(GpsFix.valid(52.0,21.0,10.0,2000,30000,1000))
        assertFalse(GpsFix.valid(52.0,21.0,100.0,2000,3000,1000))
        assertFalse(GpsFix.valid(Double.NaN,21.0,10.0,2000,3000,1000))
        assertFalse(GpsFix.valid(52.0,21.0,10.0,500,3000,1000))
        val first=TrackPoint(1000,52.0,21.0)
        assertFalse(GpsFix.follows(first,first))
        assertFalse(GpsFix.follows(first,TrackPoint(2000,53.0,21.0)))
        assertTrue(GpsFix.follows(first,TrackPoint(2000,52.0001,21.0)))
    }
}
