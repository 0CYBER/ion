package com.freecyber.ion

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays [AlertTones] beeps. Navigation-guidance usage, like the spoken battery steps: it is heard in headphones
 * and briefly ducks music instead of stopping it. One alert at a time; a new one replaces the playing beep. */
class AlertBeeper {
    private var track: AudioTrack? = null

    @Synchronized fun play(kind: AlertKind) {
        val pcm = AlertTones.pcm(kind)
        if (pcm.isEmpty()) return
        release()
        val next = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AlertTones.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        }.getOrNull() ?: return
        if (next.write(pcm, 0, pcm.size) != pcm.size) { next.release(); return }
        // Free the static buffer once the last sample is played.
        next.notificationMarkerPosition = pcm.size
        next.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { synchronized(this@AlertBeeper) { if (track === t) release() } }
            override fun onPeriodicNotification(t: AudioTrack) = Unit
        })
        track = next
        runCatching { next.play() }.onFailure { release() }
    }

    @Synchronized private fun release() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
