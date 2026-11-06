package com.freecyber.ion

internal data class Actions(
    val scan: () -> Unit = {}, val connect: (WheelDevice) -> Unit = {}, val disconnect: () -> Unit = {},
    val stopScan: () -> Unit = {}, val forget: () -> Unit = {}, val readAll: () -> Unit = {},
    val read: (V12Protocol.Read) -> Unit = {}, val write: (V12Protocol.Write) -> Unit = {},
    val theme: () -> Unit = {}, val permissions: () -> Unit = {},
    val setDashboard: (DashboardLayout) -> Unit = {},
    val setAlerts: (AlertConfig) -> Unit = {},
    val setBackground: (BackgroundConfig) -> Unit = {},
    val requestNotifications: () -> Unit = {},
    val resetTrip: () -> Unit = {},
)

/** App-level preferences rendered by settings screens; separate from wheel state so it changes rarely. */
internal data class PrefsUi(
    val dashboard: DashboardLayout = DashboardLayout.DEFAULT,
    val alerts: AlertConfig = AlertConfig(),
    val background: BackgroundConfig = BackgroundConfig(),
    val notificationsAllowed: Boolean = true,
)
