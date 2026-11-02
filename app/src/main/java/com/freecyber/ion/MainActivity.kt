package com.freecyber.ion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val model: VelaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            val preference by model.dark.collectAsStateWithLifecycle()
            val dark=systemDark(preference)
            var pendingDevice by remember { mutableStateOf<WheelDevice?>(null) }
            val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if(result.values.all { it } && model.ble.hasPermissions()) {
                    pendingDevice?.let(model::connect) ?: model.scan()
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
            IonTheme(dark) {
                IonApp(ui,dark,Actions(scan={permitted(null)},connect={permitted(it)},
                    disconnect=model::disconnect,stopScan=model.ble::stopScan,forget=model::forget,
                    readAll=model.ble::readAll,read=model.ble::read,write=model.ble::apply,
                    theme={model.setDark(!dark)},permissions={startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}))
            }
        }
    }
    override fun onStop() { super.onStop(); if(!isChangingConfigurations) model.ble.onBackground() }
}
