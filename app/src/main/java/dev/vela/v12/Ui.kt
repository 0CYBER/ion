package dev.vela.v12

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tactile press: squash to 0.94 with a spring and a light tick, like the design references. */
@Composable internal fun Modifier.squashClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) 0.94f else 1f, spring(dampingRatio=0.6f, stiffness=500f), label="squash")
    val haptics = LocalHapticFeedback.current
    return this.graphicsLayer { scaleX=scale; scaleY=scale }
        .clickable(interactionSource=interaction, indication=ripple(), enabled=enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
        }
}

@Composable internal fun Heading(title: String, subtitle: String) {
    Column(Modifier.padding(bottom=8.dp)) {
        Text(title,fontSize=30.sp,fontWeight=FontWeight.Medium,modifier=Modifier.semantics { heading() })
        Text(subtitle,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
    }
}

/** Section header with the small accent bar used by the design references. */
@Composable internal fun SectionHeader(title: String) {
    Row(Modifier.padding(top=10.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(width=5.dp,height=20.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.semantics { heading() })
    }
}

@Composable internal fun Status(ui: VelaUi) {
    val live=ui.connected
    val ion=ionColors()
    val color by animateColorAsState(if(live) ion.accent else MaterialTheme.colorScheme.secondaryContainer,tween(300),label="linkColor")
    Surface(color=color,contentColor=if(live) ion.onAccent else MaterialTheme.colorScheme.onSecondaryContainer,shape=RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            if(ui.busy) CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=2.dp)
            else Icon(if(live) Icons.Rounded.CheckCircle else Icons.Rounded.BluetoothDisabled,null,Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            AnimatedContent(if(live) "На связи" else if(ui.busy) "Проверка" else "Офлайн",label="status") { Text(it,fontSize=14.sp,fontWeight=FontWeight.Medium) }
        }
    }
}

@Composable internal fun Banner(text: String, icon: ImageVector = Icons.Rounded.Info, error: Boolean = false, warning: Boolean = false) {
    val ion=ionColors()
    Surface(shape=RoundedCornerShape(IonShapes.l),color=when { error -> MaterialTheme.colorScheme.errorContainer
        warning -> ion.warning; else -> MaterialTheme.colorScheme.surfaceContainerHigh },
        contentColor=when { error -> MaterialTheme.colorScheme.onErrorContainer
            warning -> ion.onWarning; else -> MaterialTheme.colorScheme.onSurfaceVariant }) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,Modifier.size(21.dp)); Spacer(Modifier.width(12.dp)); Text(text,fontSize=14.sp,modifier=Modifier.weight(1f))
        }
    }
}

/** Orion-style grouped list: near-flush rows whose corners morph 28 on the outside, 4 on the inner seams. */
@Composable internal fun GroupColumn(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp),content=content)
}
@Composable internal fun GroupRow(index: Int, count: Int, onClick: (() -> Unit)? = null, content: @Composable RowScope.()->Unit) {
    val shape=RoundedCornerShape(
        topStart=if(index==0) IonShapes.xl else 4.dp, topEnd=if(index==0) IonShapes.xl else 4.dp,
        bottomStart=if(index==count-1) IonShapes.xl else 4.dp, bottomEnd=if(index==count-1) IonShapes.xl else 4.dp)
    val modifier=if(onClick!=null) Modifier.fillMaxWidth().squashClick(onClick=onClick) else Modifier.fillMaxWidth()
    Surface(modifier=modifier,shape=shape,color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}

/** Switch row with border + background morph (Lumen pattern). value==null renders a disabled row. */
@Composable internal fun SwitchRow(icon: ImageVector,title: String,value: Boolean?,hint: String? = null,
    enabled: Boolean,change: (Boolean)->Unit) {
    val on=value==true
    val ion=ionColors()
    val bg by animateColorAsState(if(on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        spring(stiffness=400f),label="rowBg")
    val border by animateColorAsState(if(on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant,
        spring(stiffness=400f),label="rowBorder")
    Surface(color=bg,shape=RoundedCornerShape(IonShapes.row),border=BorderStroke(1.dp,border)) {
        Row(Modifier.fillMaxWidth().heightIn(min=58.dp).padding(start=14.dp,end=14.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(if(on) IonShapes.s else IonShapes.m))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center) {
                Icon(icon,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title,fontSize=16.sp)
                if(hint!=null) Text(hint,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                else if(value==null) Text("Нет данных",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked=on,onCheckedChange=change,enabled=enabled && value!=null)
        }
    }
}

/** Switch for controls the wheel never reports back: optimistic local state, honestly labelled. */
@Composable internal fun BlindSwitchRow(icon: ImageVector,title: String,hint: String,enabled: Boolean,change: (Boolean)->Unit) {
    var on by rememberSaveable(title) { mutableStateOf(false) }
    SwitchRow(icon,title,if(on) true else false,hint,enabled) { on=it;change(it) }
}

@Composable internal fun SettingSlider(label: String,value: Float,text: String,range: ClosedFloatingPointRange<Float>,enabled: Boolean,change: (Float)->Unit) {
    Column(Modifier.padding(top=4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,fontSize=14.sp,modifier=Modifier.weight(1f))
            Text(text,fontSize=16.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace)
        }
        Slider(value=value,onValueChange=change,enabled=enabled,valueRange=range,modifier=Modifier.fillMaxWidth())
    }
}

@Composable internal fun ApplyButton(label: String,enabled: Boolean,click: ()->Unit) {
    FilledTonalButton(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(label,fontSize=14.sp)}
}

@Composable internal fun MissingSetting(text: String) {
    Text(text,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
}

@Composable internal fun Metric(label: String,value: String,unit: String,icon: ImageVector,modifier: Modifier,accent: Boolean=false) {
    val ion=ionColors()
    Surface(modifier,color=if(accent) ion.accent else MaterialTheme.colorScheme.surfaceContainer,
        contentColor=if(accent) ion.onAccent else MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(IonShapes.xl)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {Text(label,fontSize=14.sp,modifier=Modifier.weight(1f));Icon(icon,null,Modifier.size(20.dp))}
            Row(Modifier.padding(top=18.dp),verticalAlignment=Alignment.Bottom) {
                Text(value,fontSize=34.sp,fontWeight=FontWeight.ExtraBold,maxLines=1,letterSpacing=(-1).sp)
                Text(unit,fontSize=16.sp,modifier=Modifier.padding(start=5.dp,bottom=4.dp))
            }
        }
    }
}

@Composable internal fun MetricPair(first: @Composable (Modifier)->Unit, second: @Composable (Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<300.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.2f)
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {first(Modifier.fillMaxWidth());second(Modifier.fillMaxWidth())}
        else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {first(Modifier.weight(1f));second(Modifier.weight(1f))}
    }
}

@Composable internal fun ReadAllButton(ui: VelaUi,actions: Actions,modifier: Modifier) {
    FilledTonalButton(onClick=actions.readAll,enabled=ui.connected && !ui.wheel.workPending,modifier=modifier.heightIn(min=48.dp)) {
        if(ui.wheel.workPending) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Icon(Icons.Rounded.Refresh,null,Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp)); Text(if(ui.wheel.workPending) "Читаю…" else "Прочитать всё",fontSize=14.sp)
    }
}
