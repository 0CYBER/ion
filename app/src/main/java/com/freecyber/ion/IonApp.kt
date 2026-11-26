@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History as HistoryOutlined
import androidx.compose.material.icons.outlined.Settings as SettingsOutlined
import androidx.compose.material.icons.outlined.Speed as SpeedOutlined
import androidx.compose.material.icons.outlined.TripOrigin as WheelOutlined
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Navigation order. Ride history is a destination of its own, between the ride screen and settings. */
internal object Tabs { const val RIDE = 0; const val HISTORY = 1; const val SETTINGS = 2; const val WHEEL = 3 }

/** The activity owns platform wiring; this shell owns only saveable presentation destinations. */
@Composable internal fun IonApp(ui: VelaUi,dark: Boolean,prefs: PrefsUi,actions: Actions,initialTab: Int=0) =
    IonAppContent({ui},dark,prefs,actions,initialTab)

/** Defer live-state reads to the destination; static settings and the navigation bar do not observe telemetry. */
@Composable internal fun IonAppContent(ui: ()->VelaUi,dark: Boolean,prefs: PrefsUi,actions: Actions,initialTab: Int=0) {
    var tab by rememberSaveable {mutableIntStateOf(initialTab)}
    var routes by rememberSaveable {mutableStateOf(emptyList<String>())}
    val detail=routes.lastOrNull()
    // Direction of the last stack move: pushing slides the new page in from the end edge, Back from the start edge.
    var pushed by remember {mutableStateOf(true)}
    fun open(route: String) { if(routes.lastOrNull()!=route) {pushed=true;routes=routes+route} }
    fun back() {pushed=false;routes=routes.dropLast(1)}
    fun select(index: Int) {tab=index;routes=emptyList()}
    var forget by rememberSaveable {mutableStateOf(false)}
    val savedPages=rememberSaveableStateHolder()
    val motion=MaterialTheme.motionScheme
    BackHandler(detail!=null) {back()}
    // Back on the History, Settings or Wheel tab returns to Ride first, the usual pattern for bottom navigation.
    BackHandler(detail==null && tab!=Tabs.RIDE) {tab=Tabs.RIDE}
    // Pass writes straight through: BleController.apply guards the tier itself and reports a refusal
    // through writeMessage. The old silent drop left taps with no feedback at all.
    fun write(command: V12Protocol.Write) {actions.write(command)}
    fun connect() {ui().saved?.let(actions.connect) ?: run {tab=Tabs.WHEEL;actions.scan()}}
    // A phone on its side has little height: the navigation bar becomes a rail at the start edge.
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding().preferredFrameRate(FrameRateCategory.High)) {
        val rail=maxWidth>maxHeight && maxHeight<480.dp
        // Pages below a tab are full screen; the bar returns with the tab (navigation lives at the top level).
        val showBar=detail==null
        Scaffold(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,
            contentWindowInsets=WindowInsets(0),
            bottomBar={
                if(!rail) AnimatedVisibility(showBar,
                    enter=expandVertically(motion.defaultSpatialSpec(),expandFrom=Alignment.Top)+fadeIn(motion.defaultEffectsSpec()),
                    exit=shrinkVertically(motion.fastSpatialSpec(),shrinkTowards=Alignment.Top)+fadeOut(motion.fastEffectsSpec())) {
                    IonNavigationBar(tab,::select)
                }
            }) {inset ->
            Row(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset)) {
                if(rail) IonRail(tab,::select)
                // Pages draw their own top app bar, which owns the status bar inset; the shell keeps the side and bottom insets.
                val sides=when {
                    rail -> WindowInsets.safeDrawing.only(WindowInsetsSides.End+WindowInsetsSides.Bottom)
                    showBar -> WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    else -> WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom)
                }
                Box(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(sides).testTag("app-content"),contentAlignment=Alignment.TopCenter) {
                    AnimatedContent(targetState=detail ?: "tab:$tab",modifier=Modifier.widthIn(max=840.dp).fillMaxSize(),
                        transitionSpec={
                            val from=initialState; val to=targetState
                            if(from.startsWith("tab:") && to.startsWith("tab:")) {
                                // Top-level switch: fade through, the Material pattern for navigation-bar destinations.
                                (fadeIn(motion.defaultEffectsSpec())+scaleIn(motion.defaultSpatialSpec(),initialScale=.96f)) togetherWith
                                    fadeOut(motion.fastEffectsSpec())
                            } else {
                                // Forward and back within a tab: shared axis X.
                                val forward=when {
                                    from.startsWith("tab:") -> true
                                    to.startsWith("tab:") -> false
                                    else -> pushed
                                }
                                val sign=if(forward) 1 else -1
                                (fadeIn(motion.defaultEffectsSpec())+slideInHorizontally(motion.defaultSpatialSpec()) {sign*it/10}) togetherWith
                                    (fadeOut(motion.fastEffectsSpec())+slideOutHorizontally(motion.fastSpatialSpec()) {-sign*it/10})
                            }
                        },label="destination") {route ->
                        savedPages.SaveableStateProvider(route) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                                when {
                                    route=="appearance" -> AppearanceScreen(dark,prefs.theme,actions) {back()}
                                    route=="diagnostics" -> DiagnosticsScreen(ui(),actions) {back()}
                                    route=="dashboard" -> DashboardEditorScreen(ui(),prefs.dashboard,actions.setDashboard) {back()}
                                    route=="background" -> BackgroundScreen(prefs,actions) {back()}
                                    route.startsWith("trip:") -> TripDetailRoute(route.substringAfter(':').toLongOrNull(),prefs.trips,actions) {back()}
                                    route=="app" -> AppScreen({open("appearance")},{open("background")},{back()}) {open("updates")}
                                    route=="updates" -> UpdatesScreen(prefs.update,actions.update) {back()}
                                    route.startsWith("control:") && ControlCategory.entries.any {it.name==route.substringAfter(':')} ->
                                        ControlScreen(ui(),ControlCategory.valueOf(route.substringAfter(':')),
                                            {actions.read(V12Protocol.Read.SETTINGS)},::write,prefs.sounds,actions.setSoundLabels,
                                            actions.lights.copy(designs=prefs.lightDesigns,last=prefs.lightLast),actions.password) {back()}
                                    route=="tab:${Tabs.HISTORY}" -> TripsScreen(prefs.trips,actions,{trip -> open("trip:${trip.startedAt}")})
                                    route=="tab:${Tabs.SETTINGS}" -> SettingsScreen(SettingsLinks(app={open("app")},appearance={open("appearance")},
                                        background={open("background")},updates={open("updates")})) {open("control:${it.name}")}
                                    route=="tab:${Tabs.WHEEL}" -> WheelScreen(ui(),actions,actions.connect,{forget=true},{open("diagnostics")})
                                    else -> RideScreen(ui(),prefs.dashboard,::connect,::write,{tab=Tabs.WHEEL},{tab=Tabs.SETTINGS},{open("dashboard")},
                                        {open("control:${ControlCategory.RIDE.name}")},{select(Tabs.HISTORY)},actions.resetTrip)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(forget) AlertDialog(onDismissRequest={forget=false},icon={Icon(Icons.Rounded.LinkOff,null)},
        title={Text(stringResource(R.string.forget_title))},
        text={Text(stringResource(R.string.forget_message))},
        confirmButton={TextButton(onClick={forget=false;actions.forget()}) {Text(stringResource(R.string.forget_action))}},
        dismissButton={TextButton(onClick={forget=false}) {Text(stringResource(R.string.cancel))}})
}

private val DockLabels=listOf(R.string.nav_ride,R.string.nav_history,R.string.nav_settings,R.string.nav_wheel)
/** The selected destination shows the filled icon, the others the outlined one (Material navigation guidance). */
private val DockIcons=listOf(Icons.Rounded.Speed,Icons.Rounded.History,Icons.Rounded.Settings,Icons.Rounded.TripOrigin)
private val DockIconsOutlined=listOf(Icons.Outlined.SpeedOutlined,Icons.Outlined.HistoryOutlined,Icons.Outlined.SettingsOutlined,Icons.Outlined.WheelOutlined)

/** Flexible (short) navigation bar: 64 dp, icon above label, pill indicator on the selected destination. */
@Composable private fun IonNavigationBar(tab: Int,select: (Int)->Unit) {
    // Very large fonts: labels stay as accessible names only, so four destinations still fit one row.
    val showLabel=LocalDensity.current.fontScale<=1.3f
    ShortNavigationBar(Modifier.testTag("navigation-dock"),containerColor=MaterialTheme.colorScheme.surfaceContainer) {
        DockLabels.forEachIndexed {index,label ->
            val selected=tab==index
            val title=stringResource(label)
            ShortNavigationBarItem(selected=selected,onClick={select(index)},
                icon={Icon(if(selected) DockIcons[index] else DockIconsOutlined[index],null)},
                label=if(showLabel) {{Text(title,maxLines=1)}} else null,
                modifier=Modifier.semantics {contentDescription=title})
        }
    }
}

/** Landscape counterpart: a navigation rail at the start edge with the same destinations. */
@Composable private fun IonRail(tab: Int,select: (Int)->Unit) {
    WideNavigationRail(modifier=Modifier.fillMaxHeight(),
        colors=WideNavigationRailDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer),
        windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Start+WindowInsetsSides.Vertical),
        arrangement=Arrangement.Center) {
        DockLabels.forEachIndexed {index,label ->
            val selected=tab==index
            val title=stringResource(label)
            WideNavigationRailItem(selected=selected,onClick={select(index)},railExpanded=false,
                icon={Icon(if(selected) DockIcons[index] else DockIconsOutlined[index],null)},
                label={Text(title,maxLines=1)},modifier=Modifier.semantics {contentDescription=title})
        }
    }
}
