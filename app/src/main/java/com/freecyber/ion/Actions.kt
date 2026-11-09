package com.freecyber.ion

internal data class Actions(
    val scan: () -> Unit = {}, val connect: (WheelDevice) -> Unit = {}, val disconnect: () -> Unit = {},
    val stopScan: () -> Unit = {}, val forget: () -> Unit = {}, val readAll: () -> Unit = {},
    val read: (V12Protocol.Read) -> Unit = {}, val write: (V12Protocol.Write) -> Unit = {},
    val theme: () -> Unit = {}, val permissions: () -> Unit = {},
    /** true = follow the phone's dark mode; false = pin the theme currently shown. */
    val followSystemTheme: (Boolean) -> Unit = {},
    val setDashboard: (DashboardLayout) -> Unit = {},
    val setAlerts: (AlertConfig) -> Unit = {},
    val setBackground: (BackgroundConfig) -> Unit = {},
    val requestNotifications: () -> Unit = {},
    val requestBatteryUnrestricted: () -> Unit = {},
    val requestLocation: () -> Unit = {},
    val resetTrip: () -> Unit = {},
    val shareCsv: (TripSummary) -> Unit = {},
    val shareGpx: (TripSummary) -> Unit = {},
    val deleteTrip: (TripSummary) -> Unit = {},
    val clearTrips: () -> Unit = {},
    /** GPS fixes stored for a finished ride, keyed by its startedAt. */
    val tripTrack: (Long) -> List<TrackPoint> = { emptyList() },
    val tripHasTelemetry: (Long) -> Boolean = { false },
)

/** App-level preferences rendered by settings screens; separate from wheel state so it changes rarely. */
internal data class PrefsUi(
    val dashboard: DashboardLayout = DashboardLayout.DEFAULT,
    val alerts: AlertConfig = AlertConfig(),
    val background: BackgroundConfig = BackgroundConfig(),
    val notificationsAllowed: Boolean = true,
    /** Doze exemption; without it the background link may be cut on some firmwares. */
    val batteryUnrestricted: Boolean = true,
    val trips: List<TripSummary> = emptyList(),
    val locationAllowed: Boolean = true,
    val themeFollowsSystem: Boolean = false,
)
