package com.freecyber.ion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.roundToInt

/** All notification wiring: three channels so the rider can silence the live card but keep alerts loud. */
object IonNotifications {
    const val CHANNEL_LIVE = "ride_live"
    const val CHANNEL_ALERTS = "ride_alerts"
    const val CHANNEL_LINK = "link_state"
    const val LIVE_ID = 1
    private const val ALERT_BASE_ID = 100

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_LIVE, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_live_hint); setShowBadge(false)
            },
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alerts_hint); enableVibration(true)
            },
            NotificationChannel(CHANNEL_LINK, context.getString(R.string.channel_link), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_link_hint)
            }))
    }

    fun canPost(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun serviceAction(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getService(context, code,
        Intent(context, WheelService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The ongoing card shown by the foreground service: live figures while connected, state otherwise. */
    fun live(context: Context, s: WheelState, trip: TripStats, reconnecting: Boolean, attempt: Int): android.app.Notification {
        val t = s.telemetry
        val connected = s.verifiedHt && s.phase == Phase.CONNECTED
        val title = when {
            connected && t != null -> context.getString(R.string.live_title_speed, t.speed.fmt(1), t.battery.roundToInt())
            reconnecting -> context.getString(R.string.live_title_reconnecting)
            s.phase == Phase.ERROR -> context.getString(R.string.live_title_lost)
            else -> context.getString(R.string.live_title_connecting)
        }
        val text = when {
            connected && t != null -> context.getString(R.string.live_text, maxOf(t.mosTemp, t.motorTemp), t.rangeKm.fmt(1), trip.distanceKm.fmt(1))
            reconnecting -> context.getString(R.string.live_text_reconnecting, attempt + 1)
            else -> s.message
        }
        val b = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_ion)
            .setContentTitle(title).setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
        if (connected && t != null) {
            val battery = t.battery.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 100) ?: 0
            b.setProgress(100, battery, false)
            // Android 16+: promoted ongoing notification shows a status-bar chip (speed while riding, charge at rest).
            b.setRequestPromotedOngoing(true)
            b.setShortCriticalText(statusChip(context, t.speed, battery))
            b.setSubText(context.getString(R.string.live_subtext, trip.maxSpeed.fmt(1)))
            b.addAction(0, context.getString(if (t.lowBeam) R.string.action_light_off else R.string.action_light_on),
                serviceAction(context, WheelService.ACTION_TOGGLE_LIGHT, 2))
        }
        b.addAction(0, context.getString(R.string.disconnect), serviceAction(context, WheelService.ACTION_DISCONNECT, 1))
        return b.build()
    }

    /** Status-bar chip text: a handful of characters, so only the number that matters right now. */
    internal fun statusChip(context: Context, speed: Double, battery: Int): String =
        if (speed.isFinite() && kotlin.math.abs(speed) >= 1.0) context.getString(R.string.widget_speed, kotlin.math.abs(speed).roundToInt().toString())
        else context.getString(R.string.live_chip_battery, battery)

    fun alert(context: Context, alert: Alert) {
        if (!canPost(context)) return
        val nm = NotificationManagerCompat.from(context)
        if (alert.kind == AlertKind.LINK_RESTORED) { nm.cancel(ALERT_BASE_ID + AlertKind.LINK_LOST.ordinal); return }
        val (title, text) = alertText(context, alert)
        val channel = if (alert.kind == AlertKind.LINK_LOST) CHANNEL_LINK else CHANNEL_ALERTS
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ion)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel == CHANNEL_ALERTS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (channel == CHANNEL_ALERTS) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try { nm.notify(ALERT_BASE_ID + alert.kind.ordinal, n) } catch (_: SecurityException) { }
    }

    internal fun alertText(context: Context, alert: Alert): Pair<String, String> = when (alert.kind) {
        AlertKind.BATTERY_LOW -> context.getString(R.string.alert_battery_title) to context.getString(R.string.alert_battery_text, alert.value)
        AlertKind.BATTERY_CRITICAL -> context.getString(R.string.alert_battery_critical_title) to context.getString(R.string.alert_battery_critical_text, alert.value)
        AlertKind.TEMPERATURE_HIGH -> context.getString(R.string.alert_temp_title) to context.getString(R.string.alert_temp_text, alert.value)
        AlertKind.SPEED_HIGH -> context.getString(R.string.alert_speed_title) to context.getString(R.string.alert_speed_text, alert.value)
        AlertKind.LINK_LOST -> context.getString(R.string.alert_link_title) to context.getString(R.string.alert_link_text)
        AlertKind.LINK_RESTORED -> context.getString(R.string.alert_link_restored) to ""
        AlertKind.WHEEL_ERROR -> context.getString(R.string.alert_error_title) to alert.detail.joinToString(", ") { WheelErrors.label(it) }
    }
}
