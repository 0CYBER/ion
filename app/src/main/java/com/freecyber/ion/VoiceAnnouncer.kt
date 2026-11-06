package com.freecyber.ion

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Announces each whole [step] of battery lost, e.g. 80 → 70 → 60. Pure; one announcement per crossing. */
class BatteryStepper(private val step: Int = 10) {
    private var lastBucket: Int? = null
    fun reset() { lastBucket = null }
    /** Returns the level to announce, or null. Charging re-bases so the next drop is measured from the new level. */
    fun next(battery: Double?, charging: Boolean): Int? {
        val b = battery?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val bucket = (b / step).toInt()
        val last = lastBucket
        // Only charging lets the base climb, so a reading wobbling across a boundary is announced once.
        lastBucket = if (last == null || charging) bucket else minOf(last, bucket)
        return if (last != null && !charging && bucket < last) bucket * step else null
    }
}

/** Text-to-speech for riders with the phone in a pocket. The engine is created lazily on first use and released
 * when voice is switched off, so riders who never enable it pay nothing. */
class VoiceAnnouncer(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    fun speak(text: String) {
        if (text.isBlank()) return
        if (tts == null) { ready = false; tts = TextToSpeech(context.applicationContext, this) }
        if (ready) say(text) else { if (pending.size >= 4) pending.removeFirst(); pending.addLast(text) }
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        ready = status == TextToSpeech.SUCCESS
        if (!ready) { pending.clear(); return }
        val russian = Locale.forLanguageTag("ru-RU")
        if (engine.isLanguageAvailable(russian) >= TextToSpeech.LANG_AVAILABLE) engine.language = russian
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        while (pending.isNotEmpty()) say(pending.removeFirst())
    }

    private fun say(text: String) { tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "ion-${text.hashCode()}") }

    fun shutdown() { tts?.shutdown(); tts = null; ready = false; pending.clear() }

    companion object {
        fun phrase(context: Context, alert: Alert): String = when (alert.kind) {
            AlertKind.BATTERY_LOW -> context.getString(R.string.voice_battery, alert.value)
            AlertKind.BATTERY_CRITICAL -> context.getString(R.string.voice_battery_critical, alert.value)
            AlertKind.TEMPERATURE_HIGH -> context.getString(R.string.voice_temperature, alert.value)
            AlertKind.SPEED_HIGH -> context.getString(R.string.voice_speed, alert.value)
            AlertKind.LINK_LOST -> context.getString(R.string.voice_link_lost)
            AlertKind.LINK_RESTORED -> context.getString(R.string.voice_link_restored)
            AlertKind.WHEEL_ERROR -> context.getString(R.string.voice_error)
        }
    }
}
