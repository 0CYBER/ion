package com.freecyber.ion

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertCountEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression test for the "settings apply only once" bug: drafts must survive a settings refresh
 * (read-back or the periodic poll) and Apply must send exactly what the sliders show.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w411dp-h1400dp-xhdpi")
class ControlEditorTest {
    @get:Rule val rule = createComposeRule()

    private val applied = mutableStateListOf<List<Int>>()
    /** Stand-in for the wheel's current settings value; mutated to emulate a read-back. */
    private var wheelLow by mutableIntStateOf(30)

    /** Compose sets Disabled semantics instead of an Enabled flag. */
    private fun applyDisabled(): Boolean =
        SemanticsProperties.Disabled in rule.onNodeWithTag("apply").fetchSemanticsNode().config

    @Composable
    private fun editor() {
        Surface {
            ExpandSliders(Icons.Rounded.BrightnessMedium, "Яркость", "$wheelLow% · 70%", true,
                listOf(SliderSpec("Ближний", wheelLow, 0..100) { v -> "$v%" },
                    SliderSpec("Дальний", 70, 0..100) { v -> "$v%" })) { applied += it }
        }
    }

    @Test fun draftsSurviveSettingsRefreshAndApplySendsDrafts() {
        rule.setContent { IonTheme(false) { editor() } }
        rule.onNodeWithText("Яркость").performClick()
        // The rider moves low to 60.
        rule.onNodeWithTag("slider-0").performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        rule.waitForIdle()
        // A read-back lands with a different wheel value before Apply is pressed.
        rule.runOnIdle { wheelLow = 35 }
        rule.waitForIdle()
        rule.onNodeWithTag("apply").performClick()
        rule.waitForIdle()
        assertEquals(listOf(listOf(60, 70)), applied.toList())
    }

    @Test fun untouchedSlidersFollowTheWheel() {
        rule.setContent { IonTheme(false) { editor() } }
        rule.onNodeWithText("Яркость").performClick()
        rule.onAllNodesWithText("30%").assertCountEquals(1)
        rule.runOnIdle { wheelLow = 35 }
        rule.waitForIdle()
        rule.onAllNodesWithText("35%").assertCountEquals(1)
    }

    @Test fun applyWithoutEditsIsDisabled() {
        rule.setContent { IonTheme(false) { editor() } }
        rule.onNodeWithText("Яркость").performClick()
        check(applyDisabled())
        rule.onNodeWithTag("slider-0").performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        rule.waitForIdle()
        check(!applyDisabled())
    }
}
