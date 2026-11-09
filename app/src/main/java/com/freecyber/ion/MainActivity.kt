package com.freecyber.ion

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val model: VelaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced=false
        // The system language may have changed while the process lived on.
        IonFormat.locale = java.util.Locale.forLanguageTag(getString(R.string.format_locale))
        if(savedInstanceState==null) model.autoConnect()
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle()
            val preference by model.dark.collectAsStateWithLifecycle()
            val dashboard by model.prefs.dashboard.collectAsStateWithLifecycle()
            val alerts by model.prefs.alerts.collectAsStateWithLifecycle()
            val background by model.prefs.background.collectAsStateWithLifecycle()
            val trips by model.history.trips.collectAsStateWithLifecycle()
            var notificationsAllowed by remember { mutableStateOf(IonNotifications.canPost(this)) }
            var batteryUnrestricted by remember { mutableStateOf(batteryUnrestricted()) }
            var locationAllowed by remember { mutableStateOf(model.ble.grantedLocation()) }
            val dark=systemDark(preference)
            // Permission may change in system settings while we are away.
            LifecycleResumeEffect(Unit) {
                notificationsAllowed=IonNotifications.canPost(this@MainActivity); batteryUnrestricted=batteryUnrestricted()
                locationAllowed=model.ble.grantedLocation()
                model.refreshRecording()
                onPauseOrDispose { }
            }
            var pendingDevice by remember { mutableStateOf<WheelDevice?>(null) }
            val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notificationsAllowed=IonNotifications.canPost(this)
            }
            fun askNotifications() { if(Build.VERSION.SDK_INT >= 33 && !IonNotifications.canPost(this)) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
            val location=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                locationAllowed=model.ble.grantedLocation()
                model.refreshRecording()
            }
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
            KeepRideScreenOn(ui,background.keepScreenOn)
            IonTheme(dark) {
                IonAppContent({ui.value},dark,PrefsUi(dashboard,alerts,background,notificationsAllowed,batteryUnrestricted,trips,locationAllowed,themeFollowsSystem=preference==null),Actions(scan={permitted(null)},connect={permitted(it)},
                    disconnect=model::disconnect,stopScan=model.ble::stopScan,forget=model::forget,
                    readAll=model.ble::readAll,read=model.ble::read,write=model.ble::apply,
                    theme={model.setDark(!dark)},followSystemTheme={model.setDark(if(it) null else dark)},permissions={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))},
                    setDashboard=model.prefs::setDashboard,setAlerts=model.prefs::setAlerts,setBackground=model.prefs::setBackground,
                    requestNotifications={ if(Build.VERSION.SDK_INT >= 33) askNotifications() },
                    requestBatteryUnrestricted=::requestBatteryUnrestricted,
                    requestLocation={ location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    resetTrip=model::resetTrip,shareCsv={exportTrip(it,false)},shareGpx={exportTrip(it,true)},
                    deleteTrip=model.history::delete,clearTrips=model.history::clear,shareAllCsv=::exportAllTrips,
                    tripTrack=model.history::track,tripHasTelemetry=model.history::hasTelemetry))
            }
        }
    }
    @Composable private fun KeepRideScreenOn(ui: State<VelaUi>, enabled: Boolean) {
        val keepOn=enabled && ui.value.connected
        DisposableEffect(keepOn) {
            if(keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { }
        }
    }
    private fun batteryUnrestricted()=getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName)?:true
    /** A companion app for a ride-safety device: the direct request is allowed, the settings list is the fallback. */
    @SuppressLint("BatteryLife")
    private fun requestBatteryUnrestricted() {
        val direct=Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName"))
        try { startActivity(direct) }
        catch(_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
    private fun exportTrip(trip: TripSummary, gpx: Boolean) {
        lifecycleScope.launch {
            try {
                model.journal.awaitWrites()
                val file = withContext(Dispatchers.IO) {
                    val directory = exportDirectory()
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm",java.util.Locale.US).format(Date(trip.startedAt))
                    val target = File(directory,"Ion_${stamp}.${if(gpx) "gpx" else "csv"}")
                    if(gpx) {
                        val points=model.history.track(trip.startedAt)
                        check(points.size>=2)
                        val name=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(trip.startedAt))
                        target.writeText(TrackCodec.gpx(points,trip.startedAt,name))
                    } else model.journal.csv(trip.startedAt).copyTo(target,overwrite=true)
                    target
                }
                startActivity(Intent.createChooser(TripExport.intent(this@MainActivity,file,
                    if(gpx) "application/gpx+xml" else "text/csv"),getString(R.string.trips_export_subject)))
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { android.widget.Toast.makeText(this@MainActivity,R.string.trip_export_failed,android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    /** Shared files live in the cache; anything older than a week has long been delivered and is dropped. */
    private fun exportDirectory(): File = File(cacheDir,"trip_exports").apply {
        mkdirs()
        listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>7*86_400_000L }?.forEach { it.delete() }
    }
    private fun exportAllTrips() {
        lifecycleScope.launch {
            try {
                val trips = model.history.trips.value
                check(trips.isNotEmpty())
                val file = withContext(Dispatchers.IO) {
                    val directory = exportDirectory()
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(Date())
                    File(directory,"Ion_rides_${stamp}.csv").apply { writeText(TripSummary.csv(trips)) }
                }
                startActivity(Intent.createChooser(TripExport.intent(this@MainActivity,file,"text/csv"),getString(R.string.trips_export_subject)))
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { android.widget.Toast.makeText(this@MainActivity,R.string.trip_export_failed,android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    override fun onStop() { super.onStop(); if(!isChangingConfigurations) model.onBackground() }
}
