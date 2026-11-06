@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign

@Composable internal fun DiagnosticsScreen(ui: VelaUi,actions: Actions,onBack: ()->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var mode by rememberSaveable {mutableIntStateOf(0)}
    val focusManager=LocalFocusManager.current
    val selectedReads=V12Protocol.Read.entries.filter {when(mode) {1 -> it==V12Protocol.Read.TELEMETRY;2 -> it==V12Protocol.Read.SETTINGS;else -> true}}
        .filter {read -> query.isBlank() || read.label.contains(query,true) ||
            ui.wheel.records[read]?.frame?.let(V12Protocol::fields).orEmpty().any {(it.key+it.label+it.value).contains(query,true)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item(key="intro",contentType="intro") {DetailHeading(stringResource(R.string.diagnostics_title),stringResource(R.string.data_subtitle),onBack)}
        item(key="status",contentType="status") {Status(ui)}
        if(!ui.connected) item(key="offline",contentType="banner") {Banner(stringResource(R.string.wheel_offline),Icons.Rounded.SensorsOff)}
        item(key="search",contentType="search") {
            TextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus()}),
                label={Text(stringResource(R.string.data_search))},leadingIcon={Icon(Icons.Rounded.Search,null)},
                trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}) {Icon(Icons.Rounded.Close,stringResource(R.string.data_clear_search))}},
                shape=RoundedCornerShape(IonShapes.xl),colors=TextFieldDefaults.colors(
                    focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,
                    focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerHigh,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerHigh))
        }
        item(key="actions",contentType="actions") {MetricPair(first={m -> ReadAllButton(ui,actions,m)},second={m -> ExportButton(ui,m)})}
        item(key="filters",contentType="filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(stringResource(R.string.data_filter_all),stringResource(R.string.data_filter_telemetry),stringResource(R.string.data_filter_settings),stringResource(R.string.data_filter_raw)).forEachIndexed {index,label ->
                    val corner by animateDpAsState(if(mode==index) IonShapes.row else IonShapes.s,MaterialTheme.motionScheme.fastSpatialSpec(),label="filter-shape")
                    FilterChip(selected=mode==index,onClick={mode=index},shape=RoundedCornerShape(corner),
                        leadingIcon=if(mode==index) ({Icon(Icons.Rounded.Check,null,Modifier.size(18.dp))}) else null,label={Text(label)})
                }
            }
        }
        if(selectedReads.isEmpty()) item(key="empty",contentType="empty") {
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.data_no_results),style=MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.data_no_results_hint),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(selectedReads,key={it.name},contentType={it.command}) {read ->
            val record=ui.wheel.records[read]
            val fields=record?.frame?.let(V12Protocol::fields).orEmpty().filter {query.isBlank() || read.label.contains(query,true) || (it.key+it.label+it.value).contains(query,true)}
            PacketCard(read,record,ui.now,fields,mode==3,ui.connected && !ui.wheel.workPending) {actions.read(read)}
        }
    }
}
@Composable private fun ExportButton(ui: VelaUi,modifier: Modifier) {
    val ctx=LocalContext.current
    val savedMessage=stringResource(R.string.data_export_saved)
    val emptyMessage=stringResource(R.string.data_no_export)
    OutlinedButton(onClick={
        val file=exportRecords(ctx,ui.wheel.records)
        android.widget.Toast.makeText(ctx,if(file!=null) savedMessage.format(file.name) else emptyMessage,android.widget.Toast.LENGTH_LONG).show()
    },enabled=ui.wheel.records.isNotEmpty(),modifier=modifier.heightIn(min=48.dp)) {
        Icon(Icons.Rounded.IosShare,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.data_export),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun PacketCard(read: V12Protocol.Read,record: PacketRecord?,now: Long,fields: List<V12Protocol.Field>,rawMode: Boolean,enabled: Boolean,refresh: ()->Unit) {
    var expanded by rememberSaveable(read) { mutableStateOf(read in listOf(V12Protocol.Read.TELEMETRY,V12Protocol.Read.SETTINGS)) }
    var raw by rememberSaveable(read) { mutableStateOf(false) }
    Surface(shape=RoundedCornerShape(IonShapes.xl),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(Modifier.fillMaxWidth().clickable(enabled=!rawMode) {expanded=!expanded}.padding(start=18.dp,top=10.dp,bottom=10.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(read.label,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                    PacketAge(record,now)
                }
                IconButton(onClick=refresh,enabled=enabled) { Icon(Icons.Rounded.Refresh,stringResource(R.string.data_refresh,read.label)) }
                if(!rawMode) Icon(if(expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,stringResource(R.string.data_expand),Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            AnimatedVisibility(expanded || rawMode) {
                Column(Modifier.padding(horizontal=18.dp).padding(bottom=16.dp)) {
                    if(!rawMode) fields.forEach { field -> FieldRow(field) }
                    if(record?.frame==null) Text(stringResource(R.string.data_no_answer),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        if(!rawMode) TextButton(onClick={raw=!raw}) { Icon(Icons.Rounded.Code,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(raw || rawMode) stringResource(R.string.data_raw_collapse) else stringResource(R.string.data_raw_show)) }
                        if(raw || rawMode) Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(IonShapes.s)) {
                            SelectionContainer { Text(V12Protocol.raw(record.frame),fontFamily=IonMono,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.fillMaxWidth().padding(12.dp).horizontalScroll(rememberScrollState())) }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun FieldRow(field: V12Protocol.Field) {
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(field.label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            SelectionContainer { Text(field.value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium,fontFamily=IonMono,modifier=Modifier.widthIn(max=155.dp),textAlign=TextAlign.End) }
        }
        if(field.note.isNotEmpty()) Text(field.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
}


/** Only this text observes the age clock; packets, fields and raw rendering do not tick. */
@Composable private fun PacketAge(record: PacketRecord?, snapshotAt: Long) {
    val now by produceState(snapshotAt, snapshotAt, record?.at) {
        if(record==null) return@produceState
        val anchor=android.os.SystemClock.elapsedRealtime()
        while(true) {
            value=snapshotAt+android.os.SystemClock.elapsedRealtime()-anchor
            kotlinx.coroutines.delay(1000)
        }
    }
    val age=record?.at?.let {stringResource(R.string.data_age,(now-it).coerceAtLeast(0)/1000)}.orEmpty()
    Text((record?.status ?: stringResource(R.string.data_not_requested))+age,
        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
