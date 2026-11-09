package com.freecyber.ion

import android.app.Application
import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ongoing ride card: figures while live, a status-bar chip request, and actions that match the state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class NotificationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    // contentView/bigContentView are deprecated, but they are exactly what Android 16 checks before promoting.
    @Suppress("DEPRECATION")
    @Test fun liveCardShowsFiguresChipAndActions() {
        val ui = Samples.live()
        val n = IonNotifications.live(context, ui.wheel, ui.trip, reconnecting = false, attempt = 0)
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertTrue(title, title.contains("23,4"))
        assertTrue(NotificationCompat.isRequestPromotedOngoing(n))
        // Android 16 promotes only plain ongoing cards: titled, not colorized, no custom views or exotic styles.
        // (hasPromotableCharacteristics() itself needs a real API 36 runtime.)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertNull(n.contentView); assertNull(n.bigContentView)
        assertNull(n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals("23 км/ч", NotificationCompat.getShortCriticalText(n))
        assertEquals(2, n.actions.size)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test fun reconnectingCardHasNoLiveExtras() {
        val ui = Samples.live()
        val n = IonNotifications.live(context, ui.wheel.copy(phase = Phase.CONNECTING), ui.trip, reconnecting = true, attempt = 2)
        assertNull(NotificationCompat.getShortCriticalText(n))
        assertEquals(1, n.actions.size)
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("3"))
    }

    @Test fun voicePhrasesUseRussianPlurals() {
        fun battery(v: Int) = VoiceAnnouncer.phrase(context, Alert(AlertKind.BATTERY_LOW, v))
        assertEquals("Низкий заряд, 21 процент", battery(21))
        assertEquals("Низкий заряд, 22 процента", battery(22))
        assertEquals("Низкий заряд, 15 процентов", battery(15))
        assertEquals("Перегрев, 81 градус", VoiceAnnouncer.phrase(context, Alert(AlertKind.TEMPERATURE_HIGH, 81)))
    }

    @Config(qualifiers = "en")
    @Test fun englishUiGetsEnglishPhrasesAndVoice() {
        assertEquals("Overheating, 1 degree", VoiceAnnouncer.phrase(context, Alert(AlertKind.TEMPERATURE_HIGH, 1)))
        assertEquals("Low battery, 15 percent", VoiceAnnouncer.phrase(context, Alert(AlertKind.BATTERY_LOW, 15)))
        assertEquals("en-US", context.getString(R.string.voice_locale))
        assertEquals("en", context.getString(R.string.format_locale))
    }

    @Test fun chipPrefersSpeedWhileRidingAndChargeAtRest() {
        assertEquals("31 км/ч", IonNotifications.statusChip(context, 30.6, 80))
        assertEquals("80%", IonNotifications.statusChip(context, 0.4, 80))
        assertEquals("55%", IonNotifications.statusChip(context, Double.NaN, 55))
    }
}
