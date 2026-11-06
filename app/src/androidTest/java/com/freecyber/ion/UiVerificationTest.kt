package com.freecyber.ion

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiVerificationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private var renderId=0
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

    private fun render(ui: VelaUi,tab: Int=0,dark: Boolean=true,actions: Actions=Actions(),width: Dp=Dp.Unspecified,fontScale: Float=1f,densityOverride: Float?=null) {
        val id=++renderId
        rule.runOnUiThread {
            WindowCompat.getInsetsController(rule.activity.window,rule.activity.window.decorView).apply {
                isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
            }
            rule.activity.setContent {
                key(id) {
                    CompositionLocalProvider(LocalDensity provides Density(densityOverride ?: LocalDensity.current.density,fontScale)) {
                        Box(if(width==Dp.Unspecified) Modifier.fillMaxSize() else Modifier.width(width).fillMaxHeight()) {
                            IonTheme(dark) {IonApp(ui,dark,PrefsUi(),actions,tab)}
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    private fun nav(title: String) {
        rule.onNode((hasText(title) or hasContentDescription(title)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
        rule.waitForIdle()
    }
    private fun scroll(title: String) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
    }
    private fun category(title: String) {
        nav("Настройки");scroll(title);rule.onNodeWithText(title).performClick();rule.waitForIdle()
    }
    private fun diagnostics() {
        nav("Колесо");scroll("Диагностика");rule.onNodeWithText("Диагностика").performClick();rule.waitForIdle()
    }
    private fun shot(name: String) {
        rule.waitForIdle()
        // uiAutomation reads the rendered window, which can lag Compose's semantic tree.
        android.os.SystemClock.sleep(300)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val dir=File(rule.activity.getExternalFilesDir(null),"qa-expressive-v2").apply {mkdirs()}
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun offlineRideExplainsNextStepWithoutInventingReadings() {
        render(VelaUi(),dark=false)
        rule.onNodeWithText("Всё начинается с подключения").assertIsDisplayed()
        rule.onNodeWithText("Найти колесо").assertIsDisplayed()
        rule.onAllNodesWithText("0,0").assertCountEquals(0)
        rule.onAllNodesWithText("Демо",substring=true).assertCountEquals(0)
        rule.onAllNodes(isToggleable()).onFirst().assertIsNotEnabled()
        shot("offline-ride-light")
    }
    @Test fun connectedRideHasWorkingQuickActionsAndTwoThemes() {
        val sent=mutableListOf<V12Protocol.Write>()
        render(sample(),actions=Actions(write={sent+=it}))
        assertTrue(sent.isEmpty())
        rule.onAllNodes(isToggleable()).onFirst().performClick()
        assertEquals(1,sent.size);assertEquals(V12Protocol.Control.LIGHTS,sent.single().kind)
        assertEquals(0,sent.single().a)
        shot("ride-dark")
        render(sample(),dark=false);shot("ride-light")
    }
    @Test fun settingsAreCategoriesWithRealBackNavigation() {
        render(sample(),1,dark=false);shot("settings-light")
        scroll("Свет");rule.onNodeWithText("Свет").performClick()
        rule.onNodeWithText("Ближний свет").assertIsDisplayed()
        rule.onAllNodesWithText("Пороги сигнала").assertCountEquals(0)
        shot("lights-light")
        Espresso.pressBack();rule.waitForIdle()
        rule.onNodeWithText("Оформление").assertExists()
        render(sample(),1);shot("settings-dark")
    }
    @Test fun lightsWriteExactlyOnceAndBrightnessRequiresApply() {
        val sent=mutableListOf<V12Protocol.Write>()
        render(sample(),1,actions=Actions(write={sent+=it}))
        category("Свет");assertTrue(sent.isEmpty())
        rule.onAllNodes(isToggleable()).onFirst().performClick()
        assertEquals(1,sent.size);assertEquals(V12Protocol.Control.LIGHTS,sent.single().kind)
        scroll("Яркость фар");rule.onNodeWithText("Яркость фар").performClick()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).onFirst().performSemanticsAction(SemanticsActions.SetProgress) {it(25f)}
        assertEquals(1,sent.size)
        scroll("Применить");rule.onNodeWithText("Применить").performClick()
        assertEquals(2,sent.size);assertEquals(V12Protocol.Control.BRIGHTNESS,sent.last().kind)
        assertEquals(25,sent.last().a);assertEquals(80,sent.last().b)
        shot("lights-expanded")
    }
    @Test fun movingWheelKeepsAccessoriesEnabledAndLocksRideSettings() {
        val base=sample()
        val moving=base.copy(wheel=base.wheel.copy(telemetry=base.wheel.telemetry!!.copy(speed=10.0),stationaryPackets=0))
        render(moving)
        rule.onAllNodes(isToggleable()).onFirst().assertIsEnabled()
        category("Езда")
        rule.onNodeWithText("Бездорожье").assertIsNotEnabled()
        shot("ride-settings-moving")
    }
    @Test fun everyCategoryIsReachableAndKeepsItsOwnControls() {
        render(sample(),1)
        category("Звук");rule.onNodeWithText("Без звука").assertIsDisplayed();shot("sound-dark")
        rule.onNodeWithText("Назад").performClick()
        scroll("Педали");rule.onNodeWithText("Педали").performClick()
        rule.onNodeWithText("Чувствительность педалей").assertIsDisplayed();shot("pedals-dark")
        rule.onNodeWithText("Назад").performClick()
        scroll("Система");rule.onNodeWithText("Система").performClick()
        rule.onNodeWithText("Блокировка колеса").assertIsDisplayed();shot("system-dark")
    }
    @Test fun appearanceShowsRealSelectionsAndOnlyRequestsAChangeOnce() {
        var toggles=0
        render(sample(),1,actions=Actions(theme={toggles++}))
        rule.onNodeWithText("Оформление").performClick()
        shot("appearance-dark")
        rule.onNodeWithText("Тёмная").performClick();assertEquals(0,toggles)
        rule.onNodeWithText("Светлая").performClick();assertEquals(1,toggles)
    }
    @Test fun savedWheelConnectsDirectlyAndForgetRequiresConfirmation() {
        var connects=0;var scans=0;var forgotten=0
        render(VelaUi(saved=sample().saved),2,actions=Actions(connect={connects++},scan={scans++},forget={forgotten++}))
        shot("wheel-dark")
        rule.onNodeWithText("Подключить").performClick();assertEquals(1,connects);assertEquals(0,scans)
        rule.onNodeWithText("Забыть колесо").performClick();assertEquals(0,forgotten)
        rule.onNodeWithText("Забыть колесо?").assertIsDisplayed()
        rule.onNodeWithText("Забыть",substring=false).performClick();assertEquals(1,forgotten)
    }
    @Test fun diagnosticsKeepsRawExportAndSearchAcrossNavigation() {
        render(sample(),2)
        diagnostics();shot("diagnostics-dark")
        scroll("Raw / пакеты");rule.onNodeWithText("Raw / пакеты").performClick();shot("diagnostics-raw")
        scroll("Найти поле или пакет")
        rule.onNode(hasSetTextAction()).performTextInput("zz-no-such-field")
        rule.onNode(hasSetTextAction()).performImeAction()
        scroll("Ничего не найдено");rule.onNodeWithText("Ничего не найдено").assertIsDisplayed()
        nav("Настройки");diagnostics()
        rule.onNode(hasSetTextAction()).assertTextContains("zz-no-such-field")
        shot("diagnostics-empty")
    }
    @Test fun connectedWheelNeverBecomesOfflineFromBriefPollGaps() {
        val base=sample();val current=mutableStateOf(base)
        rule.runOnUiThread {rule.activity.setContent {IonTheme(true) {IonApp(current.value,true,PrefsUi(),Actions(),2)}}}
        for(at in 10000L..13000L step 250) {
            rule.runOnUiThread {current.value=wheelUiSnapshot(base.wheel.copy(lastPacketAt=at),base.saved) {at+1}}
            rule.waitForIdle();rule.onNodeWithText("На связи").assertIsDisplayed()
            rule.onAllNodesWithText("Офлайн").assertCountEquals(0)
        }
        rule.runOnUiThread {current.value=base.copy(now=16000,staleSince=13000)}
        rule.onNodeWithText("На связи").assertIsDisplayed()
        nav("Поездка")
        rule.onNodeWithText("Показания не обновляются · BLE остаётся подключён").assertExists()
        rule.onAllNodes(isToggleable()).onFirst().assertIsEnabled();shot("ride-stale")
    }
    @Test fun shortHiccupStaysQuiet() {
        val base=sample()
        render(base.copy(now=12200,staleSince=12200))
        rule.onAllNodesWithText("Показания не обновляются · BLE остаётся подключён").assertCountEquals(0)
    }
    @Test fun quickWriteFailureKeepsReadbackAndExplainsFailure() {
        val base=sample()
        render(base.copy(wheel=base.wheel.copy(writeMessage="Команда не подтверждена")))
        rule.onNodeWithText("Команда не подтверждена").assertIsDisplayed()
        rule.onAllNodes(isToggleable()).onFirst().assertIsOn()
    }
    @Test fun narrowLargeTextKeepsCategoriesActionsAndBackReachable() {
        render(sample(),1,width=320.dp,fontScale=1.6f)
        category("Свет")
        scroll("Яркость фар");rule.onNodeWithText("Яркость фар").performClick()
        scroll("Применить");rule.onNodeWithText("Применить").assertIsDisplayed()
        shot("narrow-expanded")
        nav("Колесо")
        rule.onNodeWithText("Колесо").assertIsDisplayed()
    }
    @Test fun wideWindowAndBatteryZeroRemainHonest() {
        val base=sample()
        render(base.copy(wheel=base.wheel.copy(telemetry=base.wheel.telemetry!!.copy(battery=0.0))),dark=false,densityOverride=1.5f)
        rule.onNodeWithText("0%").assertIsDisplayed()
        shot("wide-light")
    }
    @Test fun rememberedWheelIdentityIsUnchanged() {
        val name="ion_ui_refactor_identity_test"
        val store=WheelStore(rule.activity,name);store.forget();assertNull(store.load())
        val device=WheelDevice("AA:BB:CC:DD:EE:FF","My V12 HT",-50)
        store.saveVerified(device)
        assertEquals(device.address,WheelStore(rule.activity,name).load()!!.address)
        store.forget();assertNull(WheelStore(rule.activity,name).load())
    }
}
