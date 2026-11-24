@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
)

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
})

private val LightDesign.Template.usesSecond get() = this in setOf(LightDesign.Template.BLINK, LightDesign.Template.GRADIENT,
    LightDesign.Template.CHASE, LightDesign.Template.HALVES)
private val LightDesign.Template.usesFirst get() = this != LightDesign.Template.RAINBOW

/**
 * Side lights: the four modes as the wheel reports them (settings byte 23), effects made in Ion (upload and turn on
 * in one go) and the effects stored on the wheel, which can be turned on by id.
 */
@Composable internal fun LightEffectsSection(ui: VelaUi, safe: Boolean, lights: LightUi, request: (V12Protocol.Write) -> Unit) {
    val wheel = ui.wheel
    val mode = wheel.settings?.lightEffect
    var editing by remember { mutableStateOf<LightDesign?>(null) }
    var deleting by remember { mutableStateOf<LightDesign?>(null) }
    var deletingStored by remember { mutableStateOf<LightEffects.Stored?>(null) }
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
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupColumn {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.control_light_effect), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(V12Protocol.LightEffect.OFF to R.string.control_light_effect_off,
                        V12Protocol.LightEffect.MUSIC to R.string.control_light_effect_music,
                        V12Protocol.LightEffect.SPEED to R.string.control_light_effect_speed,
                        V12Protocol.LightEffect.PERSONAL to R.string.control_light_effect_personal).forEach { (effect, label) ->
                        FilterChip(selected = mode == effect.id, enabled = safe && effect != V12Protocol.LightEffect.PERSONAL,
                            onClick = { request(V12Protocol.Write(V12Protocol.Control.LIGHT_EFFECT, effect.id)) },
                            label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text(stringResource(R.string.light_mode_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        wheel.lightTransfer?.let { TransferCard(it, lights.designs, lights.clearTransfer) }
        SectionHeader(stringResource(R.string.light_mine))
        if (lights.designs.isEmpty()) Text(stringResource(R.string.light_mine_empty), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        lights.designs.forEach { d ->
            DesignRow(d, active = d.wheelId != null && wheel.storedLights?.any { it.id == d.wheelId } == true,
                canSend = safe && wheel.lightTransfer?.done != false, edit = { editing = d }, delete = { deleting = d }) { lights.upload(d) }
        }
        LightDesign.freeSlot(lights.designs)?.let { slot ->
            FilledTonalButton(onClick = { editing = LightDesign(slot, "") }, modifier = Modifier.heightIn(min = 48.dp).testTag("light-new")) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.light_new))
            }
        }
        SectionHeader(stringResource(R.string.light_on_wheel))
        StoredEffects(wheel.storedLights, lights.designs, safe, refresh = lights.list,
            clean = { lights.delete(it) }, delete = { deletingStored = it }) { id ->
            request(V12Protocol.Write(V12Protocol.Control.LIGHT_PACKAGE, id.toInt()))
        }
    }
    editing?.let { d ->
        DesignEditor(d, onDismiss = { editing = null }) { saved ->
            lights.setDesigns((lights.designs.filterNot { it.slot == saved.slot } + saved).sortedBy { it.slot }); editing = null
        }
    }
    deleting?.let { d ->
        // Its copy on the wheel can go too, when there is one and the wheel is reachable.
        val copy = wheel.storedLights?.firstOrNull { it.id == d.wheelId && !it.temporary }
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.light_delete_title, d.title())) },
            text = { Text(stringResource(if (copy != null) R.string.light_delete_text_wheel else R.string.light_delete_text)) },
            confirmButton = {
                Row {
                    if (copy != null) TextButton(onClick = { lights.setDesigns(lights.designs - d); lights.delete(listOf(copy)); deleting = null },
                        enabled = safe) { Text(stringResource(R.string.light_delete_everywhere)) }
                    TextButton(onClick = { lights.setDesigns(lights.designs - d); deleting = null }) {
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

@Composable private fun TransferCard(t: LightTransfer, designs: List<LightDesign>, clear: () -> Unit) {
    val name = designs.firstOrNull { it.slot == t.slot }?.title() ?: t.id.toString()
    Surface(color = if (t.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(IonShapes.l)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when {
                t.error != null -> stringResource(R.string.light_upload_failed, name, t.error)
                t.done -> stringResource(R.string.light_upload_done, name)
                else -> stringResource(R.string.light_uploading, name)
            }, style = MaterialTheme.typography.bodyMedium)
            if (!t.done) LinearWavyProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
            if (t.done) TextButton(onClick = clear, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.light_ok)) }
        }
    }
}

@Composable private fun DesignRow(d: LightDesign, active: Boolean, canSend: Boolean, edit: () -> Unit, delete: () -> Unit, send: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(IonShapes.l)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.title(), style = MaterialTheme.typography.titleMedium)
                    Text(templateLabel(d.template) + " · " + stringResource(R.string.light_interval_value, (d.intervalMs / 1000.0).fmt(2)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = edit) { Icon(Icons.Rounded.Edit, stringResource(R.string.light_edit)) }
                IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) }
            }
            LedStrip(d)
            Button(onClick = send, enabled = canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(if (active) Icons.Rounded.Refresh else Icons.Rounded.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.light_send))
            }
        }
    }
}

