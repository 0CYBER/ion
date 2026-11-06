package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class AlertEngineTest {
    private fun input(battery: Double? = 80.0, temp: Int? = 40, speed: Double? = 10.0, connected: Boolean = true,
        lost: Boolean = false, charging: Boolean = false, errors: Set<String> = emptySet()) =
        AlertInput(connected, lost, battery, charging, temp, temp, speed, errors)

    @Test fun batteryFiresOnceAndRearmsWithHysteresis() {
        val e = AlertEngine(); val c = AlertConfig(batteryLowPercent = 20)
        assertTrue(e.evaluate(input(25.0), c, 0).isEmpty())
        assertEquals(listOf(Alert(AlertKind.BATTERY_LOW, 20)), e.evaluate(input(20.0), c, 1))
        assertTrue(e.evaluate(input(19.0), c, 2).isEmpty())
        assertTrue(e.evaluate(input(21.0), c, 3).isEmpty())
        assertTrue(e.evaluate(input(20.0), c, 4).isEmpty())
        e.evaluate(input(26.0), c, 5)
        assertEquals(AlertKind.BATTERY_LOW, e.evaluate(input(19.0), c, 6).single().kind)
    }

    @Test fun criticalBatterySupersedesLow() {
        val e = AlertEngine(); val c = AlertConfig(batteryLowPercent = 20)
        val fired = e.evaluate(input(9.0), c, 0)
        assertEquals(listOf(AlertKind.BATTERY_CRITICAL), fired.map { it.kind })
        assertTrue(e.evaluate(input(8.0), c, 1).isEmpty())
    }

    @Test fun chargingRearmsAndSuppressesBattery() {
        val e = AlertEngine(); val c = AlertConfig(batteryLowPercent = 20)
        e.evaluate(input(15.0), c, 0)
        assertTrue(e.evaluate(input(15.0, charging = true), c, 1).isEmpty())
        assertEquals(AlertKind.BATTERY_LOW, e.evaluate(input(15.0), c, 2).single().kind)
    }

    @Test fun zeroThresholdDisablesNumericAlerts() {
        val e = AlertEngine(); val c = AlertConfig(0, 0, 0)
        assertTrue(e.evaluate(input(1.0, 150, 90.0), c, 0).isEmpty())
    }

    @Test fun temperatureUsesHottestSensorAndIgnoresGarbage() {
        val e = AlertEngine(); val c = AlertConfig(temperatureHighC = 70)
        assertTrue(e.evaluate(AlertInput(true, false, 80.0, false, 200, 30, 0.0), c, 0).isEmpty())
        assertEquals(Alert(AlertKind.TEMPERATURE_HIGH, 72), e.evaluate(AlertInput(true, false, 80.0, false, 72, 30, 0.0), c, 1).single())
        assertTrue(e.evaluate(AlertInput(true, false, 80.0, false, 68, 30, 0.0), c, 2).isEmpty())
    }

    @Test fun speedHasCooldown() {
        val e = AlertEngine(); val c = AlertConfig(speedHighKmh = 40)
        assertEquals(AlertKind.SPEED_HIGH, e.evaluate(input(speed = 41.0), c, 0).single().kind)
        e.evaluate(input(speed = 30.0), c, 1_000)
        assertTrue(e.evaluate(input(speed = 42.0), c, 2_000).isEmpty())
        e.evaluate(input(speed = 30.0), c, 21_000)
        assertEquals(AlertKind.SPEED_HIGH, e.evaluate(input(speed = -45.0), c, 22_000).single().kind)
    }

    @Test fun linkLostAndRestoredOncePerDrop() {
        val e = AlertEngine(); val c = AlertConfig()
        e.evaluate(input(), c, 0)
        assertEquals(listOf(AlertKind.LINK_LOST), e.evaluate(input(connected = false, lost = true), c, 1).map { it.kind })
        assertTrue(e.evaluate(input(connected = false, lost = true), c, 2).isEmpty())
        assertEquals(listOf(AlertKind.LINK_RESTORED), e.evaluate(input(), c, 3).map { it.kind })
    }

    @Test fun userDisconnectIsSilent() {
        val e = AlertEngine(); val c = AlertConfig()
        e.evaluate(input(), c, 0)
        assertTrue(e.evaluate(input(connected = false, lost = false), c, 1).isEmpty())
        assertTrue(e.evaluate(input(connected = false, lost = true), c, 2).isEmpty())
    }

    @Test fun onlyNewErrorsFire() {
        val e = AlertEngine(); val c = AlertConfig()
        assertEquals(listOf("err_a"), e.evaluate(input(errors = setOf("err_a")), c, 0).single().detail)
        assertTrue(e.evaluate(input(errors = setOf("err_a")), c, 1).isEmpty())
        assertEquals(listOf("err_b"), e.evaluate(input(errors = setOf("err_a", "err_b")), c, 2).single().detail)
    }
}

