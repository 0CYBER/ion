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

/** Renders every major screen on the JVM so layout changes can be reviewed as images without a device.
 * Output: app/build/outputs/roborazzi/<name>.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1400dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            IonTheme(dark) {
                Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val trips = listOf(
        TripSummary(1_762_400_000_000, 2_400_000, 2_100_000, 14.2, 38.4, 24.3, 410.0, 35.0, 18.0, 52),
        TripSummary(1_762_300_000_000, 900_000, 800_000, 4.1, 29.0, 18.5, 95.0, 9.0, 6.0, 41))

    @Test fun rideLive() = shot("ride_live") {
        RideScreen(Samples.live(), DashboardLayout.DEFAULT, {}, {}, {}, {}, {}, {})
    }
    @Test fun rideLiveDark() = shot("ride_live_dark", dark = true) {
        RideScreen(Samples.live(), DashboardLayout.DEFAULT, {}, {}, {}, {}, {}, {})
    }
    @Test fun rideOffline() = shot("ride_offline") {
        RideScreen(VelaUi(saved = WheelDevice("AA:BB:CC:DD:EE:FF", "V12HT-1234", -60)), DashboardLayout.DEFAULT, {}, {}, {}, {}, {}, {})
    }
    @Test fun rideAllTiles() = shot("ride_all_tiles") {
        val all = DashboardLayout(DashboardLayout.DEFAULT.slots.map { it.copy(visible = true) })
        RideScreen(Samples.live(), all, {}, {}, {}, {}, {}, {})
    }
    @Test fun dashboardEditor() = shot("dashboard_editor") { DashboardEditorScreen(DashboardLayout.DEFAULT, {}, {}) }
    @Test fun background() = shot("background") { BackgroundScreen(PrefsUi(notificationsAllowed = false), Actions(), {}) }
    @Test fun tripsList() = shot("trips") { TripsScreen(trips, Actions(), {}) }
    @Test fun tripsEmpty() = shot("trips_empty", dark = true) { TripsScreen(emptyList(), Actions(), {}) }
    @Test fun settings() = shot("settings") { SettingsScreen({}, {}, {}, {}, {}) }
}

/** Realistic wheel state built from protocol frames, shared by screenshot tests. */
object Samples {
    fun live(): VelaUi {
        val p = ByteArray(66)
        fun u16(i: Int, v: Int) { p[i] = v.toByte(); p[i + 1] = (v ushr 8).toByte() }
        u16(0, 9680); u16(2, 1250); u16(4, 2340); u16(6, 125); u16(8, 4200); u16(22, 1234); u16(24, 7800); u16(26, 4120)
        p[40] = 220.toByte(); p[41] = 226.toByte(); p[55] = 1
        val telemetry = V12Protocol.Frame(0x14, 0x84, p)
        val s = ByteArray(42); s[0] = 0x20
        fun set(i: Int, v: Int) { s[i] = v.toByte(); s[i + 1] = (v ushr 8).toByte() }
        set(9, 5000); set(11, 3000); set(13, 4000); set(17, 600); s[20] = 50; s[21] = 60; s[22] = 40; s[26] = 70; s[27] = 80; s[39] = 9
        val settings = V12Protocol.Frame(0x14, 0xa0, s)
        val trip = TripStats(startedAt = 1_000, lastAt = 2_000_000, samples = 4000, distanceKm = 12.34, maxSpeed = 41.2,
            movingMs = 1_800_000, energyWh = 380.0, regenWh = 30.0, startBattery = 96.0, lastBattery = 78.0, maxTemp = 50, maxPower = 2900.0)
        return VelaUi(WheelState(phase = Phase.CONNECTED, message = "Данные с колеса", verifiedHt = true,
            telemetry = V12Protocol.telemetry(telemetry), settings = V12Protocol.settings(settings), settingsAt = 9500, lastPacketAt = 9500,
            packets = 128, stationaryPackets = 0),
            WheelDevice("AA:BB:CC:DD:EE:FF", "V12HT-1234", -55), 10_000, trip = trip)
    }
}
