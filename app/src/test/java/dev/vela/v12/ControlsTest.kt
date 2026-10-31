package dev.vela.v12
import org.junit.Assert.*
import org.junit.Test

class ControlsTest {
    private fun unpack(w: V12Protocol.Write) = V12Protocol.Decoder().feed(w.bytes()).single()
    @Test fun allowlistMatchesReferencePayloads() {
        assertEquals(19,V12Protocol.Control.entries.size)
        val samples=listOf(
            V12Protocol.Write(V12Protocol.Control.LIGHTS,1,0) to byteArrayOf(0x50,1,0),
            V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,25,75) to byteArrayOf(0x2b,25,75),
            V12Protocol.Write(V12Protocol.Control.AUTO_LIGHT,1) to byteArrayOf(0x2f,1),
            V12Protocol.Write(V12Protocol.Control.VOLUME,42) to byteArrayOf(0x26,42),
            V12Protocol.Write(V12Protocol.Control.MUTE,1) to byteArrayOf(0x2c,0),
            V12Protocol.Write(V12Protocol.Control.SOUND_WAVE,0) to byteArrayOf(0x39,0),
            V12Protocol.Write(V12Protocol.Control.STANDBY,10) to byteArrayOf(0x28,0x58,0x02),
            V12Protocol.Write(V12Protocol.Control.DRL,1) to byteArrayOf(0x2d,1),
            V12Protocol.Write(V12Protocol.Control.PLAY_SOUND,2) to byteArrayOf(0x51,2,0x01),
            V12Protocol.Write(V12Protocol.Control.CLASSIC,1) to byteArrayOf(0x23,1),
            V12Protocol.Write(V12Protocol.Control.FANCIER,0) to byteArrayOf(0x24,0),
            V12Protocol.Write(V12Protocol.Control.SPLIT_MODE,1) to byteArrayOf(0x42,1),
            V12Protocol.Write(V12Protocol.Control.SPLIT_ACCEL_BRAKE,30,70) to byteArrayOf(0x40,30,70),
            V12Protocol.Write(V12Protocol.Control.PEDAL_SENS,55) to byteArrayOf(0x25,55,55),
            V12Protocol.Write(V12Protocol.Control.PEDAL_TILT,-5) to byteArrayOf(0x22,0xce.toByte(),0xff.toByte()),
            V12Protocol.Write(V12Protocol.Control.ALARMS,20,35) to byteArrayOf(0x3e,0xd0.toByte(),0x07,0xac.toByte(),0x0d.toByte()),
            V12Protocol.Write(V12Protocol.Control.HANDLE_BUTTON,1) to byteArrayOf(0x2e,0),
            V12Protocol.Write(V12Protocol.Control.LOCK,1) to byteArrayOf(0x31,1),
            V12Protocol.Write(V12Protocol.Control.TRANSPORT,0) to byteArrayOf(0x32,0))
        samples.forEach { (w,p) -> val f=unpack(w);assertEquals(0x14,f.flags);assertEquals(0x60,f.command);assertFalse(f.response);assertArrayEquals(p,f.payload) }
    }
    @Test fun invalidValuesNeverBuildACommand() {
        listOf({V12Protocol.Write(V12Protocol.Control.VOLUME,101)}, {V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,-1,0)},
            {V12Protocol.Write(V12Protocol.Control.LIGHTS,1,2)},{V12Protocol.Write(V12Protocol.Control.MUTE,2)},
            {V12Protocol.Write(V12Protocol.Control.STANDBY,0)},{V12Protocol.Write(V12Protocol.Control.STANDBY,31)},
            {V12Protocol.Write(V12Protocol.Control.PEDAL_TILT,11)},{V12Protocol.Write(V12Protocol.Control.PEDAL_TILT,-11)},
            {V12Protocol.Write(V12Protocol.Control.ALARMS,71)},{V12Protocol.Write(V12Protocol.Control.PLAY_SOUND,16)},
            {V12Protocol.Write(V12Protocol.Control.SPLIT_ACCEL_BRAKE,101)}).forEach { factory ->
            assertThrows(IllegalArgumentException::class.java) { factory() }
        }
    }
    @Test fun safeTierIgnoresMotionChargingAndTelemetryAgeLikeVendorApp() {
        fun allowed(tier: V12Protocol.Tier,age: Long?=100,speed: Double?=0.0,verified: Boolean=true,connected: Boolean=true,
                    charging: Boolean=false,fw: Boolean=false,pending: Boolean=false)=
            V12Protocol.canWrite(tier,verified,connected,age,speed,charging,fw,pending)
        // SAFE: a live verified link is enough; riding, charging and stale telemetry do not block it.
        assertTrue(allowed(V12Protocol.Tier.SAFE));assertTrue(allowed(V12Protocol.Tier.SAFE,speed=10.0))
        assertTrue(allowed(V12Protocol.Tier.SAFE,speed=-7.5,charging=true));assertTrue(allowed(V12Protocol.Tier.SAFE,age=1500))
        assertTrue(allowed(V12Protocol.Tier.SAFE,age=1501));assertTrue(allowed(V12Protocol.Tier.SAFE,age=null))
        // SAFE still refuses firmware update, unverified wheels, disconnection and pending work.
        assertFalse(allowed(V12Protocol.Tier.SAFE,verified=false));assertFalse(allowed(V12Protocol.Tier.SAFE,connected=false))
        assertFalse(allowed(V12Protocol.Tier.SAFE,fw=true));assertFalse(allowed(V12Protocol.Tier.SAFE,pending=true))
        // STATIONARY adds stillness; charging is allowed because a charging wheel is not moving.
        assertTrue(allowed(V12Protocol.Tier.STATIONARY));assertTrue(allowed(V12Protocol.Tier.STATIONARY,charging=true))
        assertFalse(allowed(V12Protocol.Tier.STATIONARY,speed=.1));assertFalse(allowed(V12Protocol.Tier.STATIONARY,speed=-.1))
        assertFalse(allowed(V12Protocol.Tier.STATIONARY,speed=null));assertFalse(allowed(V12Protocol.Tier.STATIONARY,speed=Double.NaN))
        assertFalse(allowed(V12Protocol.Tier.STATIONARY,age=1501));assertFalse(allowed(V12Protocol.Tier.STATIONARY,age=null))
        assertFalse(allowed(V12Protocol.Tier.STATIONARY,fw=true))
    }
    @Test fun unobservableControlsHaveNoReadback() {
        listOf(V12Protocol.Control.DRL,V12Protocol.Control.PLAY_SOUND,V12Protocol.Control.LOCK).forEach { kind ->
            val w=V12Protocol.Write(kind,1);assertNull(w.readBack);assertNull(w.confirmed(V12Protocol.Frame(0x14,0xa0,ByteArray(42))))
        }
    }
    @Test fun settingsReadbackConfirmsNewCommands() {
        fun settings(): ByteArray { val p=ByteArray(42);p[0]=0x20;p[19]=0x11;p[22]=55;p[39]=0x45 // bit0 unmuted, bit2 handle, bit6 transport
            fun s16(i: Int,v: Int){p[i]=v.toByte();p[i+1]=(v ushr 8).toByte()}
            s16(11,2000);s16(13,3500);s16(15,-50);p[20]=55;p[21]=55;p[31]=30;p[32]=70;p[41]=1;return p }
        val f=V12Protocol.Frame(0x14,0xa0,settings())
        assertTrue(V12Protocol.Write(V12Protocol.Control.ALARMS,20,35).confirmed(f) == true)
        assertTrue(V12Protocol.Write(V12Protocol.Control.PEDAL_SENS,55).confirmed(f) == true)
        assertTrue(V12Protocol.Write(V12Protocol.Control.PEDAL_TILT,-5).confirmed(f) == true)
        assertTrue(V12Protocol.Write(V12Protocol.Control.CLASSIC,1).confirmed(f) == true)
        assertTrue(V12Protocol.Write(V12Protocol.Control.FANCIER,1).confirmed(f) == true)
        assertTrue(V12Protocol.Write(V12Protocol.Control.SPLIT_MODE,1).confirmed(f) == true)
        assertTrue(V12Protocol.Write(V12Protocol.Control.SPLIT_ACCEL_BRAKE,30,70).confirmed(f) == true)
        assertTrue(V12Protocol.Write(V12Protocol.Control.HANDLE_BUTTON,1).confirmed(f) == true) // bit2 of byte39=0x48
        assertTrue(V12Protocol.Write(V12Protocol.Control.TRANSPORT,1).confirmed(f) == true) // bit6
        assertTrue(V12Protocol.Write(V12Protocol.Control.MUTE,0).confirmed(f) == true) // bit0 of 0x48 clear = unmuted
        assertTrue(V12Protocol.Write(V12Protocol.Control.PEDAL_SENS,54).confirmed(f) == false) // mismatching value is not confirmed
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
    @Test fun statisticsDecodesReferenceFixture() {
        // WheelLog V12 example payload: odometer 205.79 km.
        val payload="6350000074471800d1140400c68e00007d350200b0ce0000".chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        val f=V12Protocol.Decoder().feed(V12Protocol.frame(0x14,0x91,payload)).single()
        val st=V12Protocol.statistics(f)!!
        assertEquals(205.79,st.totalKm,0.0001);assertEquals(15911.56,st.dischargedKwh,0.01)
        assertEquals(2674.73,st.recoveredKwh,0.01);assertEquals(36550,st.rideTimeSec);assertEquals(144765,st.powerOnSec)
        val rows=V12Protocol.fields(f)
        assertTrue(rows.any { it.key=="statsTotal" && it.value.contains("205,79") })
        assertTrue(rows.any { it.key=="statsRide" && it.value=="10:09:10" })
        assertNull(V12Protocol.statistics(V12Protocol.Frame(0x14,0x91,byteArrayOf(1,2,3))))
    }
    @Test fun batterySerialVersionsDiagnosticDecode() {
        val b=ByteArray(20)
        fun u16(i: Int,v: Int){b[i]=v.toByte();b[i+1]=(v ushr 8).toByte()}
        u16(0,9600);b[4]=27;b[5]=3;u16(8,9550);b[12]=26;b[13]=1;u16(16,8400);u16(18,200)
        val bf=V12Protocol.Frame(0x14,0x85,b)
        val info=V12Protocol.battery(bf)!!
        assertEquals(96.0,info.bat1Voltage,0.0001);assertTrue(info.bat1Valid);assertTrue(info.bat1Enabled)
        assertEquals(95.5,info.bat2Voltage,0.0001);assertTrue(info.bat2Valid);assertFalse(info.bat2Enabled)
        assertEquals(84.0,info.chargeVoltage,0.0001);assertEquals(2.0,info.chargeCurrent,0.0001)
        val serialPayload=byteArrayOf(2)+"ION0123456789ABC".toByteArray(Charsets.US_ASCII) // selector + exactly 16 ASCII bytes
        val serialF=V12Protocol.Decoder().feed(V12Protocol.frame(0x11,0x82,serialPayload)).single()
        assertEquals("ION0123456789ABC",V12Protocol.serial(serialF))
        val v=ByteArray(24);v[0]=6
        fun v16(i: Int,x: Int){v[i]=x.toByte();v[i+1]=(x ushr 8).toByte()}
        v16(2,15);v[4]=1;v[5]=2;v16(11,20);v[13]=3;v[14]=4;v16(20,30);v[22]=5;v[23]=6
        val ver=V12Protocol.versions(V12Protocol.Frame(0x11,0x82,v))!!
        assertEquals("2.1.15",ver.driver);assertEquals("4.3.20",ver.main);assertEquals("6.5.30",ver.ble)
        val diag=V12Protocol.fields(V12Protocol.Frame(0x14,0x83,byteArrayOf(0,0,0,0)))
        assertTrue(diag.any { it.key=="diag" && it.value.contains("Без ошибок") })
    }
    @Test fun telemetryMileageAndErrorNamesDecode() {
        val p=ByteArray(66)
        fun u16(i: Int,v: Int){p[i]=v.toByte();p[i+1]=(v ushr 8).toByte()}
        u16(22,12345);u16(26,6789);p[59]=1 // err_iPhaseSensorState
        val f=V12Protocol.Frame(0x14,0x84,p)
        val t=V12Protocol.telemetry(f)!!
        assertEquals(123.45,t.mileageKm,0.0001);assertEquals(67.89,t.rangeKm,0.0001)
        val rows=V12Protocol.fields(f)
        assertTrue(rows.any { it.key=="mileage" && it.value.contains("123,45") })
        assertTrue(rows.any { it.key=="errors" && it.value.contains("err_iPhaseSensorState") })
    }
    @Test fun savedIdentityAddressValidation() {
        assertTrue(SavedWheelFormat.validAddress("AA:BB:CC:DD:EE:FF"));assertTrue(SavedWheelFormat.validAddress("01:23:45:ab:cd:ef"))
        assertFalse(SavedWheelFormat.validAddress(null));assertFalse(SavedWheelFormat.validAddress("fake"));assertFalse(SavedWheelFormat.validAddress("AA:BB:CC:DD:EE"))
    }
    @Test fun stationarySamplesGateOnlyStationaryTier() {
        val t=V12Protocol.Telemetry(96.0,0.0,0.0,50.0,30,30,0.0,0.0,false,false,false)
        val s=WheelState(phase=Phase.CONNECTED,verifiedHt=true,telemetry=t,lastPacketAt=1000,stationaryPackets=2)
        val ui2=VelaUi(s,now=1100);val ui3=VelaUi(s.copy(stationaryPackets=3),now=1100)
        // SAFE tier ignores stillness; STATIONARY requires 3 stationary samples and fresh age.
        assertTrue(ui2.safeWrite);assertFalse(ui2.stoppedWrite)
        assertTrue(ui3.safeWrite);assertTrue(ui3.stoppedWrite)
        assertFalse(VelaUi(s.copy(stationaryPackets=3),now=3000).stoppedWrite)
    }
}
