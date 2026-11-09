package com.freecyber.ion

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Plays [AlertHaptics] patterns. Alarm usage so the rhythm still arrives with the ringer on silent:
 * the rider opted in and cannot look at the screen. */
class AlertVibrator(private val context: Context) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    }

    fun play(kind: AlertKind) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = VibrationEffect.createWaveform(AlertHaptics.pattern(kind), -1)
        if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        else @Suppress("DEPRECATION") v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }
}
