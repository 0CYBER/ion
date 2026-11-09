@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.freecyber.ion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val DashToggle.titleRes: Int get() = when(this) {
    DashToggle.LOW_BEAM -> R.string.quick_light
    DashToggle.HIGH_BEAM -> R.string.control_high_beam
    DashToggle.AUTO_LIGHT -> R.string.control_auto_light
    DashToggle.MUTE -> R.string.quick_mute
    DashToggle.SOUND_WAVE -> R.string.control_sound_wave
    DashToggle.OFF_ROAD -> R.string.control_off_road
    DashToggle.ADVANCED -> R.string.control_advanced
    DashToggle.SPLIT -> R.string.control_split
    DashToggle.HANDLE -> R.string.control_handle
    DashToggle.TRANSPORT -> R.string.control_transport
    DashToggle.LOCK -> R.string.control_lock
}
internal val DashToggle.icon get() = when(this) {
    DashToggle.LOW_BEAM -> Icons.Rounded.Lightbulb
    DashToggle.HIGH_BEAM -> Icons.Rounded.Highlight
    DashToggle.AUTO_LIGHT -> Icons.Rounded.AutoAwesome
    DashToggle.MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
    DashToggle.SOUND_WAVE -> Icons.Rounded.MusicNote
    DashToggle.OFF_ROAD -> Icons.Rounded.Terrain
    DashToggle.ADVANCED -> Icons.Rounded.Spa
    DashToggle.SPLIT -> Icons.AutoMirrored.Rounded.CallSplit
    DashToggle.HANDLE -> Icons.Rounded.TouchApp
    DashToggle.TRANSPORT -> Icons.Rounded.LocalShipping
    DashToggle.LOCK -> Icons.Rounded.Lock
}
internal val TileSlot.titleRes get() = toggleTarget?.titleRes ?: tile.titleRes
internal val TileSlot.icon get() = toggleTarget?.icon ?: tile.icon

