@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import kotlin.math.roundToInt

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

/** Connection state as an assist-chip-like pill: icon or loading indicator plus a label. */
@Composable internal fun Status(ui: VelaUi) {
    val scheme=MaterialTheme.colorScheme
    val bg by animateColorAsState(if(ui.connected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        MaterialTheme.motionScheme.fastEffectsSpec(),label="connection-state")
    val ink=if(ui.connected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Surface(color=bg,contentColor=ink,shape=RoundedCornerShape(IonShapes.s)) {
        Row(Modifier.heightIn(min=32.dp).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(ui.busy) LoadingIndicator(Modifier.size(18.dp),color=ink)
            else Icon(if(ui.connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,null,Modifier.size(18.dp))
            val label=stringResource(if(ui.connected) R.string.status_live else if(ui.busy) R.string.status_busy else R.string.status_offline)
            AnimatedContent(label,label="connection-label") {Text(it,style=MaterialTheme.typography.labelLarge)}
        }
    }
}

/** A shape that follows a [Morph] between two Material shapes at [progress]. */
internal class MorphShape(private val morph: Morph,private val progress: Float): androidx.compose.ui.graphics.Shape {
    private val matrix=androidx.compose.ui.graphics.Matrix()
    override fun createOutline(size: androidx.compose.ui.geometry.Size,layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        matrix.reset(); matrix.scale(size.width,size.height)
        val path=morph.toPath(progress).asComposePath()
        path.transform(matrix)
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** Leading icon of a setting row: a cookie badge that morphs into a filled primary circle while the setting is on. */
@Composable internal fun SettingIcon(icon: ImageVector,active: Boolean) {
    val scheme=MaterialTheme.colorScheme
    val morph=remember {Morph(MaterialShapes.Cookie9Sided,MaterialShapes.Circle)}
    val progress=animateFloatAsState(if(active) 1f else 0f,MaterialTheme.motionScheme.defaultSpatialSpec(),label="setting-shape")
    val bg by animateColorAsState(if(active) scheme.primary else scheme.secondaryContainer,MaterialTheme.motionScheme.fastEffectsSpec(),label="setting-badge")
    val fg by animateColorAsState(if(active) scheme.onPrimary else scheme.onSecondaryContainer,MaterialTheme.motionScheme.fastEffectsSpec(),label="setting-icon")
    Box(Modifier.size(40.dp).clip(MorphShape(morph,progress.value)).background(bg),contentAlignment=Alignment.Center) {
        Icon(icon,null,Modifier.size(20.dp),tint=fg)
    }
}

/** Material switch with check / close thumb icons, as in the system settings. */
@Composable internal fun IonSwitch(checked: Boolean,enabled: Boolean=true,onCheckedChange: ((Boolean)->Unit)?=null) {
    Switch(checked=checked,onCheckedChange=onCheckedChange,enabled=enabled,thumbContent={
        Icon(if(checked) Icons.Rounded.Check else Icons.Rounded.Close,null,Modifier.size(SwitchDefaults.IconSize))
    })
}

@Composable internal fun SwitchRow(icon: ImageVector,title: String,value: Boolean?,hint: String?=null,enabled: Boolean,change: (Boolean)->Unit) {
    val on=value==true
    val haptics=LocalHapticFeedback.current
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=rowShape()) {
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp).toggleable(value=on,enabled=enabled && value!=null,role=Role.Switch,onValueChange={
                haptics.performHapticFeedback(if(it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff);change(it)})
            .padding(start=16.dp,end=16.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            SettingIcon(icon,on && enabled)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,style=MaterialTheme.typography.titleMediumEmphasized,
                    color=if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=.38f))
                val supporting=hint ?: if(value==null) stringResource(R.string.no_data) else null
                supporting?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            IonSwitch(on,enabled && value!=null)
        }
    }
}

@Composable internal fun SettingSlider(label: String,value: Float,text: String,range: ClosedFloatingPointRange<Float>,enabled: Boolean,change: (Float)->Unit,
    modifier: Modifier=Modifier,sliderTag: String?=null) {
    Column(modifier.padding(top=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(label,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
            // The value sits in a small tonal pill so it reads as the slider's output, not as another label.
            Surface(color=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer,
                shape=RoundedCornerShape(IonShapes.s)) {
                Text(text,style=MaterialTheme.typography.labelLarge.copy(fontFeatureSettings="tnum"),
                    modifier=Modifier.padding(horizontal=10.dp,vertical=4.dp))
            }
        }
        Slider(value=value,onValueChange=change,enabled=enabled,valueRange=range,
            modifier=Modifier.fillMaxWidth().semantics {contentDescription=label;stateDescription=text}
                .then(if(sliderTag!=null) Modifier.testTag(sliderTag) else Modifier))
    }
}

@Composable internal fun ApplyButton(label: String,enabled: Boolean,click: ()->Unit,modifier: Modifier=Modifier) {
    Button(onClick=click,enabled=enabled,shapes=ButtonDefaults.shapes(),modifier=modifier.fillMaxWidth().heightIn(min=48.dp)) {
        Icon(Icons.Rounded.Check,null,Modifier.size(ButtonDefaults.IconSize));Spacer(Modifier.width(ButtonDefaults.IconSpacing));Text(label)
    }
}

@Composable internal fun MissingSetting(text: String) {
    Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
}

/** Same adaptive pair as the ride tiles, without a nested subcomposition (see [TilePair]). */
@Composable internal fun MetricPair(first: @Composable (Modifier)->Unit,second: @Composable (Modifier)->Unit)=TilePair(first,second)

@Composable internal fun ReadAllButton(ui: VelaUi,actions: Actions,modifier: Modifier) {
    FilledTonalButton(onClick=actions.readAll,enabled=ui.connected && !ui.wheel.workPending,shapes=ButtonDefaults.shapes(),modifier=modifier.heightIn(min=48.dp)) {
        if(ui.wheel.workPending) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Rounded.Refresh,null,Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing));Text(stringResource(if(ui.wheel.workPending) R.string.read_pending else R.string.read_all))
    }
}

/** One slider inside an [ExpandSliders] editor: the wheel's current value plus how to show it. */
internal class SliderSpec(val label: String,value: Int,val range: IntRange,val text: @Composable (Int)->String) {
    // An odd wheel reading (e.g. standby 0 min) must not ride along with Apply outside the slider's range.
    val value: Int=value.coerceIn(range)
}

/**
 * Expandable slider editor whose drafts survive settings read-backs. A slider the rider never touched
 * follows the wheel value; a touched one keeps the user's value even when a read-back or the periodic
 * settings poll arrives mid-edit. Apply sends the drafts and keeps them until a read-back confirms the
 * sent values, so a slow link can no longer throw an edit away or dead-lock the button. This replaces
 * the old `remember(s.field)` pattern whose drafts were clobbered by every settings update — the cause
 * of the "applies only once until the row is reopened" bug.
 */
@Composable internal fun ExpandSliders(icon: ImageVector,title: String,summary: String,enabled: Boolean,
    specs: List<SliderSpec>?,onApply: (List<Int>)->Unit) {
    ExpandRow(icon,title,summary) {
        if(specs.isNullOrEmpty()) { MissingSetting(stringResource(R.string.no_data)); return@ExpandRow }
        var drafts by remember { mutableStateOf(List(specs.size) { null as Float? }) }
        val sent=remember { mutableStateOf<List<Int>?>(null) }
        val current=specs.mapIndexed {index,spec -> (drafts[index] ?: spec.value.toFloat()).roundToInt()}
        // Read-back confirmed the sent values: stop showing them as drafts, follow the wheel again.
        LaunchedEffect(specs.map { it.value }) {
            if(sent.value!=null && specs.map { it.value }==sent.value) { sent.value=null; drafts=List(specs.size) { null } }
        }
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            specs.forEachIndexed {index,spec ->
                val value=drafts[index] ?: spec.value.toFloat()
                SettingSlider(spec.label,value,spec.text(current[index]),
                    spec.range.first.toFloat()..spec.range.last.toFloat(),enabled,
                    sliderTag="slider-$index",change={new -> drafts=drafts.toMutableList().also {it[index]=new}})
            }
            val haptics=LocalHapticFeedback.current
            ApplyButton(stringResource(R.string.common_apply),enabled && drafts.any {it!=null},
                modifier=Modifier.padding(top=4.dp).testTag("apply"),click={haptics.performHapticFeedback(HapticFeedbackType.Confirm);onApply(current); sent.value=current})
        }
    }
}

