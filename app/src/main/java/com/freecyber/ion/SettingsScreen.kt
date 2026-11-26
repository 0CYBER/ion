@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

internal enum class ControlCategory(val title: Int,val subtitle: Int) {
    LIGHTS(R.string.category_lights,R.string.category_lights_hint),
    SOUND(R.string.category_sound,R.string.category_sound_hint),
    RIDE(R.string.category_ride,R.string.category_ride_hint),
    PEDALS(R.string.category_pedals,R.string.category_pedals_hint),
    SERVICE(R.string.category_service,R.string.category_service_hint)
}

/** How each wheel category is marked on the overview: icon, accent family and badge shape. */
private class CategoryMark(val icon: ImageVector,val tone: BadgeTone,val polygon: RoundedPolygon)
private val ControlCategory.mark: CategoryMark get()=when(this) {
    ControlCategory.RIDE -> CategoryMark(Icons.Rounded.Route,BadgeTone.PRIMARY,MaterialShapes.Cookie9Sided)
    ControlCategory.PEDALS -> CategoryMark(Icons.Rounded.Tune,BadgeTone.TERTIARY,MaterialShapes.Clover4Leaf)
    ControlCategory.LIGHTS -> CategoryMark(Icons.Rounded.Lightbulb,BadgeTone.SECONDARY,MaterialShapes.Sunny)
    ControlCategory.SOUND -> CategoryMark(Icons.AutoMirrored.Rounded.VolumeUp,BadgeTone.PRIMARY,MaterialShapes.Cookie6Sided)
    ControlCategory.SERVICE -> CategoryMark(Icons.Rounded.Build,BadgeTone.NEUTRAL,MaterialShapes.Pill)
}
internal val ControlCategory.icon: ImageVector get()=mark.icon

/** Links from the settings overview to app-level pages. */
internal class SettingsLinks(val app: ()->Unit={},val appearance: ()->Unit={},val background: ()->Unit={},val updates: ()->Unit={})

/**
 * Settings overview, laid out like the system settings: the wheel's categories in one segmented group,
 * the app's own pages in a second. Order follows how often a rider changes them.
 */
@Composable internal fun SettingsScreen(links: SettingsLinks,open: (ControlCategory)->Unit) {
    IonPage(stringResource(R.string.nav_settings)) {
        item(key="wheel-title",contentType="section") {SectionHeader(stringResource(R.string.settings_section_wheel))}
        item(key="wheel",contentType="group") {
            GroupColumn {
                listOf(ControlCategory.RIDE,ControlCategory.PEDALS,ControlCategory.LIGHTS,ControlCategory.SOUND,ControlCategory.SERVICE).forEach {c ->
                    val m=c.mark
                    ActionRow(stringResource(c.title),stringResource(c.subtitle),m.icon,m.tone,m.polygon) {open(c)}
                }
            }
        }
        item(key="app-title",contentType="section") {SectionHeader(stringResource(R.string.settings_section_app))}
        item(key="app",contentType="group") {
            GroupColumn {
                ActionRow(stringResource(R.string.appearance),stringResource(R.string.appearance_hint),Icons.Rounded.Palette,BadgeTone.TERTIARY,MaterialShapes.Flower,links.appearance)
                ActionRow(stringResource(R.string.bg_title),stringResource(R.string.bg_hint),Icons.Rounded.Notifications,BadgeTone.SECONDARY,MaterialShapes.SoftBurst,links.background)
                ActionRow(stringResource(R.string.updates_title),stringResource(R.string.updates_hint),Icons.Rounded.SystemUpdate,BadgeTone.PRIMARY,MaterialShapes.Arch,links.updates)
            }
        }
    }
}

