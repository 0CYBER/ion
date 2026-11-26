@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.freecyber.ion

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Personal light effects made in Ion and the callbacks that store and send them. */
internal data class LightUi(
    val designs: List<LightDesign> = emptyList(),
    val setDesigns: (List<LightDesign>) -> Unit = {},
    val upload: (LightDesign) -> Unit = {},
    val list: () -> Unit = {},
    val clearTransfer: () -> Unit = {},
    val delete: (List<LightEffects.Stored>) -> Unit = {},
    /** The effect last turned on (or uploaded), used when "personal" is picked as the mode. */
    val last: Long? = null,
    val setLast: (Long) -> Unit = {},
)

/**
 * Turns on a stored effect. Inside personal mode the vendor app's select (0x2d) is enough; from another mode the
 * wheel ignores it and needs the mode command with the effect's id.
 */
internal fun lightOn(mode: Int?, id: Long) = if (mode == V12Protocol.LightEffect.PERSONAL.id)
    V12Protocol.Write(V12Protocol.Control.LIGHT_PACKAGE, id.toInt())
    else V12Protocol.Write(V12Protocol.Control.LIGHT_EFFECT, V12Protocol.LightEffect.PERSONAL.id, id.toInt())

/** The name shown everywhere: the rider's, or "Effect N" for an unnamed one. */
@Composable private fun LightDesign.title() = name.ifBlank { stringResource(R.string.light_unnamed, slot) }

@Composable private fun templateLabel(t: LightDesign.Template) = stringResource(when (t) {
    LightDesign.Template.SOLID -> R.string.light_tpl_solid
    LightDesign.Template.BLINK -> R.string.light_tpl_blink
    LightDesign.Template.BREATHE -> R.string.light_tpl_breathe
    LightDesign.Template.GRADIENT -> R.string.light_tpl_gradient
    LightDesign.Template.CHASE -> R.string.light_tpl_chase
    LightDesign.Template.RAINBOW -> R.string.light_tpl_rainbow
    LightDesign.Template.HALVES -> R.string.light_tpl_halves
    LightDesign.Template.CUSTOM -> R.string.light_tpl_custom
})

private val LightDesign.Template.usesSecond get() = this in setOf(LightDesign.Template.BLINK, LightDesign.Template.GRADIENT,
    LightDesign.Template.CHASE, LightDesign.Template.HALVES)
private val LightDesign.Template.usesFirst get() = this != LightDesign.Template.RAINBOW

/**
 * Side lights, laid out like the other settings pages: the four modes as the wheel reports them (settings byte 23),
 * effects made in Ion (upload and turn on in one go) and the effects stored on the wheel.
 */