/** The effect's frames played at its interval over the 70 LEDs in order (as picked, before the wheel's curve). */
@Composable internal fun LedStrip(d: LightDesign, modifier: Modifier = Modifier) {
    val frames = remember(d) { d.frames() }
    var frame by remember(d) { mutableIntStateOf(0) }
    LaunchedEffect(d) { while (frames.size > 1) { delay(d.intervalMs.toLong()); frame = (frame + 1) % frames.size } }
    val off = MaterialTheme.colorScheme.outlineVariant
    val current = frames[frame.coerceIn(0, frames.lastIndex)]
    val description = stringResource(R.string.light_preview)
    Canvas(modifier.fillMaxWidth().height(22.dp).semantics { contentDescription = description }) {
        val per = 35; val rows = 2
        val gap = 2.dp.toPx(); val w = (size.width - gap * (per - 1)) / per; val h = (size.height - gap) / rows
        current.forEachIndexed { i, rgb ->
            val c = if (rgb == 0) off else Color(0xFF000000.toInt() or rgb)
            drawRoundRect(c, Offset((i % per) * (w + gap), (i / per) * (h + gap)), Size(w, h), CornerRadius(w / 3))
        }
    }
}

@Composable private fun storedTitle(e: LightEffects.Stored, designs: List<LightDesign>): String =
    designs.firstOrNull { it.wheelId == e.id }?.title() ?: if (LightDesign.isIonId(e.id)) stringResource(R.string.light_ion_effect, e.id)
        else stringResource(R.string.light_vendor_effect, e.id)

/** What the wheel holds: each effect can be turned on or deleted; unfinished uploads are offered for clean-up. */
@Composable private fun StoredEffects(stored: List<LightEffects.Stored>?, designs: List<LightDesign>, safe: Boolean, refresh: () -> Unit,
    clean: (List<LightEffects.Stored>) -> Unit, delete: (LightEffects.Stored) -> Unit, turnOn: (Long) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(IonShapes.l)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val effects = stored?.filterNot { it.temporary }
            val leftovers = stored?.filter { it.temporary }.orEmpty()
            when {
                stored == null -> Text(stringResource(R.string.light_on_wheel_unknown), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                effects.isNullOrEmpty() -> Text(stringResource(R.string.light_on_wheel_empty), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                else -> effects.forEach { e ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (LightDesign.isIonId(e.id)) Icons.Rounded.Palette else Icons.Rounded.Lightbulb, null,
                            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(storedTitle(e, designs), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { turnOn(e.id) }, enabled = safe) { Text(stringResource(R.string.light_turn_on)) }
                        IconButton(onClick = { delete(e) }, enabled = safe) {
                            Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.light_delete_wheel_title, storedTitle(e, designs)))
                        }
                    }
                }
            }
            if (leftovers.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(IonShapes.m),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pluralStringResource(R.plurals.light_leftovers, leftovers.size, leftovers.size), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { clean(leftovers) }, enabled = safe, modifier = Modifier.heightIn(min = 48.dp).testTag("light-clean")) {
                        Icon(Icons.Rounded.CleaningServices, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.light_clean))
                    }
                }
            }
            Text(stringResource(R.string.light_on_wheel_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            TextButton(onClick = refresh, enabled = safe, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.light_refresh))
            }
        }
    }
}

@Composable private fun DesignEditor(start: LightDesign, onDismiss: () -> Unit, save: (LightDesign) -> Unit) {
    var d by remember(start) { mutableStateOf(start) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (start.name.isEmpty()) R.string.light_new else R.string.light_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(d.name, { d = d.copy(name = it.take(LightDesign.MAX_NAME)) }, singleLine = true,
                    label = { Text(stringResource(R.string.light_name)) }, modifier = Modifier.fillMaxWidth())
                LedStrip(d)
                Text(stringResource(R.string.light_template), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LightDesign.Template.entries.forEach { t ->
                        FilterChip(selected = d.template == t, onClick = { d = d.copy(template = t) }, label = { Text(templateLabel(t)) })
                    }
                }
                if (d.template.usesFirst) Swatches(stringResource(R.string.light_color1), d.color1) { d = d.copy(color1 = it) }
                if (d.template.usesSecond) Swatches(stringResource(if (d.template == LightDesign.Template.CHASE) R.string.light_color_bg
                    else R.string.light_color2), d.color2) { d = d.copy(color2 = it) }
                Text(stringResource(R.string.light_interval_value, (d.intervalMs / 1000.0).fmt(2)), style = MaterialTheme.typography.labelLarge)
                Slider(value = d.intervalMs.toFloat(), onValueChange = { d = d.copy(intervalMs = ((it / 50).roundToInt() * 50).coerceIn(LightEffects.INTERVAL_MS)) },
                    valueRange = LightEffects.INTERVAL_MS.first.toFloat()..LightEffects.INTERVAL_MS.last.toFloat(), steps = 17)
            }
        },
        confirmButton = { TextButton(onClick = { save(d.copy(name = d.name.trim())) }) { Text(stringResource(R.string.save)) } },
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
                    .clickable(role = Role.RadioButton) { pick(rgb) }.padding(4.dp).clip(CircleShape)
                    .then(Modifier.semantics { contentDescription = "#%06X".format(rgb) })) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(if (rgb == 0) Color(0xFF202020) else Color(0xFF000000.toInt() or rgb)) }
                }
            }
        }
    }
}
