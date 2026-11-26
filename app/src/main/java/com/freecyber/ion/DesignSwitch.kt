package com.freecyber.ion

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.freecyber.ion.orion.IonTheme as OrionTheme
import com.freecyber.ion.orion.IonAppContent as OrionAppContent
import com.freecyber.ion.orion.ConnectionOnboarding as OrionOnboarding

/**
 * The app carries two complete designs over one model: Orion (the original Ion look, default) and Material
 * (Material 3 Expressive with Material You). Only presentation differs; every write path is shared.
 */
@Composable internal fun DesignTheme(design: UiDesign, dark: Boolean, settings: ThemeSettings, content: @Composable () -> Unit) =
    if (design == UiDesign.ORION) OrionTheme(dark, content) else IonTheme(dark, settings, content)

/**
 * True once the design was switched while the app (not the first-run setup) was showing. The two designs are separate
 * trees, so a switch starts a fresh one; this reopens it on Appearance, where the switch was made.
 */
@Composable internal fun rememberDesignSwitched(design: UiDesign, counting: Boolean): Boolean {
    val state = remember { DesignSwitchState(design) }
    if (state.last != design) { state.moved = counting; state.last = design }
    return state.moved
}
private class DesignSwitchState(var last: UiDesign) { var moved = false }

@Composable internal fun DesignApp(design: UiDesign, ui: () -> VelaUi, dark: Boolean, prefs: PrefsUi, actions: Actions,
    initialTab: Int = 0, switched: Boolean = false) {
    val tab = if (switched) Tabs.SETTINGS else initialTab
    if (design == UiDesign.ORION) OrionAppContent(ui, dark, prefs, actions, tab, if (switched) listOf("app", "appearance") else emptyList())
    else IonAppContent(ui, dark, prefs, actions, tab, if (switched) listOf("appearance") else emptyList())
}

@Composable internal fun DesignOnboarding(
    design: UiDesign,
    bluetoothAllowed: Boolean, notificationsAllowed: Boolean, locationAllowed: Boolean, batteryUnrestricted: Boolean,
    requestBluetooth: () -> Unit, requestNotifications: () -> Unit, requestLocation: () -> Unit, requestBattery: () -> Unit,
    addWheel: () -> Unit, later: () -> Unit, locationServicesEnabled: Boolean = true, requestLocationServices: () -> Unit = {},
) = if (design == UiDesign.ORION) OrionOnboarding(bluetoothAllowed, notificationsAllowed, locationAllowed, batteryUnrestricted,
        requestBluetooth, requestNotifications, requestLocation, requestBattery, addWheel, later, locationServicesEnabled, requestLocationServices)
    else ConnectionOnboarding(bluetoothAllowed, notificationsAllowed, locationAllowed, batteryUnrestricted,
        requestBluetooth, requestNotifications, requestLocation, requestBattery, addWheel, later, locationServicesEnabled, requestLocationServices)

/**
 * First page of the first-run setup: the two designs side by side. Picking one restyles this very page at once,
 * so the rider sees the real thing before continuing to the permission steps.
 */
@Composable internal fun StyleStep(design: UiDesign, dark: Boolean, theme: ThemeSettings, choose: (UiDesign) -> Unit, done: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onBackground) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.setup_style_title), style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.setup_style_hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    DesignPicker(design, dark, theme, choose, previewHeight = 220.dp)
                }
                Button(onClick = done, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp).testTag("style-continue")) {
                    Text(stringResource(R.string.setup_style_continue), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Two cards, each a miniature drawn in its own design's theme, so the choice is seen before it is made. */
@Composable internal fun DesignPicker(current: UiDesign, dark: Boolean, theme: ThemeSettings, choose: (UiDesign) -> Unit,
    previewHeight: androidx.compose.ui.unit.Dp = 132.dp) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UiDesign.entries.forEach { d -> DesignCard(d, d == current, dark, theme, previewHeight, Modifier.weight(1f)) { if (d != current) choose(d) } }
    }
}

@Composable private fun DesignCard(design: UiDesign, selected: Boolean, dark: Boolean, theme: ThemeSettings,
    previewHeight: androidx.compose.ui.unit.Dp, modifier: Modifier, choose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.secondaryContainer else scheme.surfaceContainer, label = "design-card")
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = container,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null) {
        Column(Modifier.selectable(selected, role = Role.RadioButton, onClick = choose).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(20.dp)).clearAndSetSemantics {}) {
                if (design == UiDesign.ORION) OrionTheme(dark) { Miniature(orion = true) }
                else IonTheme(dark, theme) { Miniature(orion = false) }
            }
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(design.title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(stringResource(design.hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (selected) Box(Modifier.size(24.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = scheme.onPrimary)
                }
            }
        }
    }
}

/** A ride screen in miniature: hero speed, two tiles and the dock, in the colours and corners of one design. */
@Composable private fun Miniature(orion: Boolean) {
    val c = MaterialTheme.colorScheme
    val ion = if (orion) com.freecyber.ion.orion.ionColors() else null
    val hero = ion?.hero ?: c.primaryContainer
    val onHero = ion?.onHero ?: c.onPrimaryContainer
    val first = ion?.accent ?: c.tertiaryContainer
    val second = ion?.blue?.container ?: c.secondaryContainer
    Column(Modifier.fillMaxSize().background(c.background).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().weight(1.3f).clip(RoundedCornerShape(16.dp)).background(hero)
            .padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.BottomStart) {
            Text(23.4.fmt(1), style = MaterialTheme.typography.titleLarge, color = onHero, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(first))
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(second))
        }
        Row(Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(if (orion) c.surfaceContainerHigh else c.surfaceContainer)
            .padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(28.dp, 12.dp).clip(CircleShape).background(c.primary))
            repeat(3) { Box(Modifier.size(12.dp).clip(CircleShape).background(c.onSurfaceVariant.copy(alpha = .35f))) }
        }
    }
}