/** All edits stay next to the selected tile in a sheet; adding is always within reach. */
@Composable internal fun DashboardEditorScreen(ui: VelaUi, layout: DashboardLayout, change: (DashboardLayout)->Unit, onBack: ()->Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var catalog by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var reveal by remember { mutableStateOf<String?>(null) }
    val listState=rememberLazyListState()
    val slot = layout.slots.firstOrNull { it.id == selected }
    // Frozen preview: telemetry updates must not move the editor while the rider arranges it.
    val preview = remember { if(ui.wheel.telemetry != null) ui else EditorSample.ui }
    val env = remember(preview) { TileEnv(preview, {}, {}) }
    val rows = remember(layout) { layout.rows() }
    LaunchedEffect(layout,reveal) {
        val id=reveal ?: return@LaunchedEffect
        val index=rows.indexOfFirst { row -> row.any {it.id==id} }
        if(index>=0) {listState.scrollToItem(index+1);reveal=null}
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * .9f
        Column(Modifier.fillMaxSize().padding(bottom=LocalDockClearance.current)) {
            LazyColumn(Modifier.weight(1f).testTag("dashboard-preview"), state=listState, contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item(key="heading", contentType="heading") { DetailHeading(stringResource(R.string.dash_editor_title), stringResource(R.string.dash_tap_hint), onBack) }
                if(rows.isEmpty()) item(key="empty", contentType="empty") { Banner(stringResource(R.string.dash_empty)) }
                items(rows, key={ it.joinToString("+") { s -> s.id } }, contentType={ if(it.size==2) "pair" else "single" }) { row ->
                    TilePair(
                        first={ m -> PreviewTile(row[0],env,m) { selected=row[0].id } },
                        second=if(row.size==2) ({ m -> PreviewTile(row[1],env,m) { selected=row[1].id } }) else null)
                }
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=20.dp, vertical=8.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
                FilledTonalIconButton(onClick={reset=true},enabled=layout!=DashboardLayout.DEFAULT,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.RestartAlt,stringResource(R.string.dash_reset))
                }
                Button(onClick={catalog=true}, modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("add-tile")) {
                    Icon(Icons.Rounded.Add,null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.dash_add))
                }
            }
        }
        if(catalog || slot!=null) ModalBottomSheet(onDismissRequest={catalog=false;selected=null}, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().heightIn(max=sheetHeight).padding(horizontal=20.dp)) {
                if(catalog) {
                    Text(stringResource(R.string.dash_add),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(bottom=16.dp))
                    val groups = listOf(
                        R.string.dash_actions to listOf(DashTile.TOGGLE,DashTile.PLAY_SOUND,DashTile.MODE),
                        R.string.dash_readings to listOf(DashTile.SPEED,DashTile.BATTERY,DashTile.RANGE,DashTile.VOLTAGE,DashTile.CURRENT,DashTile.POWER,DashTile.MOTOR_TEMP,DashTile.MOS_TEMP,DashTile.PWM,DashTile.TILT,DashTile.CHART),
                        R.string.trips_title to listOf(DashTile.TRIP_SESSION,DashTile.TRIP,DashTile.ODOMETER,DashTile.MAX_SPEED,DashTile.AVG_SPEED,DashTile.ENERGY,DashTile.CONSUMPTION))
                    LazyColumn(Modifier.weight(1f,false),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        groups.forEach { (title,tiles) ->
                            item(key=title,contentType="heading") { SectionHeader(stringResource(title)) }
                            items(tiles,key={it.name},contentType={"catalog"}) { tile ->
                                ListItem(headlineContent={Text(stringResource(tile.titleRes))},leadingContent={Icon(tile.icon,null)},
                                    trailingContent={Icon(Icons.Rounded.Add,null)}, modifier=Modifier.testTag("catalog-${tile.name}").clickable {
                                        val next=layout.add(tile)
                                        change(next); selected=next.visible.last().id; reveal=selected; catalog=false
                                    })
                            }
                        }
                    }
                } else if(slot!=null) {
                    Text(stringResource(slot.titleRes),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.testTag("tile-editor-title"))
                    val index=layout.visible.indexOfFirst {it.id==slot.id}
                    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={change(layout.moveVisible(slot.id,-1))},enabled=index>0,modifier=Modifier.weight(1f)) {
                            Icon(Icons.Rounded.ArrowUpward,stringResource(R.string.dash_move_up,stringResource(slot.titleRes)))
                        }
                        Text("${index+1} / ${layout.visible.size}",style=MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick={change(layout.moveVisible(slot.id,1))},enabled=index<layout.visible.lastIndex,modifier=Modifier.weight(1f)) {
                            Icon(Icons.Rounded.ArrowDownward,stringResource(R.string.dash_move_down,stringResource(slot.titleRes)))
                        }
                    }
                    LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(slot.tile.canBeHalf) item(key="size",contentType="size") {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected=!slot.effectiveWide,onClick={if(slot.effectiveWide) change(layout.resize(slot.id))},label={Text(stringResource(R.string.dash_half))})
                                FilterChip(selected=slot.effectiveWide,onClick={if(!slot.effectiveWide) change(layout.resize(slot.id))},label={Text(stringResource(R.string.dash_wide))})
                            }
                        }
                        if(slot.toggleTarget!=null) item(key="target",contentType="target") {
                            Text(stringResource(R.string.dash_assign),style=MaterialTheme.typography.titleMedium)
                            Column(Modifier.selectableGroup()) {
                                DashToggle.entries.forEach { target ->
                                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("target-${target.name}")
                                        .selectable(selected=slot.toggleTarget==target,role=Role.RadioButton,onClick={change(layout.setToggle(slot.id,target))}),
                                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected=slot.toggleTarget==target,onClick=null)
                                        Icon(target.icon,null,Modifier.size(20.dp))
                                        Text(stringResource(target.titleRes),style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        slot.tile.param?.let { range -> item(key="sound",contentType="sound") {
                            Text(stringResource(R.string.dash_param_sound),style=MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                range.forEach { n -> FilterChip(selected=slot.param==n,onClick={change(layout.setParam(slot.id,n))},label={Text("$n")}) }
                            }
                        } }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick={selected=null;change(layout.remove(slot.id))},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                            Icon(Icons.Rounded.DeleteOutline,null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.dash_remove))
                        }
                        Button(onClick={selected=null},modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text(stringResource(R.string.dash_done)) }
                    }
                }
            }
        }
    }
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text(stringResource(R.string.dash_reset))},text={Text(stringResource(R.string.dash_reset_message))},
        confirmButton={TextButton(onClick={change(DashboardLayout.DEFAULT);reset=false;selected=null}) {Text(stringResource(R.string.dash_reset))}},
        dismissButton={TextButton(onClick={reset=false}) {Text(stringResource(R.string.cancel))}})
}

@Composable private fun PreviewTile(slot: TileSlot,env: TileEnv,modifier: Modifier,tap: ()->Unit) {
    val title=stringResource(slot.titleRes)
    Box(modifier) {
        Box(Modifier.clearAndSetSemantics { }) { DashTileView(slot,env,Modifier.fillMaxWidth()) }
        // One touch target overlays all interactive content, so editing never sends commands.
        Box(Modifier.matchParentSize().testTag("preview-${slot.id}").semantics {contentDescription=title}
            .clickable(role=Role.Button,onClickLabel=stringResource(R.string.dash_edit_tile,title),onClick=tap))
        Surface(Modifier.align(Alignment.TopEnd).padding(6.dp),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceContainerHighest) {
            Icon(Icons.Rounded.Edit,null,Modifier.padding(5.dp).size(14.dp))
        }
    }
}
