package com.freecyber.ion

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w411dp-h900dp-xhdpi")
class NavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun appChildBackReturnsToAppMenu() {
        rule.setContent { IonTheme(false) { IonApp(VelaUi(), false, PrefsUi(), Actions(), initialTab = 1) } }
        rule.onNodeWithContentDescription("Приложение").performClick()
        rule.onNodeWithText("Оформление").performClick()
        rule.onNodeWithText("Назад").performClick()
        rule.onNodeWithText("Экран поездки").assertIsDisplayed()
    }

    @Test fun staticSettingsDoNotReadOrObserveLiveTelemetry() {
        val live=mutableStateOf(VelaUi())
        var reads=0
        rule.setContent { IonTheme(false) { IonAppContent({reads++;live.value}, false, PrefsUi(), Actions(), initialTab=1) } }
        rule.runOnIdle {
            org.junit.Assert.assertEquals(0,reads)
            live.value=VelaUi(now=10_000)
        }
        rule.runOnIdle { org.junit.Assert.assertEquals(0,reads) }
    }
}
