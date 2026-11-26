@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The rider's own wheel first (connect, forget, diagnostics), then finding a wheel nearby. */
@Composable internal fun WheelScreen(ui: VelaUi,actions: Actions,select: (WheelDevice)->Unit,forget: ()->Unit,diagnostics: ()->Unit) {
    val scanning=ui.wheel.phase==Phase.SCANNING
    IonPage(stringResource(R.string.nav_wheel),actions={
        IconButton(onClick=actions.scan,enabled=!ui.busy && !ui.connected && !scanning,shapes=IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Add,stringResource(R.string.search_start))
        }
    }) {
        item(key="status",contentType="status") {Box(Modifier.padding(start=4.dp)) {Status(ui)}}
        if(ui.wheel.phase==Phase.ERROR) item(key="error",contentType="banner") {Box(Modifier.animateItem()) {Banner(ui.wheel.message,Icons.Rounded.ErrorOutline,error=true)}}
        ui.saved?.let {device ->
            item(key="saved",contentType="saved-wheel") {SavedWheelCard(ui,device,actions,forget)}
        }
        item(key="diagnostics",contentType="link") {
            ActionRow(stringResource(R.string.diagnostics_title),stringResource(R.string.diagnostics_hint),Icons.Rounded.DataObject,
                BadgeTone.TERTIARY,MaterialShapes.Square,diagnostics)
        }
        item(key="discovery-title",contentType="section") {SectionHeader(stringResource(R.string.wheel_discovery))}
        item(key="discovery",contentType="discovery") {DiscoveryCard(ui,scanning,actions)}
        if(ui.wheel.devices.isEmpty()) item(key="empty",contentType="empty") {
            Text(text=stringResource(R.string.wheel_search_empty),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.animateItem().padding(horizontal=16.dp,vertical=4.dp))
        }
        if(ui.wheel.devices.isNotEmpty()) item(key="devices",contentType="devices") {
            // Wheels found during a scan join one segmented group; the group grows smoothly as they arrive.
            GroupColumn(Modifier.animateItem().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                ui.wheel.devices.forEach {device ->
                    ListRow(device.name,supporting=stringResource(R.string.wheel_signal,device.address.takeLast(8),device.rssi),
                        leading={SignalBadge(device.rssi)},
                        trailing={Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)},
                        enabled=!ui.busy && !ui.connected,onClick={select(device)})
                }
            }
        }
        item(key="permissions",contentType="action") {
            TextButton(onClick=actions.permissions,shapes=ButtonDefaults.shapes(),modifier=Modifier.heightIn(min=48.dp)) {
                Icon(Icons.Rounded.Shield,null,Modifier.size(ButtonDefaults.IconSize));Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.bluetooth_permissions))
            }
        }
    }
}

/** The saved wheel: its mark turns slowly while connected, the main action is connect / disconnect. */
@Composable private fun SavedWheelCard(ui: VelaUi,device: WheelDevice,actions: Actions,forget: ()->Unit) {
    val scheme=MaterialTheme.colorScheme
    val bg by animateColorAsState(if(ui.connected) scheme.primaryContainer else scheme.surfaceContainer,MaterialTheme.motionScheme.defaultEffectsSpec(),label="wheel-card")
    val fg=if(ui.connected) scheme.onPrimaryContainer else scheme.onSurface
    Surface(color=bg,contentColor=fg,shape=IonShapes.card) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                WheelMark(ui.connected,ui.busy)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.wheel_saved),style=MaterialTheme.typography.labelLarge,color=fg.copy(alpha=.74f))
                    Text(device.name,style=MaterialTheme.typography.titleLargeEmphasized)
                    Text(stringResource(R.string.wheel_address,device.address.takeLast(8)),style=MaterialTheme.typography.bodyMedium,color=fg.copy(alpha=.74f))
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Button(onClick={if(ui.connected) actions.disconnect() else actions.connect(device)},enabled=!ui.busy,shapes=ButtonDefaults.shapes(),
                    modifier=Modifier.weight(1f).heightIn(min=56.dp),contentPadding=ButtonDefaults.MediumContentPadding) {
                    Icon(if(ui.connected) Icons.Rounded.LinkOff else Icons.Rounded.BluetoothConnected,null,Modifier.size(ButtonDefaults.MediumIconSize))
                    Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                    Text(stringResource(if(ui.connected) R.string.disconnect else R.string.connect),style=MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick=forget,enabled=!ui.busy,shapes=ButtonDefaults.shapes(),modifier=Modifier.heightIn(min=56.dp),
                    contentPadding=ButtonDefaults.MediumContentPadding) {Text(stringResource(R.string.forget_wheel),style=MaterialTheme.typography.titleMedium)}
            }
        }
    }
}