/** App-level settings: appearance, background link and updates (kept as a page for deep links and older entry points). */
@Composable internal fun AppScreen(appearance: ()->Unit,background: ()->Unit,onBack: ()->Unit,updates: ()->Unit={}) {
    IonPage(stringResource(R.string.app_title),stringResource(R.string.app_hint),onBack) {
        item(key="links",contentType="group") {
            GroupColumn {
                ActionRow(stringResource(R.string.appearance),stringResource(R.string.appearance_hint),Icons.Rounded.Palette,BadgeTone.TERTIARY,MaterialShapes.Flower,appearance)
                ActionRow(stringResource(R.string.bg_title),stringResource(R.string.bg_hint),Icons.Rounded.Notifications,BadgeTone.SECONDARY,MaterialShapes.SoftBurst,background)
                ActionRow(stringResource(R.string.updates_title),stringResource(R.string.updates_hint),Icons.Rounded.SystemUpdate,BadgeTone.PRIMARY,MaterialShapes.Arch,updates)
            }
        }
    }
}

/**
 * Appearance: a live preview of the scheme, the light/dark mode as a connected button group, and Material You —
 * wallpaper colours on Android 12+, or one of the seed palettes, each generated into a full tonal scheme.
 */
@Composable internal fun AppearanceScreen(dark: Boolean,theme: ThemeSettings,actions: Actions,onBack: ()->Unit) {
    IonPage(stringResource(R.string.appearance),stringResource(R.string.appearance_hint),onBack) {
        item(key="design-title",contentType="section") {SectionHeader(stringResource(R.string.design_title))}
        item(key="design",contentType="design") {DesignPicker(UiDesign.MATERIAL,dark,theme,actions.design)}
        item(key="preview",contentType="preview") {SchemePreview()}
        item(key="theme-title",contentType="section") {SectionHeader(stringResource(R.string.theme_title))}
        item(key="themes",contentType="choices") {ThemeModeGroup(theme.mode,actions.themeMode)}
        item(key="color-title",contentType="section") {SectionHeader(stringResource(R.string.color_title))}
        item(key="dynamic",contentType="group") {
            GroupColumn {
                SwitchRow(Icons.Rounded.Wallpaper,stringResource(R.string.dynamic_color),theme.dynamic && dynamicColorAvailable,
                    stringResource(if(dynamicColorAvailable) R.string.dynamic_color_hint else R.string.dynamic_color_unavailable),
                    dynamicColorAvailable,actions.dynamicColor)
                PalettePicker(theme.palette,dark,enabled=!(theme.dynamic && dynamicColorAvailable),choose=actions.palette)
            }
        }
        item(key="theme-note",contentType="note") {Banner(stringResource(R.string.theme_note),Icons.Rounded.Palette)}
    }
}

/** System / Light / Dark as connected toggle buttons (Expressive button group): the selected one morphs to a pill. */
@Composable private fun ThemeModeGroup(mode: ThemeMode,choose: (ThemeMode)->Unit) {
    val options=listOf(Triple(ThemeMode.SYSTEM,R.string.theme_mode_system,Icons.Rounded.BrightnessAuto),
        Triple(ThemeMode.LIGHT,R.string.theme_light,Icons.Rounded.LightMode),Triple(ThemeMode.DARK,R.string.theme_dark,Icons.Rounded.DarkMode))
    Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed {index,(value,label,icon) ->
            val checked=mode==value
            ToggleButton(checked=checked,onCheckedChange={choose(value)},
                shapes=when(index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier=Modifier.weight(1f).heightIn(min=56.dp).semantics {role=Role.RadioButton},
                contentPadding=PaddingValues(horizontal=8.dp)) {
                Icon(if(checked) Icons.Rounded.Check else icon,null,Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(label),maxLines=1,style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Seed swatches, each showing the primary / secondary / tertiary of the scheme it would generate. */
@Composable private fun PalettePicker(selected: IonPalette,dark: Boolean,enabled: Boolean,choose: (IonPalette)->Unit) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=rowShape()) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.palette_title),style=MaterialTheme.typography.titleMediumEmphasized)
                Text(stringResource(R.string.palette_hint),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Two even rows of three: the grid stays aligned at any width instead of leaving one swatch alone.
            Column(Modifier.fillMaxWidth().selectableGroup().alpha(if(enabled) 1f else .38f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                IonPalette.entries.chunked(3).forEach {row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach {p -> Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {Swatch(p,dark,p==selected,enabled) {choose(p)}}}
                    }
                }
            }
        }
    }
}

