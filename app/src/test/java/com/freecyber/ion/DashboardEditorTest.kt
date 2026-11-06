package com.freecyber.ion

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w411dp-h900dp-xhdpi")
class DashboardEditorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tileControlsOpenInASheetWithoutScrollingPastThePreview() {
        var layout by mutableStateOf(DashboardLayout.DEFAULT)
        rule.setContent { IonTheme(false) { DashboardEditorScreen(Samples.live(),layout,{layout=it},{}) } }
        rule.onNodeWithTag("preview-SPEED").performClick()
        rule.onNodeWithText("Половина").assertIsDisplayed().performClick()
        rule.onNodeWithText("Готово").assertIsDisplayed()
        assertFalse(layout.visible.first().effectiveWide)
        rule.onNodeWithText("Готово").performClick()
        rule.onNodeWithTag("add-tile").assertIsDisplayed()
    }

    @Test fun canAddTwoSwitchesAndAssignThemIndependently() {
        var layout by mutableStateOf(DashboardLayout(emptyList()))
        rule.setContent { IonTheme(false) { DashboardEditorScreen(VelaUi(),layout,{layout=it},{}) } }
        rule.onNodeWithTag("add-tile").performClick()
        rule.onNodeWithTag("catalog-TOGGLE").performClick()
        rule.onNodeWithTag("target-TRANSPORT").performScrollTo().performClick()
        rule.onNodeWithText("Готово").performClick()
        rule.onNodeWithTag("add-tile").performClick()
        rule.onNodeWithTag("catalog-TOGGLE").performClick()
        rule.onNodeWithTag("target-MUTE").performScrollTo().performClick()
        rule.onNodeWithText("Готово").performClick()
        assertEquals(listOf(DashToggle.TRANSPORT,DashToggle.MUTE),layout.visible.map {it.toggleTarget})
        assertEquals(2,layout.visible.map {it.id}.toSet().size)
        assertEquals(layout,DashboardLayout.decode(layout.encode()))
        rule.onNodeWithTag("preview-${layout.visible[0].id}").performClick()
        rule.onNodeWithText("Удалить").performClick()
        assertEquals(DashToggle.MUTE,layout.visible.single().toggleTarget)
    }
}
