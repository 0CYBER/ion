package com.freecyber.ion

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design review renders of the Material 3 screens, light and dark: app/build/outputs/m3/<name>.png.
 * `.\gradlew.bat :app:testDebugUnitTest --tests "com.freecyber.ion.M3Shots*"`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w411dp-h891dp-xhdpi")
class M3Shots {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, palette: IonPalette = IonPalette.VIOLET, content: @Composable () -> Unit) {
        rule.setContent {
            IonTheme(dark, ThemeSettings(dynamic = false, palette = palette)) {
                Surface(color = MaterialTheme.colorScheme.surface) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/m3/$name.png")
    }

    private val trips = listOf(
        TripSummary(1_764_000_000_000, 1_283_000, 1_104_000, 8.02, 56.1, 26.1, 288.0, 24.7, 18.0, 44, 5261.0, 805.0, 93.4, 65.0, 78.6),
        TripSummary(1_763_985_000_000, 1_150_000, 1_020_000, 7.47, 48.3, 26.4, 240.0, 21.0, 16.0, 41),
        TripSummary(1_763_550_000_000, 1_320_000, 1_140_000, 8.21, 51.0, 25.9, 270.0, 22.0, 17.0, 43),
        TripSummary(1_761_540_000_000, 1_100_000, 990_000, 7.45, 47.2, 27.1, 236.0, 19.0, 15.0, 40),
        TripSummary(1_761_390_000_000, 1_050_000, 930_000, 6.78, 44.8, 26.2, 210.0, 18.0, 14.0, 39))

    private val saved = WheelDevice("AA:BB:CC:DD:EE:FF", "V12HT-1234", -60)

    @Test fun rideLight() = shot("ride_light") { IonApp(Samples.live(), false, PrefsUi(trips = trips), Actions()) }
    @Test fun rideDark() = shot("ride_dark", dark = true) { IonApp(Samples.live(), true, PrefsUi(trips = trips), Actions()) }
    @Test fun rideOffline() = shot("ride_offline") { IonApp(VelaUi(saved = saved), false, PrefsUi(), Actions()) }
    @Test fun historyLight() = shot("history_light") { IonApp(Samples.live(), false, PrefsUi(trips = trips), Actions(), initialTab = Tabs.HISTORY) }
    @Test fun historyDark() = shot("history_dark", dark = true) { IonApp(Samples.live(), true, PrefsUi(trips = trips), Actions(), initialTab = Tabs.HISTORY) }
    @Test fun historyEmpty() = shot("history_empty") { IonApp(Samples.live(), false, PrefsUi(), Actions(), initialTab = Tabs.HISTORY) }
    @Test fun settingsLight() = shot("settings_light") { IonApp(Samples.live(), false, PrefsUi(), Actions(), initialTab = Tabs.SETTINGS) }
    @Test fun settingsDark() = shot("settings_dark", dark = true) { IonApp(Samples.live(), true, PrefsUi(), Actions(), initialTab = Tabs.SETTINGS) }
    @Test fun wheelLight() = shot("wheel_light") { IonApp(Samples.live().copy(saved = saved), false, PrefsUi(), Actions(), initialTab = Tabs.WHEEL) }
    @Test fun wheelDark() = shot("wheel_dark", dark = true) { IonApp(VelaUi(saved = saved), true, PrefsUi(), Actions(), initialTab = Tabs.WHEEL) }
    @Test fun appearanceLight() = shot("appearance_light", palette = IonPalette.TEAL) {
        AppearanceScreen(false, ThemeSettings(ThemeMode.SYSTEM, false, IonPalette.TEAL), Actions()) {}
    }
    @Test fun appearanceDark() = shot("appearance_dark", dark = true, palette = IonPalette.ROSE) {
        AppearanceScreen(true, ThemeSettings(ThemeMode.DARK, false, IonPalette.ROSE), Actions()) {}
    }
    @Test fun background() = shot("background") { BackgroundScreen(PrefsUi(), Actions()) {} }
    @Test fun updates() = shot("updates", dark = true) { UpdatesScreen(UpdateUi(), UpdateActions()) {} }
    @Test fun ride() = shot("control_ride") { ControlScreen(Samples.live(), ControlCategory.RIDE, {}, {}) {} }
    @Test fun pedals() = shot("control_pedals", dark = true) { ControlScreen(Samples.live(), ControlCategory.PEDALS, {}, {}) {} }
    @Test fun lights() = shot("control_lights") { ControlScreen(Samples.live(), ControlCategory.LIGHTS, {}, {}) {} }
    @Test fun sound() = shot("control_sound", dark = true) { ControlScreen(Samples.live(), ControlCategory.SOUND, {}, {}) {} }
    @Test fun service() = shot("control_service") { ControlScreen(Samples.live(), ControlCategory.SERVICE, {}, {}) {} }
    @Test fun diagnostics() = shot("diagnostics", dark = true) { DiagnosticsScreen(Samples.live(), Actions()) {} }
    @Test fun dashboard() = shot("dashboard") { DashboardEditorScreen(Samples.live(), DashboardLayout.DEFAULT, {}) {} }
    @Config(qualifiers = "ru-w860dp-h400dp-land-xhdpi")
    @Test fun landscape() = shot("ride_landscape", dark = true) { IonApp(Samples.live(), true, PrefsUi(), Actions()) }
    @Test fun largeFont() {
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1.5f)) {
                IonTheme(false) { Surface(color = MaterialTheme.colorScheme.surface) { IonApp(Samples.live(), false, PrefsUi(), Actions()) } }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/m3/ride_large_font.png")
    }
    @Test fun lightEditor() {
        rule.setContent { IonTheme(true) {
            val d = LightDesign(1, "Радуга", LightDesign.Template.RAINBOW, intervalMs = 150).painted()
            LightEditor(d.copy(custom = d.custom!!.take(6)), isNew = false, onDismiss = {}, onDelete = {}) {}
        } }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/m3/light_editor.png")
    }
    @Test fun lightsPersonal() = shot("control_lights_personal", dark = true) {
        val live = Samples.live()
        val settings = live.wheel.settings?.copy(lightEffect = V12Protocol.LightEffect.PERSONAL.id)
        ControlScreen(live.copy(wheel = live.wheel.copy(settings = settings, storedLights = listOf(LightEffects.Stored(25032, 230)))),
            ControlCategory.LIGHTS, {}, {}, lights = LightUi(listOf(LightDesign(1, "Радуга", LightDesign.Template.RAINBOW, intervalMs = 150)))) {}
    }
    @Test fun trip() = shot("trip", dark = true) { TripDetailScreen(trips.first(), emptyList(), {}, {}, {}, hasCsv = true) }
}