class DashboardLayoutTest {
    @Test fun roundTripsAndDefaultIsStable() {
        val d = DashboardLayout.DEFAULT
        assertEquals(d, DashboardLayout.decode(d.encode()))
        assertEquals(d, DashboardLayout.decode(null))
        assertEquals(d, DashboardLayout.decode("garbage"))
    }

    @Test fun unknownIdsDroppedAndNewTilesAppended() {
        val l = DashboardLayout.decode("BATTERY:1:1,NOPE:1:0,SPEED:0:1")
        assertEquals(DashTile.BATTERY, l.slots[0].tile)
        assertTrue(l.slots[0].wide)
        assertEquals(DashTile.SPEED, l.slots[1].tile)
        assertFalse(l.slots[1].visible)
        assertEquals(DashTile.entries.size, l.slots.size)
        assertEquals(DashTile.entries.toSet(), l.slots.map { it.tile }.toSet())
    }

    @Test fun rowsPairHalvesAndIsolateWide() {
        val l = DashboardLayout.decode("SPEED:1:1,BATTERY:1:0,RANGE:1:0,VOLTAGE:1:0,TRIP_SESSION:1:0,CURRENT:1:0")
            .let { DashboardLayout(it.slots.map { s -> if (s.tile.name in setOf("SPEED", "BATTERY", "RANGE", "VOLTAGE", "TRIP_SESSION", "CURRENT")) s else s.copy(visible = false) }) }
        val rows = l.rows().map { r -> r.map { it.tile } }
        assertEquals(listOf(listOf(DashTile.SPEED), listOf(DashTile.BATTERY, DashTile.RANGE), listOf(DashTile.VOLTAGE),
            listOf(DashTile.TRIP_SESSION), listOf(DashTile.CURRENT)), rows)
    }

    @Test fun moveVisibleSkipsHidden() {
        val l = DashboardLayout(listOf(TileSlot(DashTile.BATTERY, true, false), TileSlot(DashTile.POWER, false, false),
            TileSlot(DashTile.RANGE, true, false)))
        assertEquals(listOf(DashTile.RANGE, DashTile.BATTERY, DashTile.POWER), l.moveVisible(DashTile.RANGE, -1).slots.map { it.tile })
        assertSame(l, l.moveVisible(DashTile.BATTERY, -1))
    }

    @Test fun heroTilesCannotBeHalved() {
        val l = DashboardLayout.DEFAULT.resize(DashTile.SPEED)
        assertTrue(l.slots.first { it.tile == DashTile.SPEED }.effectiveWide)
        val t = DashboardLayout.DEFAULT.toggle(DashTile.POWER)
        assertTrue(t.slots.first { it.tile == DashTile.POWER }.visible)
    }
}

class TripRecorderTest {
    private fun t(speed: Double, trip: Double, battery: Double = 80.0, v: Double = 84.0, a: Double = 10.0) =
        V12Protocol.Telemetry(v, a, speed, battery, 40, 45, 0.0, 0.0, false, false, false, tripKm = trip)

