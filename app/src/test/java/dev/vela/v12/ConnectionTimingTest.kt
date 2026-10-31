package dev.vela.v12

import org.junit.Assert.*
import org.junit.Test

class ConnectionTimingTest {
    private fun state(at: Long) = WheelState(phase=Phase.CONNECTED,verifiedHt=true,lastPacketAt=at,
        settingsAt=at,stationaryPackets=3,
        telemetry=V12Protocol.Telemetry(96.0,0.0,0.0,50.0,30,30,0.0,0.0,false,false,false))

    @Test fun packetsBetweenTicksUseNewClockSample() {
        val oldTick=1000L
        val packet=state(1250)
        assertFalse(VelaUi(packet,now=oldTick).fresh) // Reproduces 0.2.0 race.
        var sampled=0
        val ui=wheelUiSnapshot(packet,null) {sampled++;1251L}
        assertEquals(1,sampled);assertTrue(ui.fresh);assertTrue(ui.connected)
        assertTrue(ui.settingsFresh);assertTrue(ui.safeWrite);assertTrue(ui.stoppedWrite)
    }
    @Test fun repeatedPacketsAndTicksDoNotFlicker() {
        for(tick in 1000L..10000L step 500) {
            for(packetAt in listOf(tick+1,tick+180,tick+499)) {
                val packet=state(packetAt)
                val onPacket=wheelUiSnapshot(packet,null) {packetAt+1}
                val onTick=wheelUiSnapshot(packet,null) {tick+500}
                assertTrue(onPacket.fresh);assertTrue(onTick.fresh)
                assertTrue(onPacket.safeWrite);assertTrue(onTick.safeWrite)
                assertTrue(onPacket.connected);assertTrue(onTick.connected)
            }
        }
    }
    @Test fun staleTelemetryDoesNotPretendBleDisconnectedAndOnlyBlocksStationaryWrites() {
        val packet=state(1000)
        val ui=wheelUiSnapshot(packet,null) {4000L}
        assertTrue(ui.connected);assertFalse(ui.fresh)
        // SAFE tier follows the vendor app: a live link is enough, staleness does not block it.
        assertTrue(ui.safeWrite)
        // STATIONARY tier still refuses stale data.
        assertFalse(ui.stoppedWrite)
        assertFalse(wheelUiSnapshot(packet,null) {17000L}.settingsFresh)
        assertFalse(wheelUiSnapshot(packet,null) {999L}.fresh) // Never clamp invalid future timestamps.
    }
    @Test fun stalenessNeedsSustainedGapBeforeAnyBanner() {
        val packet=state(1000)
        // Brief packet lag (1-2s) never shows the banner, however fresh=false it is.
        assertFalse(wheelUiSnapshot(packet,null,staleSince=2800L,clock={4000L}).staleConfirmed)
        assertFalse(wheelUiSnapshot(packet,null,staleSince=3000L,clock={5499L}).staleConfirmed)
        // Only a gap sustained >= STALE_BANNER_AFTER_MS counts (total staleness ~5s).
        assertTrue(wheelUiSnapshot(packet,null,staleSince=3000L,clock={5500L}).staleConfirmed)
        assertTrue(wheelUiSnapshot(packet,null,staleSince=3000L,clock={12000L}).staleConfirmed)
        // And a recovered packet is fresh again. (Resetting staleSince itself happens in the
        // ViewModel combine, not in this stateless snapshot.)
        val fresh=state(4000)
        assertTrue(wheelUiSnapshot(fresh,null,staleSince=3000L,clock={4001L}).fresh)
    }
    @Test fun oneTelemetryTimeoutCannotExceedTheFreshnessWindow() {
        // Worst case between telemetry packets: ask timeout + poll delay. It must stay inside the
        // freshness window, otherwise one lost packet flashes the stale banner (the 0.2.x flash).
        assertTrue(Timing.TELEMETRY_ASK_MS + Timing.POLL_DELAY_MS < Timing.FRESH_WINDOW_MS)
    }
    @Test fun actualDisconnectAndErrorsRemainDisconnected() {
        for(phase in listOf(Phase.OFFLINE,Phase.ERROR,Phase.IDENTIFYING)) {
            val ui=wheelUiSnapshot(state(1000).copy(phase=phase),null) {1001L}
            assertFalse(ui.connected);assertFalse(ui.fresh);assertFalse(ui.safeWrite);assertFalse(ui.stoppedWrite)
        }
    }
}
