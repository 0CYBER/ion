package com.freecyber.ion

import android.os.SystemClock
import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
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
        ionGraph.companionWheel.refresh()
        if(savedInstanceState==null && model.prefs.onboardingDone) model.autoConnect()
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle()
            val preference by model.dark.collectAsStateWithLifecycle()
            val dynamicColor by model.dynamicColor.collectAsStateWithLifecycle()
            val palette by model.palette.collectAsStateWithLifecycle()
            val themeSettings = ThemeSettings(when (preference) { null -> ThemeMode.SYSTEM; true -> ThemeMode.DARK; false -> ThemeMode.LIGHT },
                dynamicColor, palette)
            val dashboard by model.prefs.dashboard.collectAsStateWithLifecycle()
            val alerts by model.prefs.alerts.collectAsStateWithLifecycle()
            val background by model.prefs.background.collectAsStateWithLifecycle()
            val soundLabels by model.prefs.soundLabels.collectAsStateWithLifecycle()
            val lightDesigns by model.prefs.lightDesigns.collectAsStateWithLifecycle()
            val lightLast by model.prefs.lightLast.collectAsStateWithLifecycle()
            val updater = ionGraph.updater
            val updateState by updater.state.collectAsStateWithLifecycle()
            val updateConfig by model.prefs.updates.collectAsStateWithLifecycle()
            val updateCheckedAt by model.prefs.updateCheckedAt.collectAsStateWithLifecycle()
            val updateUi = UpdateUi(updateState, updateConfig, updateCheckedAt, updater.currentVersion, updater.currentBuild)
            val updateActions = remember(updater) { UpdateActions(setConfig = model.prefs::setUpdates, check = { updater.check(manual = true) },
                download = updater::download, dismiss = updater::dismiss, cancelDownload = updater::cancelDownload,
                openInstallSettings = updater::openInstallPermissionSettings, openPage = updater::openReleasePage) }
            val companionWheel = ionGraph.companionWheel
            var onboarding by rememberSaveable { mutableStateOf(!model.prefs.onboardingDone) }
            var startOnWheel by rememberSaveable { mutableStateOf(false) }
            var pendingPair by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
            fun finishPair(warn: Boolean) {
                val target = pendingPair ?: return
                pendingPair = null
                if (warn) showAssociationError()
                val device = ionGraph.saved.value?.takeIf { it.address == target[0] }
                    ?: WheelDevice(target[0], target[1], target[2].toInt(), target.getOrNull(3)?.ifEmpty { null })
                model.connect(device)
            }
            val association = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == RESULT_OK) companionWheel.refresh()
                finishPair(result.resultCode != RESULT_OK)
            }
            var pairingDialogLaunched by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (pendingPair != null && !pairingDialogLaunched) finishPair(true)
            }
            fun connect(device: WheelDevice) {
                if (pendingPair != null) return
                if (!companionWheel.supported || companionWheel.covers(device.address)) {
                    model.connect(device)
                    return
                }
                model.ble.stopScan()
                val target = arrayListOf(device.address, device.name, device.rssi.toString(), device.advertisedName.orEmpty())
                pendingPair = target
                pairingDialogLaunched = false
                companionWheel.associate(device.address, { sender ->
                    if (!isDestroyed && pendingPair === target) {
                        runCatching {
                            association.launch(IntentSenderRequest.Builder(sender).build())
                            pairingDialogLaunched = true
                        }.onFailure { finishPair(true) }
                    }
                }, { if (!isDestroyed && pendingPair === target) finishPair(true) })
            }
            val trips by model.history.trips.collectAsStateWithLifecycle()
            var notificationsAllowed by remember { mutableStateOf(IonNotifications.canPost(this)) }
            var batteryUnrestricted by remember { mutableStateOf(batteryUnrestricted()) }
            var locationAllowed by remember { mutableStateOf(model.ble.grantedLocation()) }
            var bluetoothAllowed by remember { mutableStateOf(model.ble.hasPermissions()) }
            fun locationServicesEnabled() = getSystemService(LocationManager::class.java)
                ?.let(LocationManagerCompat::isLocationEnabled) == true
            var locationServicesAllowed by remember { mutableStateOf(locationServicesEnabled()) }
            val dark=systemDark(preference)
            // Permission may change in system settings while we are away.
            LifecycleResumeEffect(Unit) {
                notificationsAllowed=IonNotifications.canPost(this@MainActivity); batteryUnrestricted=batteryUnrestricted()
                locationAllowed=model.ble.grantedLocation()
                bluetoothAllowed=model.ble.hasPermissions()
                locationServicesAllowed=locationServicesEnabled()
                companionWheel.refresh()
                model.refreshRecording()
                model.resumeSavedLink()
                updater.resumeInstall()
                onPauseOrDispose { }
            }
            var notificationsAskedAt by remember { mutableLongStateOf(0L) }
            val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notificationsAllowed=IonNotifications.canPost(this)
                // Only after the explicit button: an instant refusal means no dialog was shown, go to settings.
                if(!notificationsAllowed && SystemClock.elapsedRealtime()-notificationsAskedAt<500) openNotificationSettings()
            }
            fun askNotifications() { if(Build.VERSION.SDK_INT >= 33 && !IonNotifications.canPost(this)) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
            var locationAskedAt by remember { mutableLongStateOf(0L) }
            val location=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                locationAllowed=model.ble.grantedLocation()
                model.refreshRecording()
                // An instant refusal means no dialog was shown ("don't ask again"): the button used to do nothing,
                // so open the app's system settings, the only place left to grant it.
                if(!locationAllowed && SystemClock.elapsedRealtime()-locationAskedAt<500)
                    runCatching {startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}
            }
            val backupPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importTrips) }
            var bluetoothAskedAt by remember { mutableLongStateOf(0L) }
            val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                bluetoothAllowed = model.ble.hasPermissions()
                if (!bluetoothAllowed && SystemClock.elapsedRealtime() - bluetoothAskedAt < 500) openAppSettings()
            }
            fun permitted(device: WheelDevice?) {
                if(model.ble.hasPermissions()) { if(device==null) model.scan() else connect(device) }
                else onboarding = true
            }
            fun finishOnboarding(addWheel: Boolean) {
                model.prefs.onboardingDone = true
                if (addWheel) {
                    val saved = ionGraph.saved.value
                    startOnWheel = true
                    if (saved == null) model.scan() else connect(saved)
                }
                onboarding = false
            }
            SideEffect { WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
            } }
            KeepRideScreenOn(ui,background.keepScreenOn)
            IonTheme(dark, themeSettings) {
                BackHandler(onboarding) { finishOnboarding(false) }
                if (onboarding) ConnectionOnboarding(
                    bluetoothAllowed, notificationsAllowed, locationAllowed, batteryUnrestricted,
                    requestBluetooth = { bluetoothAskedAt = SystemClock.elapsedRealtime(); permission.launch(model.ble.permissions()) },
                    requestNotifications = { if (Build.VERSION.SDK_INT >= 33) {
                        notificationsAskedAt = SystemClock.elapsedRealtime(); askNotifications()
                    } else openNotificationSettings() },
                    requestLocation = { locationAskedAt = SystemClock.elapsedRealtime()
                        location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    requestBattery = ::requestBatteryUnrestricted,
                    locationServicesEnabled = !companionWheel.supported || locationServicesAllowed,
                    requestLocationServices = { runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
                    addWheel = { finishOnboarding(true) }, later = { finishOnboarding(false) },
                ) else IonAppContent({ui.value},dark,PrefsUi(dashboard,alerts,background,trips=trips,
                    themeFollowsSystem=preference==null,theme=themeSettings,update=updateUi,sounds=soundLabels,lightDesigns=lightDesigns,lightLast=lightLast),Actions(scan={permitted(null)},connect={permitted(it)},
                    disconnect=model::disconnect,stopScan=model.ble::stopScan,forget=model::forget,
                    readAll=model.ble::readAll,read=model.ble::read,write=model.ble::apply,password=model.ble::setPassword,
                    theme={model.setDark(!dark)},followSystemTheme={model.setDark(if(it) null else dark)},
                    themeMode={ model.setDark(when (it) { ThemeMode.SYSTEM -> null; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }) },
                    dynamicColor=model.prefs::setDynamicColor,palette=model.prefs::setPalette,permissions={onboarding=true},
                    setDashboard=model.prefs::setDashboard,setSoundLabels=model.prefs::setSoundLabels,
                    lights=LightUi(setDesigns=model.prefs::setLightDesigns,upload={ d -> model.ble.uploadLight(d, model.prefs.lightDesigns.value) },
                        list=model.ble::listLights,clearTransfer=model.ble::clearLightTransfer,delete=model.ble::deleteLights,setLast=model.prefs::setLightLast),setAlerts=model.prefs::setAlerts,setBackground={ config ->
                        model.prefs.setBackground(config)
                        if ((config.gpsTrack && !background.gpsTrack && !locationAllowed) ||
                            (config.liveNotification && !background.liveNotification && !notificationsAllowed)) onboarding = true
                    },
                    resetTrip=model::resetTrip,shareCsv={exportTrip(it,false)},shareGpx={exportTrip(it,true)},
                    deleteTrip=model.history::delete,shareAllCsv=::exportAllTrips,
                    importTrips={ backupPicker.launch(arrayOf("application/zip","application/x-zip-compressed","application/octet-stream")) },
                    tripTrack=model.history::track,tripHasTelemetry=model.history::hasTelemetry,
                    tripTimeline={ id -> model.journal.csv(id).takeIf { it.isFile }?.useLines { TripTimeline.parse(it) }.orEmpty() },update=updateActions),
                    initialTab=if (startOnWheel) Tabs.WHEEL else Tabs.RIDE)
                // One silent check per launch, never on top of the first-run setup.
                LaunchedEffect(onboarding) { if (!onboarding) updater.checkOnLaunch() }
                if (!onboarding) UpdateDialog(updateUi, updateActions)
                if (pendingPair != null && !pairingDialogLaunched) AlertDialog(
                    onDismissRequest = {}, title = { Text(stringResource(R.string.bg_companion_pairing)) },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { finishPair(true) }) {
                        Text(stringResource(R.string.setup_connect_without_pairing))
                    } },
                )
            }
        }
    }
    private fun openAppSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }
    private fun showAssociationError() {
        android.widget.Toast.makeText(this, R.string.bg_companion_failed, android.widget.Toast.LENGTH_LONG).show()
    }
    @Composable private fun KeepRideScreenOn(ui: State<VelaUi>, enabled: Boolean) {
        val keepOn=enabled && ui.value.connected
        DisposableEffect(keepOn) {
            if(keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { }
        }
    }
    private fun openNotificationSettings() {
        runCatching {startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
            .onFailure {runCatching {startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}}
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
        catch(_: SecurityException) {
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
                model.journal.awaitWrites()
                val file = withContext(Dispatchers.IO) {
                    val directory = exportDirectory()
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(Date())
                    File(directory,"Ion_rides_${stamp}.zip").apply {
                        outputStream().use { TripArchive.write(it,trips,model.journal::csv,model.history::track) }
                    }
                }
                startActivity(Intent.createChooser(TripExport.intent(this@MainActivity,file,"application/zip"),getString(R.string.trips_export_subject)))
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { android.widget.Toast.makeText(this@MainActivity,R.string.trip_export_failed,android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    /** Restores rides from an "All rides (ZIP)" archive; rides already in the log are skipped, never duplicated. */
    private fun importTrips(uri: Uri) {
        lifecycleScope.launch {
            val message = try {
                val added = withContext(Dispatchers.IO) {
                    val existing = model.history.trips.value.mapTo(HashSet()) { it.startedAt }
                    val rides = contentResolver.openInputStream(uri)?.use { input ->
                        TripArchive.read(input, existing, model.journal::csv, model.journal::importTrack)
                    } ?: throw java.io.IOException("unreadable")
                    rides
                }
                model.history.import(added)
                if (added.isEmpty()) getString(R.string.trips_import_none)
                else resources.getQuantityString(R.plurals.trips_imported, added.size, added.size)
            } catch (e: CancellationException) { throw e }
            catch (_: TripArchive.NotABackup) { getString(R.string.trips_import_wrong) }
            catch (_: Exception) { getString(R.string.trips_import_failed) }
            android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onStop() { super.onStop(); if(!isChangingConfigurations) model.onBackground() }
}
