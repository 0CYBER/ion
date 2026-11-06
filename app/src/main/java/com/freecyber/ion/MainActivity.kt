package com.freecyber.ion

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val model: VelaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(savedInstanceState==null) model.autoConnect()
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            val preference by model.dark.collectAsStateWithLifecycle()
            val dashboard by model.prefs.dashboard.collectAsStateWithLifecycle()
            val alerts by model.prefs.alerts.collectAsStateWithLifecycle()
            val background by model.prefs.background.collectAsStateWithLifecycle()
            val trips by model.history.trips.collectAsStateWithLifecycle()
            var notificationsAllowed by remember { mutableStateOf(IonNotifications.canPost(this)) }
            val dark=systemDark(preference)
            // Permission may change in system settings while we are away.
            LifecycleResumeEffect(Unit) { notificationsAllowed=IonNotifications.canPost(this@MainActivity); onPauseOrDispose { } }
            var pendingDevice by remember { mutableStateOf<WheelDevice?>(null) }
            val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notificationsAllowed=IonNotifications.canPost(this)
            }
            fun askNotifications() { if(Build.VERSION.SDK_INT >= 33 && !IonNotifications.canPost(this)) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
            val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if(result.values.all { it } && model.ble.hasPermissions()) {
                    pendingDevice?.let(model::connect) ?: model.scan()
                    // Ask right after Bluetooth, when the rider understands why a live card is useful.
                    askNotifications()
                } else model.ble.permissionDenied()
                pendingDevice=null
            }
            fun permitted(device: WheelDevice?) {
                if(model.ble.hasPermissions()) { if(device==null) model.scan() else model.connect(device) }
                else { pendingDevice=device; permission.launch(model.ble.permissions()) }
            }
            SideEffect { WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
            } }
            val keepOn=background.keepScreenOn && ui.connected
            DisposableEffect(keepOn) {
                if(keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { }
            }
            IonTheme(dark) {
                IonApp(ui,dark,PrefsUi(dashboard,alerts,background,notificationsAllowed,trips),Actions(scan={permitted(null)},connect={permitted(it)},
                    disconnect=model::disconnect,stopScan=model.ble::stopScan,forget=model::forget,
                    readAll=model.ble::readAll,read=model.ble::read,write=model.ble::apply,
                    theme={model.setDark(!dark)},permissions={startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))},
                    setDashboard=model.prefs::setDashboard,setAlerts=model.prefs::setAlerts,setBackground=model.prefs::setBackground,
                    requestNotifications={ if(Build.VERSION.SDK_INT >= 33) askNotifications() },
                    resetTrip=model::resetTrip,shareTrips={shareTrips(trips)},deleteTrip=model.history::delete,clearTrips=model.history::clear))
            }
        }
    }
    private fun shareTrips(trips: List<TripSummary>) {
        if(trips.isEmpty()) return
        val send=Intent(Intent.ACTION_SEND).setType("text/csv")
            .putExtra(Intent.EXTRA_SUBJECT,getString(R.string.trips_export_subject))
            .putExtra(Intent.EXTRA_TEXT,TripSummary.csv(trips))
        startActivity(Intent.createChooser(send,getString(R.string.trips_export)))
    }
    override fun onStop() { super.onStop(); if(!isChangingConfigurations) model.onBackground() }
}
