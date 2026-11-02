@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable internal fun Modifier.squashClick(enabled: Boolean=true,onClick: ()->Unit): Modifier {
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val scale=animateFloatAsState(if(pressed) .97f else 1f,MaterialTheme.motionScheme.fastSpatialSpec(),label="press")
    val haptics=LocalHapticFeedback.current
    return graphicsLayer {scaleX=scale.value;scaleY=scale.value}
        .clickable(interactionSource=interaction,indication=ripple(),enabled=enabled,role=Role.Button) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);onClick()
        }
}

@Composable internal fun SectionHeader(title: String) {
    Text(title,style=MaterialTheme.typography.titleLarge,
        modifier=Modifier.padding(start=4.dp,top=12.dp,bottom=4.dp).semantics {heading()})
}
@Composable internal fun Status(ui: VelaUi) {
    val ion=ionColors()
    val bg by animateColorAsState(if(ui.connected) ion.accent else MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.motionScheme.fastEffectsSpec(),label="connection-state")
    val ink=if(ui.connected) ion.onAccent else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color=bg,contentColor=ink,shape=RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(ui.busy) LoadingIndicator(Modifier.size(20.dp),color=ink)
            else Icon(if(ui.connected) Icons.Rounded.CheckCircle else Icons.Rounded.BluetoothDisabled,null,Modifier.size(18.dp))
            val label=stringResource(if(ui.connected) R.string.status_live else if(ui.busy) R.string.status_busy else R.string.status_offline)
            AnimatedContent(label,label="connection-label") {Text(it,style=MaterialTheme.typography.labelLarge)}
        }
    }
}
@Composable internal fun Banner(text: String,icon: ImageVector=Icons.Rounded.Info,error: Boolean=false,warning: Boolean=false) {
    val ion=ionColors()
    Surface(shape=RoundedCornerShape(IonShapes.l),color=when {error -> MaterialTheme.colorScheme.errorContainer
        warning -> ion.warning;else -> MaterialTheme.colorScheme.surfaceContainerHigh},
        contentColor=when {error -> MaterialTheme.colorScheme.onErrorContainer
            warning -> ion.onWarning;else -> MaterialTheme.colorScheme.onSurfaceVariant}) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(22.dp));Text(text,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
        }
    }
}
/** One outer outline, 2dp seams, no borders around individual settings. */
@Composable internal fun GroupColumn(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(IonShapes.xl)),verticalArrangement=Arrangement.spacedBy(2.dp),content=content)
}
@Composable private fun SettingIcon(icon: ImageVector,active: Boolean) {
    val radius by animateDpAsState(if(active) IonShapes.s else IonShapes.row,MaterialTheme.motionScheme.fastSpatialSpec(),label="setting-shape")
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center) {
        Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
    }
}
@Composable internal fun SwitchRow(icon: ImageVector,title: String,value: Boolean?,hint: String?=null,enabled: Boolean,change: (Boolean)->Unit) {
    val on=value==true
    val bg by animateColorAsState(if(on && enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.fastEffectsSpec(),label="setting-state")
    Surface(color=bg,shape=RoundedCornerShape(IonShapes.xs)) {
        Row(Modifier.fillMaxWidth().heightIn(min=76.dp).toggleable(value=on,enabled=enabled && value!=null,role=Role.Switch,onValueChange=change)
            .padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SettingIcon(icon,on && enabled)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                val supporting=hint ?: if(value==null) stringResource(R.string.no_data) else null
                supporting?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Switch(checked=on,onCheckedChange=null,enabled=enabled && value!=null)
        }
    }
}
@Composable internal fun SettingSlider(label: String,value: Float,text: String,range: ClosedFloatingPointRange<Float>,enabled: Boolean,change: (Float)->Unit) {
    Column(Modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(label,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
            Text(text,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,fontFamily=IonMono)
        }
        Slider(value=value,onValueChange=change,enabled=enabled,valueRange=range,modifier=Modifier.fillMaxWidth())
    }
}
@Composable internal fun ApplyButton(label: String,enabled: Boolean,click: ()->Unit) {
    FilledTonalButton(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(label)}
}
@Composable internal fun MissingSetting(text: String) {
    Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
}
@Composable internal fun MetricPair(first: @Composable (Modifier)->Unit,second: @Composable (Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<300.dp || LocalDensity.current.fontScale>1.2f)
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {first(Modifier.fillMaxWidth());second(Modifier.fillMaxWidth())}
        else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {first(Modifier.weight(1f));second(Modifier.weight(1f))}
    }
}
@Composable internal fun ReadAllButton(ui: VelaUi,actions: Actions,modifier: Modifier) {
    FilledTonalButton(onClick=actions.readAll,enabled=ui.connected && !ui.wheel.workPending,modifier=modifier.heightIn(min=48.dp)) {
        if(ui.wheel.workPending) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Rounded.Refresh,null,Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp));Text(stringResource(if(ui.wheel.workPending) R.string.read_pending else R.string.read_all))
    }
}
@Composable internal fun ExpandRow(icon: ImageVector,title: String,summary: String,content: @Composable ColumnScope.()->Unit) {
    var open by rememberSaveable(title) {mutableStateOf(false)}
    val state=stringResource(if(open) R.string.expanded else R.string.collapsed)
    val rotation=animateFloatAsState(if(open) 180f else 0f,MaterialTheme.motionScheme.fastSpatialSpec(),label="expand-arrow")
    val bg by animateColorAsState(if(open) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.fastEffectsSpec(),label="expanded-row")
    Surface(color=bg,shape=RoundedCornerShape(IonShapes.xs)) {
        Column(Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(Modifier.fillMaxWidth().heightIn(min=76.dp).semantics {stateDescription=state}.clickable(role=Role.Button) {open=!open}
                .padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                SettingIcon(icon,open)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,style=MaterialTheme.typography.titleMedium)
                    Text(summary,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,fontFamily=IonMono)
                }
                Icon(Icons.Rounded.ExpandMore,null,Modifier.size(24.dp).graphicsLayer {rotationZ=rotation.value})
            }
            if(open) Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        }
    }
}
@Composable internal fun SoundButtons(enabled: Boolean,play: (Int)->Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sound_preview),style=MaterialTheme.typography.titleMedium)
        MetricPair(first={m -> SoundPair(1,enabled,m,play)},second={m -> SoundPair(3,enabled,m,play)})
    }
}
@Composable private fun SoundPair(first: Int,enabled: Boolean,modifier: Modifier,play: (Int)->Unit) {
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        (first..first+1).forEach {number ->
            FilledTonalButton(onClick={play(number)},enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp),
                shape=RoundedCornerShape(topStart=if(number==first) 24.dp else 8.dp,topEnd=if(number==first) 8.dp else 24.dp,
                    bottomEnd=if(number==first) 8.dp else 24.dp,bottomStart=if(number==first) 24.dp else 8.dp),contentPadding=PaddingValues(8.dp)) {
                Text(stringResource(R.string.sound_number,number),style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}
