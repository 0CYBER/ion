package dev.vela.v12
import org.junit.Assert.*
import org.junit.Test

class ControlsTest {
    private fun unpack(w: V12Protocol.Write) = V12Protocol.Decoder().feed(w.bytes()).single()
    @Test fun allowlistHasOnlySevenAccessoryCommands() {
        assertEquals(7,V12Protocol.Control.entries.size)
        val samples=listOf(
            V12Protocol.Write(V12Protocol.Control.LIGHTS,1,0) to byteArrayOf(0x50,1,0),
            V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,25,75) to byteArrayOf(0x2b,25,75),
            V12Protocol.Write(V12Protocol.Control.AUTO_LIGHT,1) to byteArrayOf(0x2f,1),
            V12Protocol.Write(V12Protocol.Control.VOLUME,42) to byteArrayOf(0x26,42),
            V12Protocol.Write(V12Protocol.Control.MUTE,1) to byteArrayOf(0x2c,0),
            V12Protocol.Write(V12Protocol.Control.SOUND_WAVE,0) to byteArrayOf(0x39,0),
            V12Protocol.Write(V12Protocol.Control.STANDBY,10) to byteArrayOf(0x28,0x58,0x02))
        samples.forEach { (w,p) -> val f=unpack(w);assertEquals(0x14,f.flags);assertEquals(0x60,f.command);assertFalse(f.response);assertArrayEquals(p,f.payload) }
    }
    @Test fun invalidValuesNeverBuildACommand() {
        listOf({V12Protocol.Write(V12Protocol.Control.VOLUME,101)}, {V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,-1,0)},
            {V12Protocol.Write(V12Protocol.Control.LIGHTS,1,2)},{V12Protocol.Write(V12Protocol.Control.MUTE,2)},
            {V12Protocol.Write(V12Protocol.Control.STANDBY,0)},{V12Protocol.Write(V12Protocol.Control.STANDBY,31)}).forEach { factory ->
            assertThrows(IllegalArgumentException::class.java) { factory() }
        }
    }
    @Test fun safetyGateBlocksStaleMovingUnknownChargingAndFirmware() {
        fun allowed(age: Long?=100,speed: Double?=0.0,verified: Boolean=true,connected: Boolean=true,charging: Boolean=false,fw: Boolean=false,pending: Boolean=false)=
            V12Protocol.canWrite(verified,connected,age,speed,charging,fw,pending)
        assertTrue(allowed());assertTrue(allowed(age=1500));assertFalse(allowed(age=1501));assertFalse(allowed(age=-1));assertFalse(allowed(age=null))
        assertFalse(allowed(speed=.1));assertFalse(allowed(speed=-.1));assertFalse(allowed(speed=null));assertFalse(allowed(speed=Double.NaN))
        assertFalse(allowed(verified=false));assertFalse(allowed(connected=false));assertFalse(allowed(charging=true));assertFalse(allowed(fw=true));assertFalse(allowed(pending=true))
    }
    @Test fun onlyReadbackCanConfirmNotEchoOrAck() {
        val w=V12Protocol.Write(V12Protocol.Control.VOLUME,42)
        assertNull(w.confirmed(unpack(w)))
        assertNull(w.confirmed(V12Protocol.Frame(0x14,0xe0,byteArrayOf(0x26,0))))
        val p=ByteArray(42);p[0]=0x20;p[22]=42
        assertEquals(true,w.confirmed(V12Protocol.Frame(0x14,0xa0,p)))
        p[22]=41;assertEquals(false,w.confirmed(V12Protocol.Frame(0x14,0xa0,p)))
        assertNull(w.confirmed(V12Protocol.Frame(0x14,0xa0,p.copyOf(20))))
    }
    @Test fun lightsPreserveBothChannelsAndConfirmTelemetry() {
        val p=ByteArray(56);p[55]=3
        val f=V12Protocol.Frame(0x14,0x84,p)
        assertEquals(true,V12Protocol.Write(V12Protocol.Control.LIGHTS,1,1).confirmed(f))
        assertEquals(false,V12Protocol.Write(V12Protocol.Control.LIGHTS,1,0).confirmed(f))
    }
    @Test fun mutePolarityAndStandbyReadback() {
        val p=ByteArray(42);p[0]=0x20;p[17]=0x58;p[18]=2
        val f=V12Protocol.Frame(0x14,0xa0,p)
        assertEquals(true,V12Protocol.Write(V12Protocol.Control.MUTE,1).confirmed(f))
        assertEquals(true,V12Protocol.Write(V12Protocol.Control.STANDBY,10).confirmed(f))
        p[39]=1;assertEquals(false,V12Protocol.Write(V12Protocol.Control.MUTE,1).confirmed(f))
    }
    @Test fun mainInfoSelectorsDoNotCrossMatch() {
        val serial=V12Protocol.Frame(0x11,0x82,byteArrayOf(2,65,66))
        assertTrue(V12Protocol.Read.SERIAL.matches(serial));assertFalse(V12Protocol.Read.MODEL.matches(serial));assertFalse(V12Protocol.Read.VERSIONS.matches(serial))
        assertEquals(9,V12Protocol.Read.entries.size)
        V12Protocol.Read.entries.forEach { assertFalse(it.matches(V12Protocol.Decoder().feed(V12Protocol.request(it)).single())) }
    }
    @Test fun fieldsAndRawRetainUnknownBytes() {
        val p=ByteArray(66);p[40]=205.toByte();p[41]=206.toByte();p[59]=0xaa.toByte()
        val f=V12Protocol.Frame(0x14,0x84,p)
        assertTrue(V12Protocol.fields(f).size>=30)
        assertTrue(V12Protocol.fields(f).any { it.key=="temp0" && it.value=="29 °C" })
        assertTrue(V12Protocol.raw(f).contains("056:"));assertTrue(V12Protocol.raw(f).contains("AA"))
        assertTrue(V12Protocol.fields(V12Protocol.Frame(0x14,0x85,byteArrayOf(9))).isEmpty())
    }
    @Test fun savedIdentityAddressValidation() {
        assertTrue(SavedWheelFormat.validAddress("AA:BB:CC:DD:EE:FF"));assertTrue(SavedWheelFormat.validAddress("01:23:45:ab:cd:ef"))
        assertFalse(SavedWheelFormat.validAddress(null));assertFalse(SavedWheelFormat.validAddress("fake"));assertFalse(SavedWheelFormat.validAddress("AA:BB:CC:DD:EE"))
    }
    @Test fun stationarySamplesAreRequiredEvenWhenSpeedIsZero() {
        val t=V12Protocol.Telemetry(96.0,0.0,0.0,50.0,30,30,0.0,0.0,false,false,false)
        val s=WheelState(phase=Phase.CONNECTED,verifiedHt=true,telemetry=t,lastPacketAt=1000,stationaryPackets=2)
        assertFalse(VelaUi(s,now=1100).canWrite);assertTrue(VelaUi(s.copy(stationaryPackets=3),now=1100).canWrite)
        assertFalse(VelaUi(s.copy(stationaryPackets=3),now=3000).canWrite)
    }
}
