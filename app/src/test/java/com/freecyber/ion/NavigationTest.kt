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

    @Test fun appChildBackReturnsToSettings() {
        rule.setContent { IonTheme(false) { IonApp(VelaUi(), false, PrefsUi(), Actions(), initialTab = Tabs.SETTINGS) } }
        rule.onNodeWithText("Оформление").performClick()
        // Pages below a tab hide the navigation bar and carry a back button in their top app bar.
        rule.onNodeWithTag("navigation-dock").assertDoesNotExist()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Обновления").assertIsDisplayed()
        rule.onNodeWithTag("navigation-dock").assertIsDisplayed()
    }

    @Test fun themeModeIsOneConnectedGroup() {
        val picked = mutableListOf<ThemeMode>()
        rule.setContent { IonTheme(false) { AppearanceScreen(false, ThemeSettings(ThemeMode.LIGHT, false), Actions(themeMode = { picked += it })) {} } }
        rule.onNodeWithText("Тёмная").performClick()
        rule.onNodeWithText("Система").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(listOf(ThemeMode.DARK, ThemeMode.SYSTEM), picked) }
    }

    @Test fun historyIsTheSecondDockTabWithoutBack() {
        rule.setContent { IonTheme(false) { IonApp(VelaUi(), false, PrefsUi(), Actions()) } }
        rule.onNodeWithContentDescription("История").performClick()
        rule.onNodeWithText("Пока нет завершённых поездок", substring = true).assertExists()
        rule.onAllNodesWithContentDescription("Назад").assertCountEquals(0)
    }

    @Test fun staticSettingsDoNotReadOrObserveLiveTelemetry() {
        val live=mutableStateOf(VelaUi())
        var reads=0
        rule.setContent { IonTheme(false) { IonAppContent({reads++;live.value}, false, PrefsUi(), Actions(), initialTab=Tabs.SETTINGS) } }
        rule.runOnIdle {
            org.junit.Assert.assertEquals(0,reads)
            live.value=VelaUi(now=10_000)
        }
        rule.runOnIdle { org.junit.Assert.assertEquals(0,reads) }
    }
}
