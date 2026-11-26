@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                // Where the rider is in the setup: the Expressive wavy indicator and "Step 2 of 6".
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.setup_connection), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.setup_step, stepNumber, 6), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                // Each granted step slides away and the next one comes in, so the change is noticed.
                androidx.compose.animation.AnimatedContent(step, contentKey = { it.title }, modifier = Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
                    (androidx.compose.animation.fadeIn(motion.defaultEffectsSpec()) +
                        androidx.compose.animation.slideInHorizontally(motion.defaultSpatialSpec()) { it / 8 }) togetherWith
                        androidx.compose.animation.fadeOut(motion.fastEffectsSpec())
                }, label = "setup-step") { shown -> PermissionStep(shown, stepNumber) }
            }
        }
    }
}

private data class SetupStep(
    val title: Int, val hint: Int, val label: Int, val icon: ImageVector,
    val request: () -> Unit, val skip: () -> Unit,
)

/** Every step has its own Material shape; the mark slowly turns, so the page feels alive while the rider reads. */
private val StepShapes get() = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst)

@Composable
private fun PermissionStep(step: SetupStep, number: Int) {
    val scheme = MaterialTheme.colorScheme
    val turn = androidx.compose.animation.core.rememberInfiniteTransition(label = "setup-mark").animateFloat(0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(40_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "setup-turn")
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(144.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().graphicsLayer { rotationZ = turn.value }
                .clip(StepShapes[(number - 1).coerceIn(0, 5)].toShape()).background(scheme.primaryContainer))
            Icon(step.icon, contentDescription = null, Modifier.size(56.dp), tint = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(step.title), style = MaterialTheme.typography.headlineMediumEmphasized)
        Text(stringResource(step.hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = step.request, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                contentPadding = ButtonDefaults.MediumContentPadding) {
                Text(stringResource(step.label), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = step.skip, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.setup_later))
            }
        }
    }
}
