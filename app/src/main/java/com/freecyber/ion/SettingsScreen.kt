@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal enum class ControlCategory(val title: Int,val subtitle: Int) {
    LIGHTS(R.string.category_lights,R.string.category_lights_hint),
    SOUND(R.string.category_sound,R.string.category_sound_hint),
    RIDE(R.string.category_ride,R.string.category_ride_hint),
    PEDALS(R.string.category_pedals,R.string.category_pedals_hint),
    SERVICE(R.string.category_service,R.string.category_service_hint)
}

@Composable internal fun SettingsScreen(app: ()->Unit,open: (ControlCategory)->Unit) {
    val ion=ionColors()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {ScreenHeading(stringResource(R.string.nav_settings),stringResource(R.string.app_title),Icons.Rounded.Settings,app)}
        item(key="app",contentType="tiles") {
            ToneTile(stringResource(R.string.app_title),stringResource(R.string.app_hint),Icons.Rounded.Settings,
                IonTone(ion.hero,ion.onHero),minHeight=140.dp,motif=2,onClick=app)
        }
        item(key="ride-pedals",contentType="tiles") {
            TilePair(first={m -> ToneTile(stringResource(R.string.category_ride),stringResource(R.string.category_ride_hint),Icons.Rounded.Route,
                IonTone(ion.accent,ion.onAccent),m,minHeight=180.dp,motif=0) {open(ControlCategory.RIDE)}},
                second={m -> ToneTile(stringResource(R.string.category_pedals),stringResource(R.string.category_pedals_hint),Icons.Rounded.Tune,
                    ion.lilac,m,minHeight=180.dp,motif=1) {open(ControlCategory.PEDALS)}})
        }
        item(key="light-sound",contentType="tiles") {
            TilePair(first={m -> ToneTile(stringResource(R.string.category_lights),stringResource(R.string.category_lights_hint),Icons.Rounded.Lightbulb,
                ion.blue,m,minHeight=160.dp,motif=2) {open(ControlCategory.LIGHTS)}},
                second={m -> ToneTile(stringResource(R.string.category_sound),stringResource(R.string.category_sound_hint),Icons.AutoMirrored.Rounded.VolumeUp,
                    ion.peach,m,minHeight=160.dp,motif=0) {open(ControlCategory.SOUND)}})
        }
        item(key="service",contentType="tiles") {
            ToneTile(stringResource(R.string.category_service),stringResource(R.string.category_service_hint),Icons.Rounded.Build,
                ion.blue,minHeight=140.dp,motif=1) {open(ControlCategory.SERVICE)}
        }
    }
}

/** App-level settings in one place: appearance, ride screen, ride history and the background link. */
@Composable internal fun AppScreen(appearance: ()->Unit,dashboard: ()->Unit,trips: ()->Unit,background: ()->Unit,onBack: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="heading",contentType="heading") {DetailHeading(stringResource(R.string.app_title),stringResource(R.string.app_hint),onBack)}
        item(key="appearance",contentType="link") {ActionRow(stringResource(R.string.appearance),stringResource(R.string.appearance_hint),Icons.Rounded.Palette,appearance)}
        item(key="dashboard",contentType="link") {ActionRow(stringResource(R.string.dash_editor_title),stringResource(R.string.dash_editor_hint),Icons.Rounded.DashboardCustomize,dashboard)}
        item(key="trips",contentType="link") {ActionRow(stringResource(R.string.trips_title),stringResource(R.string.trips_hint),Icons.Rounded.History,trips)}
        item(key="background",contentType="link") {ActionRow(stringResource(R.string.bg_title),stringResource(R.string.bg_hint),Icons.Rounded.Notifications,background)}
    }
}

@Composable internal fun AppearanceScreen(dark: Boolean,followsSystem: Boolean,toggle: ()->Unit,follow: (Boolean)->Unit,onBack: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item(key="heading",contentType="heading") {DetailHeading(stringResource(R.string.appearance),stringResource(R.string.appearance_hint),onBack)}
        item(key="theme-title",contentType="section") {SectionHeader(stringResource(R.string.theme_title))}
        item(key="themes",contentType="choices") {
            Column(Modifier.selectableGroup()) {
                MetricPair(first={m -> ThemeChoice(false,!dark,m) {if(dark) toggle()}},second={m -> ThemeChoice(true,dark,m) {if(!dark) toggle()}})
            }
        }
        item(key="theme-system",contentType="switch") {
            SwitchRow(Icons.Rounded.BrightnessAuto,stringResource(R.string.theme_system),followsSystem,stringResource(R.string.theme_system_hint),true,follow)
        }
        item(key="theme-note",contentType="note") {Banner(stringResource(R.string.theme_note),Icons.Rounded.Palette)}
    }
}

@Composable private fun ThemeChoice(isDark: Boolean,selected: Boolean,modifier: Modifier,choose: ()->Unit) {
    val preview=if(isDark) IonPreviewDark else IonPreviewLight
    Surface(modifier=modifier,shape=RoundedCornerShape(IonShapes.xl),
        color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border=if(selected) BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.fillMaxWidth().selectable(selected=selected,role=Role.RadioButton,onClick=choose).padding(12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(preview.container)
                .clearAndSetSemantics {}) {
                Text("Aa",style=IonType.page,color=preview.content,modifier=Modifier.padding(16.dp))
                Box(Modifier.size(88.dp).align(Alignment.BottomEnd).offset(x=14.dp,y=14.dp).clip(MaterialShapes.Flower.toShape()).background(preview.content))
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(if(isDark) R.string.theme_dark else R.string.theme_light),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                if(selected) Icon(Icons.Rounded.Check,null,Modifier.size(24.dp))
            }
            Text(stringResource(if(isDark) R.string.theme_dark_hint else R.string.theme_light_hint),style=MaterialTheme.typography.bodyMedium)
        }
    }
}
