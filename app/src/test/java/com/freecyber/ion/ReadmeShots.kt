package com.freecyber.ion

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Images for README.md / README.ru.md, written straight into docs/readme/<lang>/. Not part of the normal test run:
 * `.\gradlew.bat :app:testDebugUnitTest -PreadmeShots --tests "com.freecyber.ion.Readme*"`.
 */
abstract class ReadmeShots(private val lang: String) {
    @get:Rule val rule = createComposeRule()

    private fun out(name: String) = "../docs/readme/$lang/$name.png"

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        IonFormat.locale = java.util.Locale.forLanguageTag(lang)
        try {
            rule.setContent { IonTheme(dark) { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { content() } } } }
            rule.onRoot().captureRoboImage(out(name))
        } finally { IonFormat.locale = java.util.Locale.forLanguageTag("ru") }
    }

    private val trips = listOf(
        TripSummary(1_764_000_000_000, 1_283_000, 1_104_000, 8.02, 56.1, 26.1, 288.0, 24.7, 18.0, 44, 5261.0, 805.0, 93.4, 65.0, 78.6),
        TripSummary(1_763_985_000_000, 1_150_000, 1_020_000, 7.47, 48.3, 26.4, 240.0, 21.0, 16.0, 41),
        TripSummary(1_763_550_000_000, 1_320_000, 1_140_000, 8.21, 51.0, 25.9, 270.0, 22.0, 17.0, 43),
        TripSummary(1_763_540_000_000, 1_100_000, 990_000, 7.45, 47.2, 27.1, 236.0, 19.0, 15.0, 40),
        TripSummary(1_763_390_000_000, 1_050_000, 930_000, 6.78, 44.8, 26.2, 210.0, 18.0, 14.0, 39),
        TripSummary(1_763_200_000_000, 2_400_000, 2_100_000, 14.2, 38.4, 24.3, 410.0, 35.0, 24.0, 52))

    private fun sampleRide(): Pair<List<TrackPoint>, List<Moment>> {
        val track = List(160) { i ->
            val a = i / 160.0 * 2 * Math.PI
            TrackPoint(i * 8_000L, 54.352 + 0.006 * Math.sin(a) + 0.002 * Math.sin(3 * a), 18.646 + 0.012 * Math.cos(a),
                40.0 + 15 * Math.sin(a), 7.0 + 2 * Math.sin(5 * a))
        }
        val moments = List(1_280) { i ->
            val a = i / 1_280.0 * 2 * Math.PI
            val speed = (24 + 16 * Math.sin(a * 5) + 6 * Math.sin(a * 23)).coerceAtLeast(0.0)
            Moment(i * 1_000L, speed, 88 - i * 0.014, speed * 0.7, speed * 56, speed * 1.5, 82.0 - i * 0.004,
                30 + i / 120, 34 + i / 140, i * 0.0063)
        }
        return track to moments
    }

    private fun name(en: String, ru: String) = if (lang == "ru") ru else en
    private val designs get() = listOf(
        LightDesign(1, name("Rainbow", "Радуга"), LightDesign.Template.RAINBOW, intervalMs = 150, wheelId = LightEffects.ION_ID_BASE + 1),
        LightDesign(2, name("Night", "Ночной"), LightDesign.Template.CHASE, 0x00E5FF, 0x000000, 150),
        LightDesign(3, name("Police", "Полиция"), LightDesign.Template.HALVES, 0xFF0000, 0x0000FF, 300))

    @Test fun ride() = shot("ride", dark = true) { IonApp(Samples.live(), true, PrefsUi(trips = trips), Actions()) }
    @Test fun history() = shot("history", dark = false) { IonApp(Samples.live(), false, PrefsUi(trips = trips), Actions(), initialTab = 1) }
    @Test fun settings() = shot("settings", dark = false) { IonApp(Samples.live(), false, PrefsUi(trips = trips), Actions(), initialTab = 2) }
    @Test fun trip() = shot("trip", dark = true) {
        val (track, moments) = sampleRide()
        TripDetailScreen(trips.first(), track, {}, {}, {}, hasCsv = true, moments = moments)
    }
    @Test fun lights() = shot("lights", dark = true) {
        val live = Samples.live()
        val settings = live.wheel.settings?.copy(lightEffect = V12Protocol.LightEffect.PERSONAL.id)
        val ui = live.copy(wheel = live.wheel.copy(settings = settings, storedLights = listOf(LightEffects.Stored(25032, 230),
            LightEffects.Stored(LightEffects.ION_ID_BASE + 1, 440))))
        ControlScreen(ui, ControlCategory.LIGHTS, {}, {}, lights = LightUi(designs)) {}
    }
    @Test fun editor() {
        IonFormat.locale = java.util.Locale.forLanguageTag(lang)
        rule.setContent {
            IonTheme(true) {
                val d = LightDesign(1, name("Rainbow", "Радуга"), LightDesign.Template.RAINBOW, intervalMs = 150).painted()
                LightEditor(d.copy(custom = d.custom!!.take(6)), isNew = false, onDismiss = {}, onDelete = {}) {}
            }
        }
        captureScreenRoboImage(out("editor"))
    }

    /** The README banner: the app icon on the brand hero, with the name and what Ion is for. */
    @Config(qualifiers = "+w640dp-h300dp")
    @Test fun banner() = shot("banner", dark = true) {
        val c = MaterialTheme.colorScheme
        val tagline = if (lang == "ru") "Приложение для моноколёс INMOTION V12" else "A companion app for INMOTION V12 wheels"
        val points = if (lang == "ru") "Поездки · Свет · Настройки · Виджет" else "Rides · Lights · Settings · Widget"
        Row(Modifier.fillMaxSize().background(c.primaryContainer).padding(horizontal = 48.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // The launcher icon as the phone shows it: the foreground on its near-black tile.
            Box(Modifier.size(200.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(52.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF141218))) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
                    Modifier.fillMaxSize().scale(1.5f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ion", style = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = 88.sp, lineHeight = 88.sp), color = c.onPrimaryContainer)
                Text(tagline, style = MaterialTheme.typography.headlineSmall, color = c.onPrimaryContainer)
                Text(points, style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer.copy(alpha = .7f))
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w411dp-h891dp-xhdpi")
class ReadmeShotsEn : ReadmeShots("en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w411dp-h891dp-xhdpi")
class ReadmeShotsRu : ReadmeShots("ru")
