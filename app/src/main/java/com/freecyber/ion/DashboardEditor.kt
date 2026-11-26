@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.freecyber.ion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    DashToggle.RANGE_ESTIMATE -> R.string.control_range_estimate
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
    DashToggle.RANGE_ESTIMATE -> Icons.Rounded.Navigation
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
    // Removing a tile is one tap; the snackbar offers it back instead of a confirmation dialog.
    val snackbar=remember {SnackbarHostState()}
    val scope=rememberCoroutineScope()
    val latestLayout by rememberUpdatedState(layout)
    val removedTemplate=stringResource(R.string.dash_removed,"%s")
    val undoLabel=stringResource(R.string.common_undo)
    val slot = layout.slots.firstOrNull { it.id == selected }
    // Frozen preview: telemetry updates must not move the editor while the rider arranges it.
    val preview = remember { if(ui.wheel.telemetry != null) ui else EditorSample.ui }
    val env = remember(preview) { TileEnv(preview, {}, {}) }
    val rows = remember(layout) { layout.rows() }
    LaunchedEffect(layout,reveal) {
        val id=reveal ?: return@LaunchedEffect
        val index=rows.indexOfFirst { row -> row.any {it.id==id} }
        // Smooth scroll so the moved or added tile ends up at the top, in view above the sheet.
        if(index>=0) {listState.animateScrollToItem(index+1);reveal=null}
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * .9f
        IonPage(stringResource(R.string.dash_editor_title), stringResource(R.string.dash_tap_hint), onBack, state=listState,
            listModifier=Modifier.testTag("dashboard-preview"),
            contentPadding=PaddingValues(start=IonSpacing.page,end=IonSpacing.page,top=8.dp,bottom=104.dp), actions={
            IconButton(onClick={reset=true},enabled=layout!=DashboardLayout.DEFAULT,shapes=IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.RestartAlt,stringResource(R.string.dash_reset))
            }
        }) {
            run {
                if(rows.isEmpty()) item(key="empty", contentType="empty") { Banner(stringResource(R.string.dash_empty)) }
                // Keyed by the first tile: half tiles re-pair after a move, and a key made of both ids made every
                // affected row vanish and reappear instead of sliding to its new place.
                items(rows, key={ it.first().id }, contentType={ if(it.size==2) "pair" else "single" }) { row ->
                    // animateItem: Up/Down moves visibly slide the tile instead of teleporting it.
                    Box(Modifier.fillMaxWidth().animateItem()) { TilePair(
                        first={ m -> PreviewTile(row[0],env,m,row[0].id==selected) { selected=row[0].id } },
                        second=if(row.size==2) ({ m -> PreviewTile(row[1],env,m,row[1].id==selected) { selected=row[1].id } }) else null) }
                }
            }
        }
        // The primary action of the page: an extended FAB, as Material places "create".
        ExtendedFloatingActionButton(onClick={catalog=true},
            modifier=Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end=IonSpacing.page,bottom=16.dp).testTag("add-tile"),
            icon={Icon(Icons.Rounded.Add,null)},text={Text(stringResource(R.string.dash_add))})
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=88.dp))
        if(catalog || slot!=null) ModalBottomSheet(onDismissRequest={catalog=false;selected=null}, sheetState=rememberBottomSheetState(initialValue=SheetValue.Hidden,enabledValues=setOf(SheetValue.Hidden,SheetValue.Expanded))) {
            Column(Modifier.fillMaxWidth().heightIn(max=sheetHeight).padding(horizontal=IonSpacing.page)) {
                if(catalog) {
                    Text(stringResource(R.string.dash_add),style=MaterialTheme.typography.headlineSmallEmphasized,modifier=Modifier.padding(start=8.dp,bottom=8.dp))
                    val groups = listOf(
                        R.string.dash_actions to listOf(DashTile.TOGGLE,DashTile.PLAY_SOUND,DashTile.MODE),
                        R.string.dash_readings to listOf(DashTile.SPEED,DashTile.BATTERY,DashTile.RANGE,DashTile.VOLTAGE,DashTile.CURRENT,DashTile.POWER,DashTile.MOTOR_TEMP,DashTile.MOS_TEMP,DashTile.PWM,DashTile.TILT,DashTile.CHART),
                        R.string.trips_title to listOf(DashTile.TRIP_SESSION,DashTile.TRIP,DashTile.ODOMETER,DashTile.MAX_SPEED,DashTile.AVG_SPEED,DashTile.ENERGY,DashTile.CONSUMPTION))
                    LazyColumn(Modifier.weight(1f,false),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(IonSpacing.seam)) {
                        groups.forEach { (title,tiles) ->
                            item(key=title,contentType="heading") { SectionHeader(stringResource(title),Modifier.padding(bottom=8.dp)) }
                            itemsIndexed(tiles,key={_,it -> it.name},contentType={_,_ -> "catalog"}) { index,tile ->
                                Surface(onClick={
                                        val next=layout.add(tile)
                                        change(next); selected=next.visible.last().id; reveal=selected; catalog=false
                                    },shape=segmentShape(index,tiles.size),color=MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier=Modifier.fillMaxWidth().testTag("catalog-${tile.name}")) {
                                    Row(Modifier.heightIn(min=56.dp).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,
                                        horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                        ShapeBadge(tile.icon,size=36.dp,polygon=MaterialShapes.Circle)
                                        Text(stringResource(tile.titleRes),style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
                                        Icon(Icons.Rounded.Add,null,tint=MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                } else if(slot!=null) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.padding(bottom=8.dp)) {
                        ShapeBadge(slot.icon,size=48.dp,polygon=MaterialShapes.Cookie9Sided,containerColor=MaterialTheme.colorScheme.primaryContainer,
                            contentColor=MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(slot.titleRes),style=MaterialTheme.typography.headlineSmallEmphasized,modifier=Modifier.testTag("tile-editor-title"))
                    }
                    val index=layout.visible.indexOfFirst {it.id==slot.id}
                    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick={change(layout.moveVisible(slot.id,-1));reveal=slot.id},enabled=index>0,shapes=ButtonDefaults.shapes(),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                            Icon(Icons.Rounded.ArrowUpward,stringResource(R.string.dash_move_up,stringResource(slot.titleRes)))
                        }
                        val position=stringResource(R.string.dash_position,index+1,layout.visible.size)
                        Text("${index+1} / ${layout.visible.size}",style=MaterialTheme.typography.titleMediumEmphasized.copy(fontFeatureSettings="tnum"),
                            modifier=Modifier.clearAndSetSemantics {contentDescription=position})
                        FilledTonalButton(onClick={change(layout.moveVisible(slot.id,1));reveal=slot.id},enabled=index<layout.visible.lastIndex,shapes=ButtonDefaults.shapes(),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                            Icon(Icons.Rounded.ArrowDownward,stringResource(R.string.dash_move_down,stringResource(slot.titleRes)))
                        }
                    }
                    LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(slot.tile.canBeHalf) item(key="size",contentType="size") {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                                ToggleButton(checked=!slot.effectiveWide,onCheckedChange={if(slot.effectiveWide) change(layout.resize(slot.id))},
                                    shapes=ButtonGroupDefaults.connectedLeadingButtonShapes(),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                                    Icon(Icons.Rounded.ViewColumn,null,Modifier.size(ButtonDefaults.IconSize));Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.dash_half))
                                }
                                ToggleButton(checked=slot.effectiveWide,onCheckedChange={if(!slot.effectiveWide) change(layout.resize(slot.id))},
                                    shapes=ButtonGroupDefaults.connectedTrailingButtonShapes(),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                                    Icon(Icons.Rounded.ViewStream,null,Modifier.size(ButtonDefaults.IconSize));Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.dash_wide))
                                }
                            }
                        }
                        if(slot.toggleTarget!=null) item(key="target",contentType="target") {
                            Text(stringResource(R.string.dash_assign),style=MaterialTheme.typography.titleSmallEmphasized,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp,bottom=4.dp))
                            Column(Modifier.selectableGroup()) {
                                DashToggle.entries.forEach { target ->
                                    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clip(RoundedCornerShape(IonShapes.l)).testTag("target-${target.name}")
                                        .selectable(selected=slot.toggleTarget==target,role=Role.RadioButton,onClick={change(layout.setToggle(slot.id,target))}),
                                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected=slot.toggleTarget==target,onClick=null)
                                        Icon(target.icon,null,Modifier.size(20.dp))
                                        Text(stringResource(target.titleRes),style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        if (slot.tile.paramOptions.isNotEmpty()) { item(key="sound",contentType="sound") {
                            Text(stringResource(R.string.dash_param_sound),style=MaterialTheme.typography.titleSmallEmphasized,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp,bottom=4.dp))
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                slot.tile.paramOptions.forEach { n -> FilterChip(selected=slot.param==n,onClick={change(layout.setParam(slot.id,n))},label={Text("$n")}) }
                            }
                        } }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        val removedTitle=stringResource(slot.titleRes)
                        OutlinedButton(onClick={
                            val before=layout
                            selected=null;change(layout.remove(slot.id))
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result=snackbar.showSnackbar(removedTemplate.format(removedTitle),undoLabel,withDismissAction=true,duration=SnackbarDuration.Long)
                                // Only restore if nothing else was edited meanwhile; otherwise undo would discard newer changes.
                                if(result==SnackbarResult.ActionPerformed && latestLayout==before.remove(slot.id)) change(before)
                            }
                        },shapes=ButtonDefaults.shapes(),modifier=Modifier.weight(1f).heightIn(min=56.dp)) {
                            Icon(Icons.Rounded.DeleteOutline,null,Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.width(ButtonDefaults.IconSpacing)); Text(stringResource(R.string.dash_remove))
                        }
                        Button(onClick={selected=null},shapes=ButtonDefaults.shapes(),modifier=Modifier.weight(1f).heightIn(min=56.dp)) {
                            Icon(Icons.Rounded.Check,null,Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.width(ButtonDefaults.IconSpacing)); Text(stringResource(R.string.dash_done))
                        }
                    }
                }
            }
        }
    }
    if(reset) AlertDialog(onDismissRequest={reset=false},icon={Icon(Icons.Rounded.RestartAlt,null)},title={Text(stringResource(R.string.dash_reset))},text={Text(stringResource(R.string.dash_reset_message))},
        confirmButton={TextButton(onClick={change(DashboardLayout.DEFAULT);reset=false;selected=null}) {Text(stringResource(R.string.dash_reset))}},
        dismissButton={TextButton(onClick={reset=false}) {Text(stringResource(R.string.cancel))}})
}

