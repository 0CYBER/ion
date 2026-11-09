@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.preferredFrameRate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The activity owns platform wiring; this shell owns only saveable presentation destinations. */
@Composable internal fun IonApp(ui: VelaUi,dark: Boolean,prefs: PrefsUi,actions: Actions,initialTab: Int=0) =
    IonAppContent({ui},dark,prefs,actions,initialTab)

/** Defer live-state reads to the destination; static settings and the dock do not observe telemetry. */
@Composable internal fun IonAppContent(ui: ()->VelaUi,dark: Boolean,prefs: PrefsUi,actions: Actions,initialTab: Int=0) {
    var tab by rememberSaveable {mutableIntStateOf(initialTab)}
    var routes by rememberSaveable {mutableStateOf(emptyList<String>())}
    val detail=routes.lastOrNull()
    fun open(route: String) { if(routes.lastOrNull()!=route) routes=routes+route }
    fun back() {routes=routes.dropLast(1)}
    var forget by rememberSaveable {mutableStateOf(false)}
    val savedPages=rememberSaveableStateHolder()
    val motion=MaterialTheme.motionScheme
    BackHandler(detail!=null) {back()}
    // Pass writes straight through: BleController.apply guards the tier itself and reports a refusal
    // through writeMessage. The old silent drop left taps with no feedback at all.
    fun write(command: V12Protocol.Write) {actions.write(command)}
    fun connect() {if(ui().saved!=null) actions.connect(ui().saved!!) else {tab=2;actions.scan()}}
    // A phone on its side has little height: the dock becomes a rail at the start edge.
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().preferredFrameRate(FrameRateCategory.High)) {
    val rail=maxWidth>maxHeight && maxHeight<480.dp
    val dockClearance=if(rail) 0.dp else 72.dp+WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalDockClearance provides dockClearance) {
    // A transparent container makes Scaffold inherit LocalContentColor, which is Color.Black at the root:
    // page headings without an explicit color then stayed black on the dark background.
    Scaffold(containerColor=Color.Transparent,contentColor=MaterialTheme.colorScheme.onBackground,
        contentWindowInsets=if(rail) WindowInsets.safeDrawing else WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)) {inset ->
        Row(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset)) {
        if(rail) IonRail(tab) {tab=it;routes=emptyList()}
        Box(Modifier.weight(1f).fillMaxHeight().testTag("app-content"),contentAlignment=Alignment.TopCenter) {
            AnimatedContent(targetState=detail ?: "tab:$tab",modifier=Modifier.widthIn(max=840.dp).fillMaxSize(),
                transitionSpec={(fadeIn(motion.defaultEffectsSpec())+slideInHorizontally(motion.defaultSpatialSpec()) {it/12}) togetherWith fadeOut(motion.fastEffectsSpec())},label="destination") {route ->
                savedPages.SaveableStateProvider(route) {
                    when {
                        route=="appearance" -> AppearanceScreen(dark,prefs.themeFollowsSystem,actions.theme,actions.followSystemTheme) {back()}
                        route=="diagnostics" -> DiagnosticsScreen(ui(),actions) {back()}
                        route=="dashboard" -> DashboardEditorScreen(ui(),prefs.dashboard,actions.setDashboard) {back()}
                        route=="background" -> BackgroundScreen(prefs,actions) {back()}
                        route=="trips" -> TripsScreen(prefs.trips,actions,{trip -> open("trip:${trip.startedAt}")}) {back()}
                        route.startsWith("trip:") -> TripDetailRoute(route.substringAfter(':').toLongOrNull(),prefs.trips,actions) {back()}
                        route=="app" -> AppScreen({open("appearance")},{open("dashboard")},{open("trips")},{open("background")}) {back()}
                        route.startsWith("control:") -> ControlScreen(ui(),ControlCategory.valueOf(route.substringAfter(':')),
                            {actions.read(V12Protocol.Read.SETTINGS)},::write) {back()}
                        route=="tab:1" -> SettingsScreen({open("app")}) {open("control:${it.name}")}
                        route=="tab:2" -> WheelScreen(ui(),actions,actions.connect,{forget=true},{open("diagnostics")})
                        else -> RideScreen(ui(),prefs.dashboard,::connect,::write,{tab=2},{tab=1},{open("dashboard")},{open("control:${ControlCategory.RIDE.name}")},
                            {open("trips")},actions.resetTrip)
                    }
                }
            }
        }
        }
    }
    }
    if(!rail) Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=16.dp,vertical=6.dp)) {
        IonDock(tab) {tab=it;routes=emptyList()}
    }
    }
    if(forget) AlertDialog(onDismissRequest={forget=false},title={Text(stringResource(R.string.forget_title))},
        text={Text(stringResource(R.string.forget_message))},
        confirmButton={TextButton(onClick={forget=false;actions.forget()}) {Text(stringResource(R.string.forget_action))}},
        dismissButton={TextButton(onClick={forget=false}) {Text(stringResource(R.string.cancel))}})
}

private val DockLabels=listOf(R.string.nav_ride,R.string.nav_settings,R.string.nav_wheel)
private val DockIcons=listOf(Icons.Rounded.Home,Icons.Rounded.Tune,Icons.Rounded.TripOrigin)

/** Landscape counterpart of [IonDock]: icon-only pills stacked at the start edge, names kept for TalkBack. */
@Composable private fun IonRail(tab: Int,select: (Int)->Unit) {
    Surface(Modifier.padding(start=12.dp,top=12.dp,bottom=12.dp).fillMaxHeight(),shape=RoundedCornerShape(40.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxHeight().selectableGroup().padding(5.dp),verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
            DockLabels.forEachIndexed {index,label ->
                val selected=tab==index
                val color by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.motionScheme.fastEffectsSpec(),label="rail-color")
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(50)).background(color)
                    .selectable(selected=selected,role=Role.Tab,onClick={select(index)}),contentAlignment=Alignment.Center) {
                    Icon(DockIcons[index],stringResource(label),Modifier.size(24.dp),
                        tint=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Expanding selected destination: one label, three full accessible names at every font scale. */
@Composable private fun IonDock(tab: Int,select: (Int)->Unit) {
    val labels=DockLabels
    val icons=DockIcons
    val showLabel=LocalDensity.current.fontScale<=1.3f
    Surface(Modifier.testTag("navigation-dock"),shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.selectableGroup().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            labels.forEachIndexed {index,label ->
                val selected=tab==index
                val color=animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    MaterialTheme.motionScheme.fastEffectsSpec(),label="dock-color")
                val title=stringResource(label)
                Row(Modifier.heightIn(min=48.dp).widthIn(min=48.dp).clip(RoundedCornerShape(50)).drawBehind {drawRect(color.value)}
                    .selectable(selected=selected,role=Role.Tab,onClick={select(index)}).padding(horizontal=14.dp,vertical=10.dp),
                    horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    Icon(icons[index],if(!selected || !showLabel) title else null,Modifier.size(22.dp),
                        tint=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedVisibility(selected && showLabel,enter=expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())+fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit=shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())+fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
                        Row {Spacer(Modifier.width(8.dp));Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimary,maxLines=1)}
                    }
                }
            }
        }
    }
}
internal val LocalDockClearance=staticCompositionLocalOf {0.dp}
internal val PagePadding: PaddingValues
    @Composable get()=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=20.dp+LocalDockClearance.current)