@Composable private fun WheelMark(connected: Boolean,busy: Boolean) {
    val scheme=MaterialTheme.colorScheme
    val turn=if(connected || busy) rememberInfiniteTransition(label="wheel-mark").animateFloat(0f,360f,
        infiniteRepeatable(tween(if(busy) 4_000 else 24_000,easing=LinearEasing)),label="wheel-turn") else null
    Box(Modifier.size(64.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer {rotationZ=turn?.value ?: 0f}.clip(MaterialShapes.Cookie12Sided.toShape())
            .background(if(connected) scheme.primary else scheme.secondaryContainer))
        Icon(Icons.Rounded.TripOrigin,null,Modifier.size(30.dp),tint=if(connected) scheme.onPrimary else scheme.onSecondaryContainer)
    }
}

/** Finding a wheel: a short explanation, the live scanner message and one start / stop action. */
@Composable private fun DiscoveryCard(ui: VelaUi,scanning: Boolean,actions: Actions) {
    val scheme=MaterialTheme.colorScheme
    Surface(color=scheme.surfaceContainerLow,shape=IonShapes.card) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                if(ui.busy || scanning) LoadingIndicator(Modifier.size(48.dp))
                else ShapeBadge(Icons.Rounded.Radar,size=48.dp,polygon=MaterialShapes.Clover4Leaf,
                    containerColor=scheme.tertiaryContainer,contentColor=scheme.onTertiaryContainer)
                Text(stringResource(if(ui.saved==null) R.string.wheel_discovery_title else R.string.wheel_discovery_other),
                    style=MaterialTheme.typography.titleLargeEmphasized,modifier=Modifier.weight(1f))
            }
            Text(stringResource(R.string.wheel_discovery_hint),style=MaterialTheme.typography.bodyMedium,color=scheme.onSurfaceVariant)
            Text(ui.wheel.message,style=MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick=if(scanning) actions.stopScan else actions.scan,enabled=!ui.busy,shapes=ButtonDefaults.shapes(),
                modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                Icon(if(scanning) Icons.Rounded.Stop else Icons.Rounded.Search,null,Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing));Text(stringResource(if(scanning) R.string.search_stop else R.string.search_start))
            }
            if(ui.connected && ui.saved==null) TextButton(onClick=actions.disconnect) {Text(stringResource(R.string.disconnect))}
        }
    }
}

/** Signal strength at a glance: which of several nearby wheels is the rider's own is usually the closest one. */
@Composable private fun SignalBadge(rssi: Int) {
    val (icon,label,strong)=when {
        rssi>=-65 -> Triple(Icons.Rounded.SignalCellularAlt,R.string.signal_strong,true)
        rssi>=-80 -> Triple(Icons.Rounded.SignalCellularAlt2Bar,R.string.signal_medium,false)
        else -> Triple(Icons.Rounded.SignalCellularAlt1Bar,R.string.signal_weak,false)
    }
    val scheme=MaterialTheme.colorScheme
    ShapeBadge(icon,size=40.dp,polygon=if(strong) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
        containerColor=if(strong) scheme.primaryContainer else scheme.surfaceContainerHighest,
        contentColor=if(strong) scheme.onPrimaryContainer else scheme.onSurfaceVariant,contentDescription=stringResource(label))
}