@Composable private fun Swatch(palette: IonPalette,dark: Boolean,selected: Boolean,enabled: Boolean,choose: ()->Unit) {
    val scheme=remember(palette,dark) {seedScheme(palette.seed,dark)}
    val morph=remember {Morph(MaterialShapes.Circle,MaterialShapes.Cookie9Sided)}
    val progress by animateFloatAsState(if(selected) 1f else 0f,MaterialTheme.motionScheme.defaultSpatialSpec(),label="swatch")
    val ring by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else Color.Transparent,MaterialTheme.motionScheme.fastEffectsSpec(),label="swatch-ring")
    val name=stringResource(palette.title)
    Column(Modifier.width(80.dp).clip(RoundedCornerShape(IonShapes.l)).selectable(selected,enabled=enabled,role=Role.RadioButton,onClick=choose).padding(vertical=4.dp)
        .semantics(mergeDescendants=true) {contentDescription=name},horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(ring).padding(3.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(3.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(MorphShape(morph,progress))) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(scheme.primary))
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(scheme.secondaryContainer))
                        Box(Modifier.weight(1f).fillMaxHeight().background(scheme.tertiaryContainer))
                    }
                }
                if(selected) Box(Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape).background(scheme.primaryContainer),
                    contentAlignment=Alignment.Center) {
                    Icon(Icons.Rounded.Check,null,Modifier.size(18.dp),tint=scheme.onPrimaryContainer)
                }
            }
        }
        Text(name,style=MaterialTheme.typography.labelMedium,maxLines=1,
            color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A miniature of the ride screen drawn with the live scheme, so a palette change is seen before leaving the page. */
@Composable private fun SchemePreview() {
    val c=MaterialTheme.colorScheme
    Surface(color=c.surfaceContainerLow,shape=IonShapes.card,modifier=Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(color=c.primaryContainer,contentColor=c.onPrimaryContainer,shape=RoundedCornerShape(IonShapes.li)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.Bottom) {
                    Text(23.4.fmt(1),style=MaterialTheme.typography.displayMediumEmphasized.copy(fontFeatureSettings="tnum"))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.speed_unit),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=8.dp))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(c.primary),contentAlignment=Alignment.Center) {
                        Icon(Icons.Rounded.Speed,null,Modifier.size(20.dp),tint=c.onPrimary)
                    }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PreviewTile("78%",Icons.Rounded.BatteryFull,c.tertiaryContainer,c.onTertiaryContainer,Modifier.weight(1f))
                PreviewTile("42%",Icons.Rounded.Equalizer,c.secondaryContainer,c.onSecondaryContainer,Modifier.weight(1f))
            }
            // The floating dock in miniature: a raised pill, the selected destination filled with primary.
            Row(Modifier.align(Alignment.CenterHorizontally).padding(top=4.dp).clip(CircleShape).background(c.surfaceContainerHighest).padding(4.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(Icons.Rounded.Speed,Icons.Rounded.History,Icons.Rounded.Settings,Icons.Rounded.TripOrigin).forEachIndexed {i,icon ->
                    Box(Modifier.width(if(i==0) 56.dp else 36.dp).height(32.dp).clip(CircleShape).background(if(i==0) c.primary else Color.Transparent),
                        contentAlignment=Alignment.Center) {
                        Icon(icon,null,Modifier.size(20.dp),tint=if(i==0) c.onPrimary else c.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable private fun PreviewTile(value: String,icon: ImageVector,bg: Color,fg: Color,modifier: Modifier) {
    Surface(modifier,color=bg,contentColor=fg,shape=RoundedCornerShape(IonShapes.li)) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(icon,null,Modifier.size(20.dp))
            Text(value,style=MaterialTheme.typography.titleLargeEmphasized.copy(fontFeatureSettings="tnum"))
        }
    }
}