@Composable private fun PreviewTile(slot: TileSlot,env: TileEnv,modifier: Modifier,selected: Boolean,tap: ()->Unit) {
    val title=stringResource(slot.titleRes)
    // The tile being edited is outlined, so it can be followed while it moves up and down.
    val outline by androidx.compose.animation.animateColorAsState(
        if(selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
        MaterialTheme.motionScheme.fastEffectsSpec(),label="tile-selected")
    Box(modifier.border(3.dp,outline,androidx.compose.foundation.shape.RoundedCornerShape(IonShapes.xl))) {
        Box(Modifier.clearAndSetSemantics { }) { DashTileView(slot,env,Modifier.fillMaxWidth()) }
        // One touch target overlays all interactive content, so editing never sends commands.
        Box(Modifier.matchParentSize().testTag("preview-${slot.id}").semantics {contentDescription=title}
            .clickable(role=Role.Button,onClickLabel=stringResource(R.string.dash_edit_tile,title),onClick=tap))
        // The selected tile carries a check badge on its outline; the others stay clean, as the rider will see them.
        if(selected) Surface(Modifier.align(Alignment.TopStart).offset(x=(-4).dp,y=(-4).dp),shape=androidx.compose.foundation.shape.CircleShape,
            color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
            Icon(Icons.Rounded.Edit,null,Modifier.padding(6.dp).size(16.dp))
        }
    }
}
