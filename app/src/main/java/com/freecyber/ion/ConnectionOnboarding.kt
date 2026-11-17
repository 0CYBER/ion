package com.freecyber.ion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PagePadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.setup_connection), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.setup_connection_hint))
            }
            if (!bluetoothAllowed) item {
                ApplyButton(stringResource(R.string.setup_bluetooth), true, requestBluetooth, Modifier.fillMaxWidth())
            }
            if (!notificationsAllowed) item {
                Text(stringResource(R.string.notif_permission_hint))
                TextButton(onClick = requestNotifications) { Text(stringResource(R.string.notif_permission)) }
            }
            if (!locationAllowed) item {
                Text(stringResource(R.string.location_permission_hint))
                TextButton(onClick = requestLocation) { Text(stringResource(R.string.location_permission)) }
            }
            if (!batteryUnrestricted) item {
                Text(stringResource(R.string.battery_unrestricted_hint))
                TextButton(onClick = requestBattery) { Text(stringResource(R.string.battery_unrestricted)) }
            }
            item {
                ApplyButton(stringResource(R.string.setup_add_wheel), bluetoothAllowed, addWheel, Modifier.fillMaxWidth())
                TextButton(onClick = later) { Text(stringResource(R.string.setup_later)) }
            }
        }
    }
}