@Composable internal fun LightEffectsSection(ui: VelaUi, safe: Boolean, lights: LightUi, request: (V12Protocol.Write) -> Unit) {
    val wheel = ui.wheel
    var editing by remember { mutableStateOf<LightDesign?>(null) }
    var deleting by remember { mutableStateOf<LightDesign?>(null) }
    var deletingStored by remember { mutableStateOf<LightEffects.Stored?>(null) }
    val transfer = wheel.lightTransfer
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        transfer?.let { TransferCard(it, lights.designs, lights.clearTransfer) }
        SectionHeader(stringResource(R.string.light_mine))
        GroupColumn {
            lights.designs.forEach { d ->
                DesignRow(d, onWheel = d.wheelId != null && wheel.storedLights?.any { it.id == d.wheelId && !it.temporary } == true,
                    canSend = safe && transfer?.done != false, edit = { editing = d }) { lights.upload(d) }
            }
            LightDesign.freeSlot(lights.designs)?.let { slot ->
                ActionRow(Icons.Rounded.Add, stringResource(R.string.light_new), null, Modifier.testTag("light-new")) {
                    editing = LightDesign(slot, "", LightDesign.Template.CUSTOM, custom = listOf(List(LightEffects.LEDS) { 0 }))
                }
            }
        }
        SectionHeader(stringResource(R.string.light_on_wheel))
        StoredEffects(wheel.storedLights, lights.designs, safe, refresh = lights.list,
            clean = { lights.delete(it) }, delete = { deletingStored = it }) { id ->
            lights.setLast(id); request(lightOn(wheel.settings?.lightEffect, id))
        }
    }
    editing?.let { d ->
        val existing = lights.designs.any { it.slot == d.slot }
        LightEditor(d, isNew = !existing, onDismiss = { editing = null },
            onDelete = if (existing) ({ editing = null; deleting = d }) else null) { saved ->
            lights.setDesigns((lights.designs.filterNot { it.slot == saved.slot } + saved).sortedBy { it.slot }); editing = null
        }
    }
    deleting?.let { d ->
        // Its copy on the wheel can go too, when there is one and the wheel is reachable.
        val copy = wheel.storedLights?.firstOrNull { it.id == d.wheelId && !it.temporary }
        AlertDialog(onDismissRequest = { deleting = null }, icon = { Icon(Icons.Rounded.DeleteOutline, null) }, title = { Text(stringResource(R.string.light_delete_title, d.title())) },
            text = { Text(stringResource(if (copy != null) R.string.light_delete_text_wheel else R.string.light_delete_text)) },
            confirmButton = {
                Row {
                    if (copy != null) TextButton(onClick = { lights.setDesigns(lights.designs.filterNot { it.slot == d.slot }); lights.delete(listOf(copy)); deleting = null },
                        enabled = safe) { Text(stringResource(R.string.light_delete_everywhere)) }
                    TextButton(onClick = { lights.setDesigns(lights.designs.filterNot { it.slot == d.slot }); deleting = null }) {
                        Text(stringResource(if (copy != null) R.string.light_delete_ion_only else R.string.delete))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
    deletingStored?.let { e ->
        val title = storedTitle(e, lights.designs)
        AlertDialog(onDismissRequest = { deletingStored = null }, title = { Text(stringResource(R.string.light_delete_wheel_title, title)) },
            text = { Text(stringResource(R.string.light_delete_wheel_text)) },
            confirmButton = { TextButton(onClick = { lights.delete(listOf(e)); deletingStored = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deletingStored = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

/** Whether the personal-effect settings are shown: the wheel runs a personal effect, or the rider picked that mode. */
internal fun lightPersonal(mode: Int?, picked: Boolean) = picked || mode == V12Protocol.LightEffect.PERSONAL.id

/**
 * Side-light mode as an expandable row of the lights group. Off, music and speed are sent at once; "personal" needs
 * an effect, so picking it only opens the effect settings below, where an effect is turned on.
 */
@Composable internal fun LightModeRow(ui: VelaUi, safe: Boolean, lights: LightUi, personalPicked: Boolean, pickPersonal: (Boolean) -> Unit,
    request: (V12Protocol.Write) -> Unit) {
    val mode = ui.wheel.settings?.lightEffect
    val personal = lightPersonal(mode, personalPicked)
    val options = listOf(V12Protocol.LightEffect.OFF to R.string.control_light_effect_off,
        V12Protocol.LightEffect.MUSIC to R.string.control_light_effect_music,
        V12Protocol.LightEffect.SPEED to R.string.control_light_effect_speed,
        V12Protocol.LightEffect.PERSONAL to R.string.control_light_effect_personal)
    val selected = if (personal) V12Protocol.LightEffect.PERSONAL else options.firstOrNull { it.first.id == mode }?.first
    val summary = options.firstOrNull { it.first == selected }?.let { stringResource(it.second) } ?: "—"
    ExpandRow(Icons.Rounded.AutoAwesome, stringResource(R.string.control_light_effect), summary) {
        if (mode == null) { MissingSetting(stringResource(R.string.no_data)); return@ExpandRow }
        Column(Modifier.selectableGroup()) {
            options.forEach { (effect, label) ->
                val on = effect == selected
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(IonShapes.l))
                    .selectable(on, enabled = safe, role = Role.RadioButton) {
                        if (effect == V12Protocol.LightEffect.PERSONAL) {
                            pickPersonal(true)
                            // The last effect turned on, else the first one on the wheel; with none, the list below is the way in.
                            val effects = ui.wheel.storedLights?.filterNot { it.temporary }.orEmpty()
                            val id = effects.firstOrNull { it.id == lights.last }?.id ?: effects.firstOrNull()?.id
                            if (id != null && mode != V12Protocol.LightEffect.PERSONAL.id) request(lightOn(mode, id))
                        }
                        else { pickPersonal(false); request(V12Protocol.Write(V12Protocol.Control.LIGHT_EFFECT, effect.id)) }
                    }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = on, onClick = null, enabled = safe)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Keeps the effect list and each design's wheel id current, whether or not the effect settings are on screen. */
@Composable internal fun LightSync(ui: VelaUi, lights: LightUi) {
    val wheel = ui.wheel
    // The stored list is read once per screen visit; it is a few hundred bytes.
    LaunchedEffect(ui.connected) { if (ui.connected && wheel.storedLights == null) lights.list() }
    // A finished upload records the id it got, so the design knows which copy on the wheel is its own.
    val transfer = wheel.lightTransfer
    LaunchedEffect(transfer?.done, transfer?.id) {
        val id = transfer?.id
        if (transfer?.done == true && transfer.error == null && id != null)
            lights.designs.firstOrNull { it.slot == transfer.slot && it.wheelId != id }?.let { d ->
                lights.setDesigns(lights.designs.map { if (it.slot == d.slot) it.copy(wheelId = id) else it })
            }
        if (transfer?.done == true && transfer.error == null && id != null) lights.setLast(id)
    }
}

/** A plain row that does one thing when tapped, in the settings style. */
@Composable private fun ActionRow(icon: ImageVector, title: String, hint: String?, modifier: Modifier = Modifier, enabled: Boolean = true, click: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = rowShape()) {
        Row(modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = enabled, role = Role.Button, onClick = click)
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingIcon(icon, false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable private fun TransferCard(t: LightTransfer, designs: List<LightDesign>, clear: () -> Unit) {
    val name = designs.firstOrNull { it.slot == t.slot }?.title() ?: t.id.toString()
    val error = t.error != null
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(IonShapes.li)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(when { error -> Icons.Rounded.ErrorOutline; t.done -> Icons.Rounded.CheckCircle; else -> Icons.Rounded.Upload }, null, Modifier.size(22.dp))
                Text(when {
                    error -> stringResource(R.string.light_upload_failed, name, t.error!!)
                    t.done -> stringResource(R.string.light_upload_done, name)
                    else -> stringResource(R.string.light_uploading, name)
                }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (t.done) TextButton(onClick = clear, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.light_ok)) }
            }
            if (!t.done) LinearWavyProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun DesignRow(d: LightDesign, onWheel: Boolean, canSend: Boolean, edit: () -> Unit, send: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = rowShape()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(role = Role.Button, onClickLabel = stringResource(R.string.light_edit), onClick = edit)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(IonShapes.l)) {
                PlayingWheel(d, Modifier.padding(6.dp).height(60.dp).aspectRatio(LedLayout.WIDTH))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.title(), style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val frames = d.frames().size
                Text(listOfNotNull(templateLabel(d.template), pluralStringResource(R.plurals.light_frames, frames, frames),
                    stringResource(R.string.light_interval_value, (d.intervalMs / 1000.0).fmt(2))).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onWheel) Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(IonShapes.s), modifier = Modifier.padding(top = 2.dp)) {
                    Text(stringResource(R.string.light_on_wheel_badge), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            FilledTonalIconButton(onClick = send, enabled = canSend, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                Icon(if (onWheel) Icons.Rounded.Refresh else Icons.Rounded.Upload, stringResource(R.string.light_send))
            }
        }
    }
}

/** The wheel's LEDs as the vendor editor draws them: two side bars and a ring open at the top and bottom. */
@Composable internal fun WheelLeds(frame: List<Int>, modifier: Modifier = Modifier, tracks: Boolean = true, highlight: Int? = null) {
    val off = MaterialTheme.colorScheme.outlineVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val mark = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val scale = minOf(size.height, size.width / LedLayout.WIDTH)
        val left = (size.width - LedLayout.WIDTH * scale) / 2; val top = (size.height - scale) / 2
        fun at(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
        val dot = LedLayout.DOT * scale
        if (tracks) drawTracks(::at, scale, track)
        LedLayout.spots.forEachIndexed { i, s ->
            val rgb = frame.getOrElse(i) { 0 }
            drawCircle(if (rgb == 0) off else Color(0xFF000000.toInt() or rgb), dot, at(s.x, s.y))
            if (i == highlight) drawCircle(mark, dot * 1.45f, at(s.x, s.y), style = Stroke(dot * 0.3f))
        }
    }
}

private fun DrawScope.drawTracks(at: (Float, Float) -> Offset, scale: Float, color: Color) {
    val width = LedLayout.DOT * scale * 2.9f
    val bars = listOf(LedLayout.spots.filter { it.segment == LedLayout.Segment.LEFT_BAR }, LedLayout.spots.filter { it.segment == LedLayout.Segment.RIGHT_BAR })
    bars.forEach { bar ->
        val a = at(bar.first().x, bar.first().y); val b = at(bar.last().x, bar.last().y)
        drawLine(color, a, b, width, StrokeCap.Round)
    }
    val c = at(LedLayout.CX, LedLayout.CY); val r = LedLayout.R * scale
    val sweep = (180 - LedLayout.TOP_GAP - LedLayout.BOTTOM_GAP).toFloat()
    // Compose angles: 0° at three o'clock, clockwise; the top is −90°.
    listOf(-90f + LedLayout.TOP_GAP.toFloat(), -90f - LedLayout.TOP_GAP.toFloat() - sweep).forEach { start ->
        drawArc(color, start, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(width, cap = StrokeCap.Round))
    }
}

/** [WheelLeds] playing the effect at its own interval. */
@Composable internal fun PlayingWheel(d: LightDesign, modifier: Modifier = Modifier) {
    val frames = remember(d) { d.frames() }
    var frame by remember(d) { mutableIntStateOf(0) }
    LaunchedEffect(d) { while (frames.size > 1) { delay(d.intervalMs.toLong()); frame = (frame + 1) % frames.size } }
    val description = stringResource(R.string.light_preview)
    WheelLeds(frames[frame.coerceIn(0, frames.lastIndex)].toList(), modifier.semantics { contentDescription = description }, tracks = false)
}

@Composable private fun storedTitle(e: LightEffects.Stored, designs: List<LightDesign>): String =
    designs.firstOrNull { it.wheelId == e.id }?.title() ?: if (LightDesign.isIonId(e.id)) stringResource(R.string.light_ion_effect, e.id)
        else stringResource(R.string.light_vendor_effect, e.id)

/** What the wheel holds: each effect can be turned on or deleted; unfinished uploads are offered for clean-up. */
@Composable private fun StoredEffects(stored: List<LightEffects.Stored>?, designs: List<LightDesign>, safe: Boolean, refresh: () -> Unit,
    clean: (List<LightEffects.Stored>) -> Unit, delete: (LightEffects.Stored) -> Unit, turnOn: (Long) -> Unit) {
    val effects = stored?.filterNot { it.temporary }
    val leftovers = stored?.filter { it.temporary }.orEmpty()
    GroupColumn {
        effects?.forEach { e ->
            val title = storedTitle(e, designs)
            val ion = LightDesign.isIonId(e.id)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = rowShape()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Ion's own uploads are previewed from the design that made them; the wheel's files are not read back.
                    val source = designs.firstOrNull { it.wheelId == e.id }
                    if (source != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(IonShapes.s)) {
                        PlayingWheel(source, Modifier.padding(4.dp).height(40.dp).aspectRatio(LedLayout.WIDTH))
                    } else SettingIcon(if (ion) Icons.Rounded.Palette else Icons.Rounded.Lightbulb, false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(if (ion) R.string.light_from_ion else R.string.light_from_vendor), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { turnOn(e.id) }, enabled = safe, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.light_turn_on_named, title))
                    }
                    IconButton(onClick = { delete(e) }, enabled = safe, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.light_delete_wheel_title, title))
                    }
                }
            }
        }
        if (stored == null || effects.isNullOrEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = rowShape()) {
            Text(stringResource(if (stored == null) R.string.light_on_wheel_unknown else R.string.light_on_wheel_empty),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp))
        }
        if (leftovers.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = rowShape()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(22.dp))
                Text(pluralStringResource(R.plurals.light_leftovers, leftovers.size, leftovers.size), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { clean(leftovers) }, enabled = safe, modifier = Modifier.heightIn(min = 48.dp).testTag("light-clean")) {
                    Text(stringResource(R.string.light_clean))
                }
            }
        }
        ActionRow(Icons.Rounded.Refresh, stringResource(R.string.light_refresh), stringResource(R.string.light_on_wheel_hint), enabled = safe, click = refresh)
    }
}

/**
 * Full-screen editor: the wheel's LEDs where they sit, painted by tapping or dragging; frames below, played back on
 * the same picture. Templates fill the frames as a starting point; everything is undoable.
 */
@Composable internal fun LightEditor(start: LightDesign, isNew: Boolean, onDismiss: () -> Unit, onDelete: (() -> Unit)?, save: (LightDesign) -> Unit) {
    val first = remember(start) { if (start.template == LightDesign.Template.CUSTOM) start else start.painted() }
    var name by remember(start) { mutableStateOf(start.name) }
    var interval by remember(start) { mutableIntStateOf(start.intervalMs) }
    var frames by remember(start) { mutableStateOf(first.custom.orEmpty().ifEmpty { listOf(List(LightEffects.LEDS) { 0 }) }) }
    val history = remember(start) { mutableStateListOf<List<List<Int>>>() }
    var current by remember(start) { mutableIntStateOf(0) }
    var brush by remember(start) { mutableIntStateOf(LightDesign.PALETTE.first()) }
    var playing by remember(start) { mutableStateOf(false) }
    var shown by remember(start) { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf<LightDesign.Template?>(null) }
    fun change(new: List<List<Int>>) { if (new != frames) { history += frames; if (history.size > 50) history.removeAt(0); frames = new } }
    LaunchedEffect(playing, interval, frames.size) {
        shown = current
        while (playing && frames.size > 1) { delay(interval.toLong()); shown = (shown + 1) % frames.size }
    }
    val result = start.copy(name = name.trim(), template = LightDesign.Template.CUSTOM, custom = frames, intervalMs = interval)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { Text(stringResource(if (isNew) R.string.light_new else R.string.light_effect), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) } },
                    actions = {
                        onDelete?.let { IconButton(onClick = it) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) } }
                        Button(onClick = { save(result) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(end = 8.dp).testTag("light-save")) { Text(stringResource(R.string.save)) }
                    })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(name, { name = it.take(LightDesign.MAX_NAME) }, singleLine = true,
                        label = { Text(stringResource(R.string.light_name)) }, modifier = Modifier.fillMaxWidth())
                    Paintable(frames[if (playing) shown.coerceIn(0, frames.lastIndex) else current], !playing,
                        Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.88f).aspectRatio(LedLayout.WIDTH),
                        strokeStart = { history += frames; if (history.size > 50) history.removeAt(0) }) { led ->
                        val f = frames[current]
                        if (f[led] != brush) frames = frames.toMutableList().also { it[current] = f.toMutableList().also { p -> p[led] = brush } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledIconToggleButton(checked = playing, onCheckedChange = { playing = it }, enabled = frames.size > 1, shapes = IconButtonDefaults.toggleableShapes(),
                            modifier = Modifier.size(56.dp)) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.light_pause else R.string.light_play))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { history.removeLastOrNull()?.let { frames = it; current = current.coerceAtMost(it.lastIndex) } },
                            enabled = history.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.light_undo)) }
                        IconButton(onClick = { change(frames.toMutableList().also { it[current] = List(LightEffects.LEDS) { brush } }) }, enabled = !playing) {
                            Icon(Icons.Rounded.FormatColorFill, stringResource(R.string.light_fill))
                        }
                        IconButton(onClick = { change(frames.toMutableList().also { it[current] = List(LightEffects.LEDS) { 0 } }) }, enabled = !playing) {
                            Icon(Icons.Rounded.FormatColorReset, stringResource(R.string.light_clear))
                        }
                    }
                    Brushes(brush, { brush = it }) { picking = true }
                    FrameStrip(frames, current, enabled = !playing, select = { current = it },
                        add = {
                            if (frames.size < LightEffects.MAX_FRAMES) {
                                change(frames.toMutableList().also { it.add(current + 1, frames[current]) }); current++
                            }
                        },
                        remove = {
                            if (frames.size > 1) { change(frames.toMutableList().also { it.removeAt(current) }); current = current.coerceAtMost(frames.lastIndex) }
                        })
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingSlider(stringResource(R.string.light_interval), interval.toFloat(), stringResource(R.string.light_seconds, (interval / 1000.0).fmt(2)),
                            LightEffects.INTERVAL_MS.first.toFloat()..LightEffects.INTERVAL_MS.last.toFloat(), true,
                            { interval = ((it / 50).roundToInt() * 50).coerceIn(LightEffects.INTERVAL_MS) })
                    }
                    Text(stringResource(R.string.light_start_template), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LightDesign.Template.generators.forEach { t ->
                            AssistChip(onClick = { template = t }, label = { Text(templateLabel(t)) }, shape = RoundedCornerShape(IonShapes.s),
                                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(AssistChipDefaults.IconSize)) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
            }
        }
    }
    if (picking) CustomColorDialog(brush, onDismiss = { picking = false }) { brush = it; picking = false }
    template?.let { t ->
        TemplateDialog(start.copy(template = t, intervalMs = interval, custom = null), onDismiss = { template = null }) { generated ->
            change(generated.frames().map { it.toList() }); current = 0; playing = false; template = null
        }
    }
}

