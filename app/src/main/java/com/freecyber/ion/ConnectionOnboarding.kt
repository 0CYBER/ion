package com.freecyber.ion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectionOnboarding(
    bluetoothAllowed: Boolean,
    notificationsAllowed: Boolean,
    locationAllowed: Boolean,
    batteryUnrestricted: Boolean,
    requestBluetooth: () -> Unit,
    requestNotifications: () -> Unit,
    requestLocation: () -> Unit,
    requestBattery: () -> Unit,
    addWheel: () -> Unit,
    later: () -> Unit,
    locationServicesEnabled: Boolean = true,
    requestLocationServices: () -> Unit = {},
) {
    var skipBattery by rememberSaveable { mutableStateOf(false) }
    var skipNotifications by rememberSaveable { mutableStateOf(false) }
    var skipLocation by rememberSaveable { mutableStateOf(false) }
    var skipLocationServices by rememberSaveable { mutableStateOf(false) }
    val batteryReady = batteryUnrestricted || skipBattery
    val locationServicesReady = locationServicesEnabled || skipLocationServices
    val notificationsReady = notificationsAllowed || skipNotifications
    val locationReady = locationAllowed || skipLocation
    val step = when {
        !bluetoothAllowed -> SetupStep(R.string.setup_bluetooth_title, R.string.setup_bluetooth_hint,
            R.string.setup_bluetooth, Icons.Rounded.Bluetooth, requestBluetooth, later)
        !batteryReady -> SetupStep(R.string.setup_battery_title, R.string.battery_unrestricted_hint,
            R.string.battery_unrestricted, Icons.Rounded.BatterySaver, requestBattery) { skipBattery = true }
        !locationServicesReady -> SetupStep(R.string.setup_location_services_title, R.string.setup_location_services_hint,
            R.string.setup_location_services, Icons.Rounded.LocationOn, requestLocationServices) { skipLocationServices = true }
        !notificationsReady -> SetupStep(R.string.setup_notifications_title, R.string.notif_permission_hint,
            R.string.notif_permission, Icons.Rounded.Notifications, requestNotifications) { skipNotifications = true }
        !locationReady -> SetupStep(R.string.setup_route_title, R.string.location_permission_hint,
            R.string.location_permission, Icons.Rounded.LocationOn, requestLocation) { skipLocation = true }
        else -> SetupStep(R.string.setup_wheel_title, R.string.setup_connection_hint,
            R.string.setup_add_wheel, Icons.Rounded.Link, addWheel, later)
    }
    val stepsLeft = listOf(bluetoothAllowed, batteryReady, locationServicesReady, notificationsReady, locationReady)
        .count { !it }
    val stepNumber = (6 - stepsLeft).coerceIn(1, 6)
    // The bar glides to the next step instead of jumping when a permission is granted.
    val progress by androidx.compose.animation.core.animateFloatAsState((6 - stepsLeft) / 6f,
        MaterialTheme.motionScheme.slowSpatialSpec(), label = "setup-progress")
    val motion = MaterialTheme.motionScheme
    val colors = ionColors()
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 520.dp).fillMaxSize(), contentPadding = PagePadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Surface(shape = IonShapes.hero, color = colors.hero, contentColor = colors.onHero) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(stringResource(R.string.setup_connection), style = MaterialTheme.typography.headlineLarge)
                            Text(stringResource(R.string.setup_step, stepNumber, 6), style = MaterialTheme.typography.labelLarge,
                                color = colors.onHero.copy(alpha = 0.8f))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                                color = colors.accent, trackColor = colors.onHero.copy(alpha = 0.19f))
                        }
                    }
                }
                item {
                    // Each granted step slides away and the next one comes in, so the change is noticed.
                    androidx.compose.animation.AnimatedContent(step, contentKey = { it.title }, transitionSpec = {
                        (androidx.compose.animation.fadeIn(motion.defaultEffectsSpec()) +
                            androidx.compose.animation.slideInHorizontally(motion.defaultSpatialSpec()) { it / 8 }) togetherWith
                            androidx.compose.animation.fadeOut(motion.fastEffectsSpec())
                    }, label = "setup-step") { shown -> PermissionStep(shown) }
                }
            }
        }
    }
}

private data class SetupStep(
    val title: Int, val hint: Int, val label: Int, val icon: ImageVector,
    val request: () -> Unit, val skip: () -> Unit,
)

@Composable
private fun PermissionStep(step: SetupStep) {
    val colors = ionColors()
    Surface(shape = RoundedCornerShape(IonShapes.xl), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(IonShapes.m), color = colors.lilac.container, contentColor = colors.lilac.content) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(step.icon, contentDescription = null) }
                }
                Text(stringResource(step.title), style = MaterialTheme.typography.titleLarge)
            }
            Text(stringResource(step.hint), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = step.request, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(step.label)) }
                TextButton(onClick = step.skip) { Text(stringResource(R.string.setup_later)) }
            }
        }
    }
}
