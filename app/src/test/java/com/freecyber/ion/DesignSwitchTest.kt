package com.freecyber.ion

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class DesignSwitchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun orionIsTheDefaultDesign() = assertEquals(UiDesign.ORION, UiDesign.of(null))

    @Test fun styleStepPicksADesignAndOnlyContinueFinishesIt() {
        val picked = mutableListOf<UiDesign>()
        var done = 0
        rule.setContent { IonTheme(false) { StyleStep(UiDesign.ORION, false, ThemeSettings(dynamic = false), { picked += it }) { done++ } } }
        rule.onNodeWithText("Orion").assertIsSelected()
        rule.onNodeWithText("Material").performClick()
        rule.runOnIdle { assertEquals(listOf(UiDesign.MATERIAL), picked); assertEquals(0, done) }
        rule.onNodeWithTag("style-continue").performClick()
        rule.runOnIdle { assertEquals(1, done) }
    }

    @Test fun pickingTheCurrentDesignRequestsNothing() {
        val picked = mutableListOf<UiDesign>()
        rule.setContent { IonTheme(false) { DesignPicker(UiDesign.MATERIAL, false, ThemeSettings(dynamic = false), { picked += it }) } }
        rule.onNodeWithText("Material").performClick()
        rule.runOnIdle { assertEquals(emptyList<UiDesign>(), picked) }
    }
}