/** The editable picture: a touch paints the LED under it; a drag paints every LED it crosses. */
@Composable private fun Paintable(frame: List<Int>, enabled: Boolean, modifier: Modifier, strokeStart: () -> Unit, paint: (Int) -> Unit) {
    val description = stringResource(R.string.light_wheel_canvas)
    val latestPaint by rememberUpdatedState(paint)
    val latestStart by rememberUpdatedState(strokeStart)
    Box(modifier.semantics { contentDescription = description }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                fun led(p: Offset): Int? {
                    val scale = minOf(size.height.toFloat(), size.width / LedLayout.WIDTH)
                    val left = (size.width - LedLayout.WIDTH * scale) / 2; val top = (size.height - scale) / 2
                    return LedLayout.at((p.x - left) / scale, (p.y - top) / scale)
                }
                latestStart()
                led(down.position)?.let(latestPaint)
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    led(change.position)?.let(latestPaint)
                    change.consume()
                }
            }
        }) {
        WheelLeds(frame, Modifier.fillMaxSize())
    }
}

/** The palette plus the rider's own colour; black switches an LED off. */
@Composable private fun Brushes(selected: Int, pick: (Int) -> Unit, custom: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface; val edge = MaterialTheme.colorScheme.outlineVariant
    val off = stringResource(R.string.light_off_color)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = LightDesign.PALETTE + listOfNotNull(selected.takeIf { it !in LightDesign.PALETTE })
        colors.forEach { rgb ->
            val on = rgb == selected
            Box(Modifier.size(44.dp).clip(CircleShape).border(if (on) 3.dp else 1.dp, if (on) ring else edge, CircleShape)
                .selectable(on, role = Role.RadioButton) { pick(rgb) }.padding(5.dp).clip(CircleShape)
                .semantics { contentDescription = if (rgb == 0) off else "#%06X".format(rgb) }) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(if (rgb == 0) Color(0xFF202020) else Color(0xFF000000.toInt() or rgb))
                    if (rgb == 0) drawLine(edge, Offset(size.width * .2f, size.height * .8f), Offset(size.width * .8f, size.height * .2f), 2.dp.toPx())
                }
            }
        }
        FilledTonalIconButton(onClick = custom, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Colorize, stringResource(R.string.light_custom_color)) }
    }
}

