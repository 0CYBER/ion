@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion.orion

import com.freecyber.ion.*
import com.freecyber.ion.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable internal fun WheelScreen(ui: VelaUi,actions: Actions,select: (WheelDevice)->Unit,forget: ()->Unit,diagnostics: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item(key="intro",contentType="intro") {ScreenHeading(stringResource(R.string.nav_wheel),stringResource(R.string.search_start),Icons.Rounded.Add,actions.scan,
            enabled=!ui.busy && !ui.connected && ui.wheel.phase!=Phase.SCANNING)}
        item(key="status",contentType="status") {Status(ui)}
        if(ui.wheel.phase==Phase.ERROR) item(key="error",contentType="banner") {Banner(ui.wheel.message,error=true)}
        ui.saved?.let {device ->
            item(key="saved-title",contentType="section") {SectionHeader(stringResource(R.string.wheel_saved))}
            item(key="saved",contentType="saved-wheel") {
                Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(64.dp).clip(MaterialShapes.Cookie4Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                                Icon(Icons.Rounded.TripOrigin,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(device.name,style=MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.wheel_address,device.address.takeLast(8)),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(onClick={if(ui.connected) actions.disconnect() else actions.connect(device)},enabled=!ui.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                            Icon(if(ui.connected) Icons.Rounded.LinkOff else Icons.Rounded.BluetoothConnected,null,Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp));Text(stringResource(if(ui.connected) R.string.disconnect else R.string.connect))
                        }
                        TextButton(onClick=forget,enabled=!ui.busy,modifier=Modifier.align(Alignment.CenterHorizontally)) {Text(stringResource(R.string.forget_wheel))}
                    }
                }
            }
        }
        item(key="diagnostics",contentType="link") {ActionRow(stringResource(R.string.diagnostics_title),stringResource(R.string.diagnostics_hint),Icons.Rounded.DataObject,diagnostics)}
        item(key="discovery-title",contentType="section") {SectionHeader(stringResource(R.string.wheel_discovery))}
        item(key="discovery",contentType="discovery") {
            Surface(color=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer,shape=IonShapes.discovery) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(56.dp).clip(MaterialShapes.Clover4Leaf.toShape()).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                        Icon(Icons.Rounded.Radar,null,Modifier.size(30.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(stringResource(if(ui.saved==null) R.string.wheel_discovery_title else R.string.wheel_discovery_other),style=MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.wheel_discovery_hint),style=MaterialTheme.typography.bodyLarge)
                    Text(ui.wheel.message,style=MaterialTheme.typography.bodyMedium)
                    if(ui.busy || ui.wheel.phase==Phase.SCANNING) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    FilledTonalButton(onClick=if(ui.wheel.phase==Phase.SCANNING) actions.stopScan else actions.scan,enabled=!ui.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                        Icon(if(ui.wheel.phase==Phase.SCANNING) Icons.Rounded.Stop else Icons.Rounded.Search,null,Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp));Text(stringResource(if(ui.wheel.phase==Phase.SCANNING) R.string.search_stop else R.string.search_start))
                    }
                    if(ui.connected && ui.saved==null) TextButton(onClick=actions.disconnect) {Text(stringResource(R.string.disconnect))}
                }
            }
        }
        if(ui.wheel.devices.isEmpty()) item(key="empty",contentType="empty") {
            Text(text=stringResource(R.string.wheel_search_empty),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.animateItem().padding(horizontal=4.dp,vertical=8.dp))
        }
        items(ui.wheel.devices,key={it.address},contentType={"device"}) {device ->
            // Wheels found during a scan slide in instead of popping into the list.
            Surface(onClick={select(device)},enabled=!ui.busy && !ui.connected,shape=RoundedCornerShape(IonShapes.l),
                color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.animateItem()) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SignalBadge(device.rssi)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(device.name,style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.wheel_signal,device.address.takeLast(8),device.rssi),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight,null)
                }
            }
        }
        item(key="permissions",contentType="action") {TextButton(onClick=actions.permissions) {Text(stringResource(R.string.bluetooth_permissions))}}
    }
}

/** Signal strength at a glance: which of several nearby wheels is the rider's own is usually the closest one. */
@Composable private fun SignalBadge(rssi: Int) {
    val (icon,label,strong)=when {
        rssi>=-65 -> Triple(Icons.Rounded.SignalCellularAlt,R.string.signal_strong,true)
        rssi>=-80 -> Triple(Icons.Rounded.SignalCellularAlt2Bar,R.string.signal_medium,false)
        else -> Triple(Icons.Rounded.SignalCellularAlt1Bar,R.string.signal_weak,false)
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(IonShapes.m))
        .background(if(strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center) {
        Icon(icon,stringResource(label),Modifier.size(22.dp),
            tint=if(strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
    }
}