    @Test fun integratesFromWheelCounter() {
        val r = TripRecorder()
        r.feed(t(20.0, 1.00), 0)
        r.feed(t(20.0, 1.01), 1_000)
        r.feed(t(25.0, 1.02), 2_000)
        val s = r.stats.value
        assertEquals(0.02, s.distanceKm, 1e-9)
        assertEquals(25.0, s.maxSpeed, 0.0)
        assertEquals(2_000L, s.movingMs)
        assertEquals(840.0 * 2 / 3600, s.energyWh, 1e-9)
        assertEquals(3, s.samples)
    }

    @Test fun gapsAreNotIntegratedAndCounterResetFallsBack() {
        val r = TripRecorder()
        r.feed(t(36.0, 5.0), 0)
        r.feed(t(36.0, 5.0), 60_000)
        assertEquals(0.0, r.stats.value.distanceKm, 0.0)
        r.feed(t(36.0, 0.0), 61_000)
        assertEquals(0.01, r.stats.value.distanceKm, 1e-9)
    }

    @Test fun regenAndBattery() {
        val r = TripRecorder()
        r.feed(t(10.0, 0.0, battery = 90.0, a = -5.0), 0)
        r.feed(t(10.0, 0.0, battery = 85.0, a = -5.0), 3_600)
        val s = r.stats.value
        assertEquals(0.0, s.energyWh, 0.0)
        assertTrue(s.regenWh > 0)
        assertEquals(5.0, s.batteryUsed!!, 1e-9)
        assertNull(s.consumption)
        r.reset()
        assertEquals(TripStats(), r.stats.value)
    }
}

class WidgetSnapshotTest {
    @Test fun roundTrip() {
        val s = WidgetSnapshot(WidgetSnapshot.State.LIVE, 77, 23.5, 41.2, 3.4, 51, true, true, 1234)
        assertEquals(s, WidgetSnapshot.decode(s.encode()))
        val empty = WidgetSnapshot()
        assertEquals(empty, WidgetSnapshot.decode(empty.encode()))
        assertEquals(empty, WidgetSnapshot.decode("junk"))
        assertEquals(empty, WidgetSnapshot.decode(null))
    }

    @Test fun offlineKeepsLastKnownFiguresAndTime() {
        val live = WidgetSnapshot(WidgetSnapshot.State.LIVE, 77, 23.5, 41.2, 3.4, 51, true, true, 1_000)
        val off = WidgetSnapshot(WidgetSnapshot.State.OFFLINE, hasSavedWheel = true, updatedAt = 9_000).carryOver(live)
        assertEquals(77, off.battery); assertEquals(41.2, off.rangeKm!!, 0.0); assertEquals(51, off.temperature)
        assertNull(off.speed); assertFalse(off.lowBeam)
        assertEquals(1_000, off.updatedAt)
        assertTrue(off.isStale)
        // Chained offline pushes keep the original time; a new live push replaces everything.
        assertEquals(1_000, WidgetSnapshot(updatedAt = 20_000).carryOver(off).updatedAt)
        val again = WidgetSnapshot(WidgetSnapshot.State.LIVE, 60, 10.0, null, null, null, false, true, 30_000).carryOver(off)
        assertEquals(60, again.battery); assertNull(again.rangeKm); assertFalse(again.isStale)
        // Nothing remembered: nothing invented.
        assertNull(WidgetSnapshot().carryOver(WidgetSnapshot()).battery)
    }
}

class ReconnectPolicyTest {
    @Test fun backoffGrowsAndCaps() {
        assertEquals(2_000L, ReconnectPolicy.delayFor(0))
        assertEquals(4_000L, ReconnectPolicy.delayFor(1))
        assertEquals(60_000L, ReconnectPolicy.delayFor(5))
        assertEquals(60_000L, ReconnectPolicy.delayFor(30))
        assertTrue(ReconnectPolicy.shouldRetry(0))
        assertFalse(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS))
    }
}

