package com.freecyber.ion

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Geometry and semantics checks only: no image capture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=Application::class,qualifiers="ru-w411dp-h900dp-xhdpi")
class RidePresentationTest {
    @get:Rule val rule=createComposeRule()

    @Test fun floatingDockOverlaysContentAndLastLinkScrollsClearOfIt() {
        rule.setContent { IonTheme(false) { IonApp(Samples.live(),false,PrefsUi(),Actions()) } }
        val content=rule.onNodeWithTag("app-content").getUnclippedBoundsInRoot()
        val dock=rule.onNodeWithTag("navigation-dock").getUnclippedBoundsInRoot()
        assertTrue("Content must continue behind the dock instead of ending in a rectangular band",content.bottom>dock.bottom)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Соединение активно"))
        rule.onNode(hasScrollToIndexAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {it(0f,10_000f)}
        val last=rule.onNodeWithText("Соединение активно").getUnclippedBoundsInRoot()
        assertTrue("Last link $last must clear floating dock $dock at the end of the list",last.bottom<=dock.top)
    }

    @Test fun speedIsCompactAndDoesNotShowRedundantLiveCaption() {
        rule.setContent { IonTheme(false) {
            DashTileView(TileSlot(DashTile.SPEED,true,true),TileEnv(Samples.live(),{},{}),Modifier.width(371.dp))
        } }
        rule.onAllNodesWithText("Показания с колеса").assertCountEquals(0)
        val bounds=rule.onNodeWithTag("speed-instrument").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom-bounds.top<220.dp)
    }

    @Test fun chargingUsesNativeIndeterminateWaveEvenAtFullChargeAndStopsOnDisconnect() {
        val sample=Samples.live()
        var ui by mutableStateOf(sample.copy(wheel=sample.wheel.copy(telemetry=sample.wheel.telemetry!!.copy(battery=100.0,charging=true))))
        rule.setContent { IonTheme(false) {
            DashTileView(TileSlot(DashTile.BATTERY,true,false),TileEnv(ui,{},{}),Modifier.width(180.dp))
        } }
        rule.onNodeWithTag("battery-charge-wave",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo.Indeterminate))
        rule.onNodeWithContentDescription("Зарядка").assertExists()
        // The tile is one TalkBack stop: label, value and charging state are read together.
        rule.onNode(hasText("Заряд").and(hasContentDescription("Зарядка"))).assertExists()
        rule.runOnIdle { ui=ui.copy(wheel=ui.wheel.copy(phase=Phase.OFFLINE)) }
        rule.onNodeWithTag("battery-charge-wave",useUnmergedTree=true).assertDoesNotExist()
        rule.onNodeWithTag("battery-level-wave",useUnmergedTree=true).assertExists()
    }
}
