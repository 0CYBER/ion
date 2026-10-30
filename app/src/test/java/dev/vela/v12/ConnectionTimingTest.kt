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
        assertTrue(ui.settingsFresh);assertTrue(ui.canWrite)
    }
    @Test fun repeatedPacketsAndTicksDoNotFlicker() {
        for(tick in 1000L..10000L step 500) {
            for(packetAt in listOf(tick+1,tick+180,tick+499)) {
                val packet=state(packetAt)
                val onPacket=wheelUiSnapshot(packet,null) {packetAt+1}
                val onTick=wheelUiSnapshot(packet,null) {tick+500}
                assertTrue(onPacket.fresh);assertTrue(onTick.fresh)
                assertTrue(onPacket.canWrite);assertTrue(onTick.canWrite)
                assertTrue(onPacket.connected);assertTrue(onTick.connected)
            }
        }
    }
    @Test fun staleTelemetryDoesNotPretendBleDisconnectedButBlocksWrites() {
        val packet=state(1000)
        val ui=wheelUiSnapshot(packet,null) {4000L}
        assertTrue(ui.connected);assertFalse(ui.fresh);assertFalse(ui.canWrite)
        assertFalse(wheelUiSnapshot(packet,null) {17000L}.settingsFresh)
        assertFalse(wheelUiSnapshot(packet,null) {999L}.fresh) // Never clamp invalid future timestamps.
        assertFalse(wheelUiSnapshot(packet,null) {999L}.canWrite)
    }
    @Test fun actualDisconnectAndErrorsRemainDisconnected() {
        for(phase in listOf(Phase.OFFLINE,Phase.ERROR,Phase.IDENTIFYING)) {
            val ui=wheelUiSnapshot(state(1000).copy(phase=phase),null) {1001L}
            assertFalse(ui.connected);assertFalse(ui.fresh);assertFalse(ui.canWrite)
        }
    }
}