class WheelErrorsTest {
    @Test fun labelsAreHumanAndUnknownFallsBack() {
        assertTrue(WheelErrors.isBenign("err_deviceLockState"))
        assertFalse(WheelErrors.isBenign("err_overheat"))
        assertTrue(WheelErrors.label("err_something_new").isNotBlank())
    }
}

class TripSummaryTest {
    private val sample = TripSummary(1_700_000_000_000, 600_000, 540_000, 4.321, 31.2, 28.8, 120.0, 12.5, 7.0, 55)

    @Test fun roundTripIncludingNulls() {
        assertEquals(sample, TripSummary.decode(sample.encode()))
        val bare = sample.copy(batteryUsed = null, maxTemp = null)
        assertEquals(bare, TripSummary.decode(bare.encode()))
        assertNull(TripSummary.decode("1;2;3"))
        assertNull(TripSummary.decode("x;2;3;4;5;6;7;8;;"))
    }

    @Test fun fromStatsConvertsClockAndDropsBlips() {
        val stats = TripStats(startedAt = 10_000, lastAt = 70_000, samples = 60, distanceKm = 0.5, movingMs = 50_000)
        val s = TripSummary.from(stats, wallNow = 1_000_000, elapsedNow = 100_000)!!
        assertEquals(910_000L, s.startedAt)
        assertEquals(60_000L, s.durationMs)
        assertNull(TripSummary.from(stats.copy(distanceKm = 0.01), 1_000_000, 100_000))
        assertNull(TripSummary.from(TripStats(), 1_000_000, 100_000))
    }

    @Test fun csvHasHeaderAndDotDecimals() {
        val lines = TripSummary.csv(listOf(sample)).trim().lines()
        assertEquals(TripSummary.CSV_HEADER, lines[0])
        assertEquals("1700000000000,600,540,4.321,31.2,28.8,120.0,12.5,7.0,55", lines[1])
        assertEquals(10, lines[0].split(',').size)
    }

    @Test fun totals() {
        val t = TripTotals.of(listOf(sample, sample.copy(distanceKm = 1.0, maxSpeed = 40.0)))
        assertEquals(2, t.count)
        assertEquals(5.321, t.distanceKm, 1e-9)
        assertEquals(40.0, t.maxSpeed, 0.0)
        assertEquals(215.0, t.energyWh, 1e-9)
    }
}

class BatteryStepperTest {
    @Test fun announcesEachDecadeOnceDespiteWobble() {
        val s = BatteryStepper()
        assertNull(s.next(83.0, false))
        assertNull(s.next(80.2, false))
        assertEquals(70, s.next(79.9, false))
        assertNull(s.next(80.1, false))
        assertNull(s.next(79.8, false))
        assertEquals(60, s.next(69.0, false))
        assertEquals(40, s.next(49.0, false))
    }

    @Test fun chargingRebasesAndGarbageIgnored() {
        val s = BatteryStepper()
        s.next(55.0, false)
        assertNull(s.next(75.0, true))
        assertEquals(60, s.next(69.0, false))
        assertNull(s.next(Double.NaN, false))
        assertNull(s.next(140.0, false))
        assertNull(s.next(null, false))
    }
}

class SampleBufferTest {
    private fun t(speed: Double) = V12Protocol.Telemetry(84.0, 10.0, speed, 80.0, 40, 45, 0.0, 0.0, false, false, false)

    @Test fun thinsToOnePerStepAndWraps() {
        val b = SampleBuffer(capacity = 3, stepMs = 1_000)
        assertTrue(b.add(t(1.0), 0))
        assertFalse(b.add(t(2.0), 500))
        assertTrue(b.add(t(-3.0), 1_000))
        assertTrue(b.add(t(4.0), 2_000))
        assertTrue(b.add(t(5.0), 3_000))
        assertEquals(listOf(3f, 4f, 5f), b.snapshot().map { it.speed })
        assertEquals(840f, b.snapshot().first().power, 0f)
        b.clear()
        assertTrue(b.snapshot().isEmpty())
        assertTrue(b.add(t(1.0), 3_100))
    }
}
