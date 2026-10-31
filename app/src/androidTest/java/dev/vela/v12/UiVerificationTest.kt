package dev.vela.v12

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowCompat
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Synthetic data is confined to the separately installed TEST APK. No demo path exists in the app. */
@RunWith(AndroidJUnit4::class)
class UiVerificationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun sample(): VelaUi {
        val p=ByteArray(66)
        fun u16(i: Int,v: Int) {p[i]=v.toByte();p[i+1]=(v ushr 8).toByte()}
        u16(0,9680);u16(2,80);u16(4,0);u16(6,125);u16(8,12);u16(24,7800)
        p[40]=210.toByte();p[41]=208.toByte();p[42]=201.toByte();p[43]=206.toByte();p[44]=207.toByte();p[45]=206.toByte();p[46]=200.toByte();p[55]=1
        val telemetry=V12Protocol.Frame(0x14,0x84,p)
        val s=ByteArray(42);s[0]=0x20
        fun set(i: Int,v: Int) {s[i]=v.toByte();s[i+1]=(v ushr 8).toByte()}
        set(9,5000);set(11,3000);set(13,4000);set(17,600);s[20]=50;s[21]=60;s[22]=40;s[26]=70;s[27]=80;s[39]=9
        val settings=V12Protocol.Frame(0x14,0xa0,s)
        val model=V12Protocol.Frame(0x11,0x82,byteArrayOf(1,2,7,2,1,3,0))
        val frames=mapOf(V12Protocol.Read.MODEL to model,V12Protocol.Read.TELEMETRY to telemetry,V12Protocol.Read.SETTINGS to settings)
        return VelaUi(WheelState(phase=Phase.CONNECTED,message="Данные с колеса",verifiedHt=true,
            telemetry=V12Protocol.telemetry(telemetry),settings=V12Protocol.settings(settings),settingsAt=9500,lastPacketAt=9500,packets=128,
            stationaryPackets=5,records=frames.mapValues {(r,f)->PacketRecord(r,f,9500,"Получено")}),WheelDevice("AA:BB:CC:DD:EE:FF","INMOTION V12 HT",-55),10000)
    }
    private fun render(ui: VelaUi,tab: Int=0,dark: Boolean=true,actions: Actions=Actions()) {
        rule.runOnUiThread {
            WindowCompat.getInsetsController(rule.activity.window,rule.activity.window.decorView).apply {
                isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
            }
            rule.activity.setContent {VelaTheme(dark) {VelaApp(ui,dark,actions,tab)}}
        }
        rule.waitForIdle()
    }
    private fun shot(name: String) {
        rule.waitForIdle()
        val dir=File(rule.activity.getExternalFilesDir(null),"qa-v02").apply {mkdirs()}
        android.os.SystemClock.sleep(200)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun offlineTabsHaveNoDemoAndNoAbout() {
        render(VelaUi());rule.onAllNodesWithText("Демо",substring=true).assertCountEquals(0);rule.onAllNodesWithText("О Vela").assertCountEquals(0);shot("offline-ride")
        rule.onNodeWithText("Данные").performClick();rule.waitForIdle();rule.onNodeWithText("Под капотом").assertExists();shot("offline-data")
        rule.onNodeWithText("Пульт").performClick();rule.waitForIdle();rule.onNodeWithText("Твой пульт").assertExists();shot("offline-controls")
        rule.onNodeWithText("Связь").performClick();rule.waitForIdle();rule.onNodeWithText("Всегда своё").assertExists();shot("offline-wheel")
    }
    @Test fun connectedDashboardDarkAndLight() {render(sample());shot("connected-dark");render(sample(),dark=false);shot("connected-light")}
    @Test fun decodedAndRawDataScreens() {
        render(sample(),1);shot("connected-data")
        rule.onAllNodesWithText("Телеметрия").onFirst().performClick();rule.waitForIdle();shot("telemetry-fields")
        rule.onNodeWithText("Raw / пакеты").performScrollTo().performClick();rule.waitForIdle();shot("raw-packets")
    }
    @Test fun writeRequiresConfirmationAndNeverSendsOnComposition() {
        val sent=mutableListOf<V12Protocol.Write>();render(sample(),2,actions=Actions(write={sent+=it}));shot("connected-controls")
        assertTrue(sent.isEmpty());rule.onAllNodes(isToggleable()).onFirst().performClick();rule.waitForIdle()
        rule.onNodeWithText("Применить изменение?").assertExists();assertTrue(sent.isEmpty());shot("write-confirmation")
        rule.onNodeWithText("Применить",substring=false).performClick();rule.waitForIdle();assertEquals(1,sent.size)
        assertEquals(V12Protocol.Control.LIGHTS,sent.single().kind);assertEquals(0,sent.single().a)
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(3);shot("sound-controls")
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(4);shot("standby-controls")
    }
    @Test fun safeControlsStayUsableWhileMovingStationaryOnesLock() {
        val u=sample();val moving=u.copy(wheel=u.wheel.copy(telemetry=u.wheel.telemetry!!.copy(speed=10.0),stationaryPackets=0))
        render(moving,2);shot("moving-controls")
        val states=rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        assertTrue(states.isNotEmpty())
        // SAFE tier stays usable while riding, like the vendor app.
        assertTrue(states.any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) })
        // STATIONARY tier (ride modes) locks while moving.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Классический режим"));rule.waitForIdle()
        val later=rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        assertTrue(later.any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) })
        shot("moving-stationary-locked")
    }
    @Test fun savedWheelConnectsWithoutScanAndForgetRequiresConfirmation() {
        var connects=0;var scans=0;var forgotten=0
        render(VelaUi(saved=sample().saved),3,actions=Actions(connect={connects++},scan={scans++},forget={forgotten++}))
        shot("saved-wheel")
        rule.onNodeWithText("Подключить",substring=false).performClick();assertEquals(1,connects);assertEquals(0,scans)
        rule.onNodeWithText("Забыть колесо",substring=false).performClick();rule.waitForIdle();assertEquals(0,forgotten)
        rule.onNodeWithText("Забыть колесо?").assertExists();shot("forget-confirmation")
        rule.onNodeWithText("Забыть",substring=false).performClick();assertEquals(1,forgotten)
    }
    @Test fun wheelIdentityPersistsAndForgetRemovesIt() {
        val name="vela_identity_instrumentation_test"
        val first=WheelStore(rule.activity,name);first.forget()
        assertNull(first.load())
        val d=WheelDevice("AA:BB:CC:DD:EE:FF","My V12 HT",-50)
        first.saveVerified(d)
        val second=WheelStore(rule.activity,name)
        assertEquals(d.address,second.load()!!.address);assertEquals(d.name,second.load()!!.name)
        second.forget();assertNull(WheelStore(rule.activity,name).load())
    }

    @Test fun packetArrivalAndStalenessNeverFakeDisconnect() {
        val u=sample()
        val current=androidx.compose.runtime.mutableStateOf(u)
        rule.runOnUiThread {rule.activity.setContent {VelaTheme(true) {VelaApp(current.value,true,Actions(),0)}}}
        for(at in 10000L..13000L step 250) {
            rule.runOnUiThread {current.value=wheelUiSnapshot(u.wheel.copy(lastPacketAt=at),u.saved) {at+1}}
            rule.waitForIdle();rule.onNodeWithText("На связи").assertExists();rule.onAllNodesWithText("Офлайн").assertCountEquals(0)
        }
        rule.runOnUiThread {current.value=u.copy(now=16000,staleSince=13000)} // sustained staleness (3 s)
        rule.waitForIdle();rule.onNodeWithText("На связи").assertExists()
        rule.onNodeWithText("Показания не обновляются · BLE остаётся подключён").assertExists();shot("connected-stale")
        rule.onNodeWithText("Пульт").performClick();rule.waitForIdle()
        // SAFE tier stays usable on stale data; only stationary controls lock.
        rule.onAllNodes(isToggleable()).onFirst().assertIsEnabled();shot("stale-controls-safe-still-usable")
        rule.runOnUiThread {current.value=u.copy(wheel=u.wheel.copy(phase=Phase.OFFLINE,verifiedHt=false),now=14000)}
        rule.onNodeWithText("Связь").performClick();rule.waitForIdle();rule.onNodeWithText("Офлайн").assertExists()
    }

    @Test fun briefPollHiccupShowsNoStaleBanner() {
        val u=sample()
        val current=androidx.compose.runtime.mutableStateOf(u)
        rule.runOnUiThread {rule.activity.setContent {VelaTheme(true) {VelaApp(current.value,true,Actions(),0)}}}
        rule.waitForIdle()
        // One missed poll (~1.7 s gap) enters staleness less than a second ago: no banner may flash.
        rule.runOnUiThread {current.value=u.copy(now=12200,staleSince=12200)}
        rule.waitForIdle()
        rule.onNodeWithText("Показания не обновляются").assertDoesNotExist()
        rule.onNodeWithText("На связи").assertExists()
    }

}
