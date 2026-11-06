package com.freecyber.ion

import android.app.Application
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Widget content per size class, checked on the Glance tree (no launcher needed). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class WidgetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val large = DpSize(260.dp, 180.dp)
    private val small = DpSize(110.dp, 60.dp)
    private val live = WidgetSnapshot(WidgetSnapshot.State.LIVE, battery = 78, speed = 23.4, rangeKm = 41.2, tripKm = 12.3,
        temperature = 50, lowBeam = false, hasSavedWheel = true, updatedAt = 1_000L)

    @Before fun russianNumbers() { IonFormat.locale = java.util.Locale.forLanguageTag("ru") }

    @Test fun liveLargeShowsSpeedFiguresAndActions() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(large); setContext(context)
        provideComposable { WidgetBody(context, live, large) }
        onNode(hasTextEqualTo("78%")).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.widget_speed, "23,4"))).assertExists()
        onNode(hasText("41,2")).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.disconnect))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.action_light_on))).assertExists()
    }

    @Test fun smallKeepsOnlyBatteryAndState() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(small); setContext(context)
        provideComposable { WidgetBody(context, live, small) }
        onNode(hasTextEqualTo("78%")).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.widget_speed, "23,4"))).assertDoesNotExist()
        onNode(hasTextEqualTo(context.getString(R.string.disconnect))).assertDoesNotExist()
    }

    @Test fun offlineKeepsLastFiguresAndOffersConnect() = runGlanceAppWidgetUnitTest {
        val offline = WidgetSnapshot(hasSavedWheel = true).carryOver(live)
        setAppWidgetSize(large); setContext(context)
        provideComposable { WidgetBody(context, offline, large) }
        onNode(hasTextEqualTo("78%")).assertExists()
        onNode(hasText(context.getString(R.string.status_offline))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.connect))).assertExists()
        // No live stream: speed and the headlight button make no sense.
        onNode(hasTextEqualTo(context.getString(R.string.widget_speed, "23,4"))).assertDoesNotExist()
        onNode(hasTextEqualTo(context.getString(R.string.action_light_on))).assertDoesNotExist()
    }

    @Test fun noSavedWheelHasNoConnectButton() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(large); setContext(context)
        provideComposable { WidgetBody(context, WidgetSnapshot(), large) }
        onNode(hasTextEqualTo("—")).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.connect))).assertDoesNotExist()
    }
}
