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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Navigation order. Ride history is a destination of its own, between the ride screen and settings. */
internal object Tabs { const val RIDE = 0; const val HISTORY = 1; const val SETTINGS = 2; const val WHEEL = 3 }

/** The activity owns platform wiring; this shell owns only saveable presentation destinations. */
@Composable internal fun IonApp(ui: VelaUi,dark: Boolean,prefs: PrefsUi,actions: Actions,initialTab: Int=0) =
    IonAppContent({ui},dark,prefs,actions,initialTab)

/** Defer live-state reads to the destination; static settings and the navigation bar do not observe telemetry. */
@Composable internal fun IonAppContent(ui: ()->VelaUi,dark: Boolean,prefs: PrefsUi,actions: Actions,initialTab: Int=0,initialRoutes: List<String> = emptyList()) {
    var tab by rememberSaveable {mutableIntStateOf(initialTab)}
    var routes by rememberSaveable {mutableStateOf(initialRoutes)}
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
        // The floating dock hovers over the page: lists scroll behind it and end clear of it.
        val clearance=if(rail || !showBar) 0.dp else DockHeight+DockMargin*2+WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        CompositionLocalProvider(LocalDockClearance provides clearance) {
        Scaffold(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,
            contentWindowInsets=WindowInsets(0)) {inset ->
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
        if(!rail) AnimatedVisibility(showBar,Modifier.align(Alignment.BottomCenter),
            enter=slideInVertically(motion.defaultSpatialSpec()) {it}+fadeIn(motion.defaultEffectsSpec()),
            exit=slideOutVertically(motion.fastSpatialSpec()) {it}+fadeOut(motion.fastEffectsSpec())) {
            Box(Modifier.navigationBarsPadding().padding(DockMargin)) {IonDock(tab,::select)}
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

private val DockHeight=64.dp
private val DockMargin=12.dp

/**
 * Floating dock in the Material 3 Expressive toolbar style: an elevated pill above the content. One tonal indicator
 * slides between destinations on the expressive spatial spring and stretches to carry the selected label; the
 * selected icon is filled, the others outlined. Every item keeps its full name for TalkBack at any font scale.
 */
@Composable private fun IonDock(tab: Int,select: (Int)->Unit) {
    val scheme=MaterialTheme.colorScheme
    val motion=MaterialTheme.motionScheme
    val density=LocalDensity.current
    // Very large fonts: the selected label stays an accessible name only, so four destinations still fit.
    val showLabel=density.fontScale<=1.3f
    val bounds=remember {mutableStateMapOf<Int,Pair<Float,Float>>()}
    val target=bounds[tab]
    val x=remember {Animatable(0f)}
    val w=remember {Animatable(0f)}
    LaunchedEffect(target) {
        val (tx,tw)=target ?: return@LaunchedEffect
        if(w.value==0f) {x.snapTo(tx);w.snapTo(tw);return@LaunchedEffect}
        launch {x.animateTo(tx,motion.defaultSpatialSpec())}
        launch {w.animateTo(tw,motion.defaultSpatialSpec())}
    }
    Surface(Modifier.testTag("navigation-dock").height(DockHeight),shape=CircleShape,color=scheme.surfaceContainer,
        shadowElevation=6.dp,tonalElevation=3.dp) {
        Box(Modifier.padding(8.dp)) {
            if(w.value>0f) Box(Modifier.offset {IntOffset(x.value.roundToInt(),0)}.width(with(density) {w.value.toDp()})
                .fillMaxHeight().clip(CircleShape).background(scheme.primary))
            Row(Modifier.selectableGroup().fillMaxHeight(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                DockLabels.forEachIndexed {index,label ->
                    val selected=tab==index
                    val title=stringResource(label)
                    val tint by animateColorAsState(if(selected) scheme.onPrimary else scheme.onSurfaceVariant,motion.fastEffectsSpec(),label="dock-tint")
                    val interaction=remember {MutableInteractionSource()}
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if(pressed) .88f else 1f,motion.fastSpatialSpec(),label="dock-press")
                    Row(Modifier.fillMaxHeight().widthIn(min=48.dp).clip(CircleShape)
                        .onPlaced {bounds[index]=it.positionInParent().x to it.size.width.toFloat()}
                        .selectable(selected=selected,role=Role.Tab,interactionSource=interaction,indication=ripple(),onClick={select(index)})
                        .semantics {contentDescription=title}
                        .padding(horizontal=if(selected && showLabel) 18.dp else 12.dp),
                        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(selected) DockIcons[index] else DockIconsOutlined[index],null,
                            Modifier.size(24.dp).graphicsLayer {scaleX=scale;scaleY=scale},tint=tint)
                        AnimatedVisibility(selected && showLabel,
                            enter=expandHorizontally(motion.defaultSpatialSpec())+fadeIn(motion.defaultEffectsSpec()),
                            exit=shrinkHorizontally(motion.fastSpatialSpec())+fadeOut(motion.fastEffectsSpec())) {
                            Row {Spacer(Modifier.width(8.dp));Text(title,style=MaterialTheme.typography.labelLargeEmphasized,color=tint,maxLines=1)}
                        }
                    }
                }
            }
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