@Composable private fun FrameStrip(frames: List<List<Int>>, current: Int, enabled: Boolean, select: (Int) -> Unit, add: () -> Unit, remove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.light_frame_n, current + 1, frames.size), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
            IconButton(onClick = remove, enabled = enabled && frames.size > 1, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.light_delete_frame)) }
            FilledTonalButton(onClick = add, enabled = enabled && frames.size < LightEffects.MAX_FRAMES, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.light_add_frame))
            }
        }
        val state = rememberLazyListState()
        LaunchedEffect(current) { state.animateScrollToItem(current) }
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(frames) { i, f ->
                val on = i == current
                Surface(color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(IonShapes.l),
                    modifier = Modifier.border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(IonShapes.l))
                        .selectable(on, enabled = enabled, role = Role.Tab) { select(i) }) {
                    Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        WheelLeds(f, Modifier.height(56.dp).aspectRatio(LedLayout.WIDTH), tracks = false)
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** A touch or drag over the area, reported as 0..1 fractions of its width and height. */
private fun Modifier.dragFraction(update: (Float, Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        fun report(p: Offset) = update((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
        report(down.position); down.consume()
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            report(change.position); change.consume()
        }
    }
}

/** A saturation/brightness square over a hue strip, with the hex code; for colours outside the palette. */
@Composable private fun CustomColorDialog(start: Int, onDismiss: () -> Unit, pick: (Int) -> Unit) {
    var picked by remember { mutableIntStateOf(start) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.light_custom_color)) },
        text = { ColorPicker(start) { picked = it } },
        confirmButton = { TextButton(onClick = { pick(picked) }) { Text(stringResource(R.string.light_use_color)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun ColorPicker(start: Int, change: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(0xFF000000.toInt() or start, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0] / 360f) }
    var saturation by remember { mutableFloatStateOf(if (start == 0) 1f else hsv[1]) }
    var value by remember { mutableFloatStateOf(if (start == 0) 1f else hsv[2]) }
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, saturation, value)) and 0xFFFFFF
    // What the rider is typing; dragging goes back to showing the picked colour.
    var draft by remember { mutableStateOf<String?>(null) }
    val hex = draft ?: "%06X".format(rgb)
    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, 1f, 1f)))
    val hues = remember { List(7) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) } }
    val description = stringResource(R.string.light_custom_color)
    LaunchedEffect(rgb) { change(rgb) }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Canvas(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(IonShapes.m)).semantics { contentDescription = description }
                    .dragFraction { x, y -> saturation = x; value = 1 - y; draft = null }) {
                    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.White, pure)))
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val at = Offset(saturation * size.width, (1 - value) * size.height)
                    drawCircle(Color.Black, 12.dp.toPx(), at, style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, 12.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                }
                Canvas(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(50)).dragFraction { x, _ -> hue = x.coerceAtMost(0.999f); draft = null }) {
                    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(hues))
                    val at = Offset(hue * size.width, size.height / 2)
                    drawCircle(pure, size.height / 2 - 3.dp.toPx(), at)
                    drawCircle(Color.White, size.height / 2 - 3.dp.toPx(), at, style = Stroke(3.dp.toPx()))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape)) { Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xFF000000.toInt() or rgb)) } }
                    OutlinedTextField(hex, { text ->
                        val typed = text.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        draft = typed
                        if (typed.length == 6) typed.toIntOrNull(16)?.let { c ->
                            val f = FloatArray(3); android.graphics.Color.colorToHSV(0xFF000000.toInt() or c, f)
                            hue = (f[0] / 360f).coerceAtMost(0.999f); saturation = f[1]; value = f[2]
                        }
                    }, singleLine = true, prefix = { Text("#") }, label = { Text("HEX") }, modifier = Modifier.weight(1f))
                }
            }
}