/** A list row that expands in place; the chevron turns and gains a tonal circle while open. */
@Composable internal fun ExpandRow(icon: ImageVector,title: String,summary: String,content: @Composable ColumnScope.()->Unit) {
    var open by rememberSaveable(title) {mutableStateOf(false)}
    val state=stringResource(if(open) R.string.expanded else R.string.collapsed)
    val rotation=animateFloatAsState(if(open) 180f else 0f,MaterialTheme.motionScheme.fastSpatialSpec(),label="expand-arrow")
    val scheme=MaterialTheme.colorScheme
    val bg by animateColorAsState(if(open) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        MaterialTheme.motionScheme.fastEffectsSpec(),label="expanded-row")
    val chip by animateColorAsState(if(open) scheme.secondaryContainer else Color.Transparent,MaterialTheme.motionScheme.fastEffectsSpec(),label="expand-chip")
    Surface(color=bg,shape=rowShape()) {
        Column(Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(Modifier.fillMaxWidth().heightIn(min=72.dp).semantics {stateDescription=state}.clickable(role=Role.Button) {open=!open}
                .padding(start=16.dp,end=16.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                SettingIcon(icon,open)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(title,style=MaterialTheme.typography.titleMediumEmphasized)
                    // Before settings arrive the summary is a dash; TalkBack says "no data" instead.
                    val noData=stringResource(R.string.no_data)
                    Text(summary,style=MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings="tnum"),color=scheme.onSurfaceVariant,
                        modifier=if(summary=="—") Modifier.semantics {contentDescription=noData} else Modifier)
                }
                Box(Modifier.size(32.dp).clip(CircleShape).background(chip),contentAlignment=Alignment.Center) {
                    Icon(Icons.Rounded.ExpandMore,null,Modifier.size(24.dp).graphicsLayer {rotationZ=rotation.value},
                        tint=if(open) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                }
            }
            if(open) Column(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        }
    }
}

/**
 * Sound preview: tap plays the slot on the wheel, long press names or hides it. What a slot sounds like depends on
 * the wheel's sound pack, so the names are the rider's own (see [SoundLabels]); hidden slots stay one tap away.
 */
@Composable internal fun SoundButtons(enabled: Boolean,labels: SoundLabels,setLabels: (SoundLabels)->Unit,play: (Int)->Unit) {
    var editing by rememberSaveable {mutableStateOf<Int?>(null)}
    var showHidden by rememberSaveable {mutableStateOf(false)}
    val haptics=LocalHapticFeedback.current
    @Composable fun label(id: Int)=labels.name(id) ?: if(id==SoundLabels.HORN) stringResource(R.string.sound_horn) else stringResource(R.string.sound_number,id)
    // Collapsed by default: a test tool for finding what each slot plays, not an everyday control.
    ExpandRow(Icons.Rounded.GraphicEq,stringResource(R.string.sound_preview),stringResource(R.string.sound_preview_summary)) {
        Text(stringResource(R.string.sound_preview_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            V12Protocol.soundIds.filter {showHidden || it !in labels.hidden}.forEach {id ->
                val hidden=id in labels.hidden
                val text=label(id)
                val rename=stringResource(R.string.sound_rename)
                // Chip geometry: 8 dp corners, 40 dp tall inside a 48 dp target.
                Surface(color=if(hidden) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor=if(hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
                    shape=RoundedCornerShape(IonShapes.s),modifier=Modifier.alpha(if(enabled) 1f else .38f)) {
                    Row(Modifier.heightIn(min=40.dp)
                        .combinedClickable(enabled=enabled,role=Role.Button,onLongClickLabel=rename,
                            onLongClick={haptics.performHapticFeedback(HapticFeedbackType.LongPress);editing=id}) {play(id)}
                        .padding(start=8.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(if(hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.PlayArrow,null,Modifier.size(18.dp))
                        Text(text,style=MaterialTheme.typography.labelLarge,maxLines=1)
                    }
                }
            }
        }
        if(labels.hidden.isNotEmpty()) TextButton(onClick={showHidden=!showHidden},modifier=Modifier.heightIn(min=48.dp)) {
            Text(if(showHidden) stringResource(R.string.sound_hide_hidden) else stringResource(R.string.sound_show_hidden,labels.hidden.size))
        }
    }
    editing?.let {id ->
        var name by rememberSaveable(id) {mutableStateOf(labels.name(id).orEmpty())}
        var hide by rememberSaveable(id) {mutableStateOf(id in labels.hidden)}
        AlertDialog(onDismissRequest={editing=null},icon={Icon(Icons.Rounded.GraphicEq,null)},
            title={Text(stringResource(R.string.sound_number,id))},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name,{name=it.take(SoundLabels.MAX_NAME)},singleLine=true,
                        label={Text(stringResource(R.string.sound_name))},modifier=Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(hide,role=Role.Checkbox) {hide=it},
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Checkbox(hide,null);Text(stringResource(R.string.sound_hide))
                    }
                    TextButton(onClick={play(id)},enabled=enabled) {
                        Icon(Icons.Rounded.PlayArrow,null,Modifier.size(ButtonDefaults.IconSize));Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.sound_play_again))
                    }
                }
            },
            confirmButton={TextButton(onClick={setLabels(labels.rename(id,name).hide(id,hide));editing=null}) {Text(stringResource(R.string.save))}},
            dismissButton={TextButton(onClick={editing=null}) {Text(stringResource(R.string.cancel))}})
    }
}

/** A lazy item that fades in and out and slides into place when neighbours appear, e.g. a banner above it. */
internal fun androidx.compose.foundation.lazy.LazyListScope.animatedItem(key: Any,contentType: Any?=null,
    content: @Composable androidx.compose.foundation.lazy.LazyItemScope.()->Unit) =
    item(key=key,contentType=contentType) {Box(Modifier.fillMaxWidth().animateItem()) {content()}}
