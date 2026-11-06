@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The activity owns platform wiring; this shell owns only saveable presentation destinations. */
@Composable internal fun IonApp(ui: VelaUi,dark: Boolean,prefs: PrefsUi,actions: Actions,initialTab: Int=0) {
    var tab by rememberSaveable {mutableIntStateOf(initialTab)}
    var detail by rememberSaveable {mutableStateOf<String?>(null)}
    var forget by rememberSaveable {mutableStateOf(false)}
    val savedPages=rememberSaveableStateHolder()
    val motion=MaterialTheme.motionScheme
    BackHandler(detail!=null) {detail=null}
    fun write(command: V12Protocol.Write) {if(ui.canWrite(command)) actions.write(command)}
    fun connect() {if(ui.saved!=null) actions.connect(ui.saved) else {tab=2;actions.scan()}}
    Scaffold(modifier=Modifier.imePadding(),containerColor=MaterialTheme.colorScheme.background,
        contentWindowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal),bottomBar={
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                IonDock(tab) {tab=it;detail=null}
            }
        }) {inset ->
        Box(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset),contentAlignment=Alignment.TopCenter) {
            AnimatedContent(targetState=detail ?: "tab:$tab",modifier=Modifier.widthIn(max=840.dp).fillMaxSize(),
                transitionSpec={(fadeIn(motion.defaultEffectsSpec())+slideInHorizontally(motion.defaultSpatialSpec()) {it/30}) togetherWith fadeOut(motion.fastEffectsSpec())},label="destination") {route ->
                savedPages.SaveableStateProvider(route) {
                    when {
                        route=="appearance" -> AppearanceScreen(dark,actions.theme) {detail=null}
                        route=="diagnostics" -> DiagnosticsScreen(ui,actions) {detail=null}
                        route=="dashboard" -> DashboardEditorScreen(prefs.dashboard,actions.setDashboard) {detail=null}
                        route=="background" -> BackgroundScreen(prefs,actions) {detail=null}
                        route=="trips" -> TripsScreen(prefs.trips,actions) {detail=null}
                        route.startsWith("control:") -> ControlScreen(ui,ControlCategory.valueOf(route.substringAfter(':')),
                            {actions.read(V12Protocol.Read.SETTINGS)},::write) {detail=null}
                        route=="tab:1" -> SettingsScreen({detail="appearance"},{detail="background"},{detail="dashboard"},{detail="trips"}) {detail="control:${it.name}"}
                        route=="tab:2" -> WheelScreen(ui,actions,actions.connect,{forget=true},{detail="diagnostics"})
                        else -> RideScreen(ui,prefs.dashboard,::connect,::write,{tab=2},{tab=1},{detail="dashboard"},{detail="control:${ControlCategory.RIDE.name}"},
                            {detail="trips"},actions.resetTrip)
                    }
                }
            }
        }
    }
    if(forget) AlertDialog(onDismissRequest={forget=false},title={Text(stringResource(R.string.forget_title))},
        text={Text(stringResource(R.string.forget_message))},
        confirmButton={TextButton(onClick={forget=false;actions.forget()}) {Text(stringResource(R.string.forget_action))}},
        dismissButton={TextButton(onClick={forget=false}) {Text(stringResource(R.string.cancel))}})
}

/** Expanding selected destination: one label, three full accessible names at every font scale. */
@Composable private fun IonDock(tab: Int,select: (Int)->Unit) {
    val labels=listOf(R.string.nav_ride,R.string.nav_settings,R.string.nav_wheel)
    val icons=listOf(Icons.Rounded.Home,Icons.Rounded.Tune,Icons.Rounded.TripOrigin)
    val showLabel=LocalDensity.current.fontScale<=1.3f
    Surface(modifier=Modifier.widthIn(max=440.dp),shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed {index,label ->
                val selected=tab==index
                val weight by animateFloatAsState(if(selected && showLabel) 1.85f else 1f,MaterialTheme.motionScheme.fastSpatialSpec(),label="dock-width")
                val color by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.motionScheme.fastEffectsSpec(),label="dock-color")
                val title=stringResource(label)
                Row(Modifier.weight(weight).heightIn(min=56.dp).clip(RoundedCornerShape(50)).background(color)
                    .selectable(selected=selected,role=Role.Tab,onClick={select(index)}).padding(horizontal=12.dp,vertical=14.dp),
                    horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    Icon(icons[index],if(!selected || !showLabel) title else null,Modifier.size(24.dp),
                        tint=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if(selected && showLabel) {Spacer(Modifier.width(8.dp));Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimary,maxLines=1)}
                }
            }
        }
    }
}
internal val PagePadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=20.dp)