/** A template with its colours, previewed; applying it replaces every frame (undoable). */
@Composable private fun TemplateDialog(start: LightDesign, onDismiss: () -> Unit, apply: (LightDesign) -> Unit) {
    var d by remember(start) { mutableStateOf(start) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(templateLabel(d.template)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayingWheel(d, Modifier.fillMaxWidth().height(160.dp))
                if (d.template.usesFirst) Swatches(stringResource(R.string.light_color1), d.color1) { d = d.copy(color1 = it) }
                if (d.template.usesSecond) Swatches(stringResource(if (d.template == LightDesign.Template.CHASE) R.string.light_color_bg
                    else R.string.light_color2), d.color2) { d = d.copy(color2 = it) }
                Text(stringResource(R.string.light_template_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { apply(d) }, modifier = Modifier.testTag("light-template-apply")) { Text(stringResource(R.string.light_template_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun Swatches(label: String, selected: Int, pick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val ring = MaterialTheme.colorScheme.onSurface; val edge = MaterialTheme.colorScheme.outlineVariant
            LightDesign.PALETTE.forEach { rgb ->
                val on = rgb == selected
                Box(Modifier.size(36.dp).clip(CircleShape).border(if (on) 3.dp else 1.dp, if (on) ring else edge, CircleShape)
                    .selectable(on, role = Role.RadioButton) { pick(rgb) }.padding(4.dp).clip(CircleShape)
                    .semantics { contentDescription = "#%06X".format(rgb) }) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(if (rgb == 0) Color(0xFF202020) else Color(0xFF000000.toInt() or rgb)) }
                }
            }
        }
    }
}
