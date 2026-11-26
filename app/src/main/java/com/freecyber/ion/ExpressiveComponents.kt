@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.freecyber.ion

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/*
 * Page chrome and list building blocks shared by every screen.
 *
 * A destination is an [IonPage]: a flexible top app bar that collapses as the list scrolls (large on the four
 * top-level tabs, medium with a back button on every screen below them), over one lazy column with 16 dp margins.
 * Lists use the Expressive segmented style: a group is one rounded block split by 2 dp seams ([GroupColumn]),
 * each row a [ListRow] with a [ShapeBadge] icon. Cards and notices sit between groups at the same margins.
 */

/** Horizontal page margin (compact window), list gap and the gap between groups. */
internal object IonSpacing { val page = 16.dp; val item = 12.dp; val seam = 2.dp }

/** Space the floating chrome over the content needs at the bottom (0 when the navigation bar pushes content up). */
internal val LocalDockClearance = staticCompositionLocalOf { 0.dp }

internal val PagePadding: PaddingValues
    @Composable get() = PaddingValues(start = IonSpacing.page, end = IonSpacing.page, top = 8.dp, bottom = 24.dp + LocalDockClearance.current)

@Composable private fun BackButton(onBack: () -> Unit) {
    // A filled tonal circle reads as a button on any surface colour, also before the bar tints on scroll.
    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
    }
}

/**
 * The top app bar of a page. [large] for top-level destinations (Large flexible), otherwise Medium flexible with a
 * back button. Both collapse into a small bar as the content scrolls; the bar tints to surface container once
 * content passes beneath it.
 */
@Composable internal fun IonTopBar(title: String, subtitle: String?, onBack: (() -> Unit)?, large: Boolean,
    scroll: TopAppBarScrollBehavior, actions: @Composable RowScope.() -> Unit, compact: Boolean = false) {
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val titleText: @Composable () -> Unit = {
        Text(title, maxLines = if (large) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
    }
    val subtitleText: (@Composable () -> Unit)? = subtitle?.takeIf { it.isNotBlank() }?.let {
        { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
    val nav: @Composable () -> Unit = { onBack?.let { BackButton(it) } }
    if (compact) TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1,
            modifier = Modifier.semantics { heading() }) }, navigationIcon = nav, actions = actions,
        colors = colors, windowInsets = insets, scrollBehavior = scroll)
    else if (large) LargeFlexibleTopAppBar(title = titleText, subtitle = subtitleText, navigationIcon = nav, actions = actions,
        colors = colors, windowInsets = insets, scrollBehavior = scroll)
    else MediumFlexibleTopAppBar(title = titleText, subtitle = subtitleText, navigationIcon = nav, actions = actions,
        colors = colors, windowInsets = insets, scrollBehavior = scroll)
}

/**
 * One destination: flexible top app bar + lazy content. The list keeps 16 dp page margins and a 12 dp rhythm;
 * [content] adds items exactly as on a plain LazyColumn.
 */
@Composable internal fun IonPage(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    large: Boolean = onBack == null, state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {}, spacing: Dp = IonSpacing.item,
    contentPadding: PaddingValues = PagePadding, compact: Boolean = false, listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit) {
    val scroll = if (compact) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        IonTopBar(title, subtitle, onBack, large, scroll, actions, compact)
        LazyColumn(listModifier.weight(1f).fillMaxWidth(), state = state, contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** Non-lazy variant for pages that lay out their own scrolling content (maps, editors). */
@Composable internal fun IonPageFrame(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        val colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
        TopAppBar(title = { Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } },
            navigationIcon = { onBack?.let { BackButton(it) } }, actions = actions, colors = colors,
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal), scrollBehavior = scroll)
        content()
    }
}

/** List subheader: primary, emphasized, aligned with the text inside list rows. */
@Composable internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp).semantics { heading() })
}

/** Rows inside a [GroupColumn] draw square seams; a row on its own is fully rounded. */
internal val LocalInGroup = compositionLocalOf { false }

@Composable internal fun rowShape(): Shape = if (LocalInGroup.current) RoundedCornerShape(IonShapes.seam) else RoundedCornerShape(IonShapes.group)

/** Expressive segmented group: one rounded block, 2 dp seams, no borders. */
@Composable internal fun GroupColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalInGroup provides true) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(IonShapes.group)),
            verticalArrangement = Arrangement.spacedBy(IonSpacing.seam), content = content)
    }
}

/** Icon inside a Material shape: the leading element of list rows and the mark of a category. */
@Composable internal fun ShapeBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer, contentDescription: String? = null) {
    Surface(modifier.size(size), shape = polygon.toShape(), color = containerColor, contentColor = contentColor) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(size * .5f)) }
    }
}

/** Colour pairs for category badges: the three accent families of the scheme, never fixed hues. */
internal enum class BadgeTone { PRIMARY, SECONDARY, TERTIARY, ERROR, NEUTRAL }
@Composable internal fun BadgeTone.colors(): IonTone = MaterialTheme.colorScheme.let { c ->
    when (this) {
        BadgeTone.PRIMARY -> IonTone(c.primaryContainer, c.onPrimaryContainer)
        BadgeTone.SECONDARY -> IonTone(c.secondaryContainer, c.onSecondaryContainer)
        BadgeTone.TERTIARY -> IonTone(c.tertiaryContainer, c.onTertiaryContainer)
        BadgeTone.ERROR -> IonTone(c.errorContainer, c.onErrorContainer)
        BadgeTone.NEUTRAL -> IonTone(c.surfaceContainerHighest, c.onSurfaceVariant)
    }
}

/**
 * Two-line list row: badge, headline (title medium emphasized), supporting text, trailing slot.
 * [onClick] makes the whole row one target; min height 72 dp as for two-line Material lists.
 */
@Composable internal fun ListRow(title: String, modifier: Modifier = Modifier, supporting: String? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainer, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
        }
    }
    if (onClick != null) Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), shape = rowShape(), color = container, content = body)
    else Surface(modifier.fillMaxWidth(), shape = rowShape(), color = container, content = body)
}

/** A row that opens another page: badge, two lines, chevron. */
@Composable internal fun ActionRow(title: String, subtitle: String, icon: ImageVector, tone: BadgeTone = BadgeTone.SECONDARY,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided, onClick: () -> Unit) {
    val c = tone.colors()
    ListRow(title, supporting = subtitle.takeIf { it.isNotBlank() },
        leading = { ShapeBadge(icon, polygon = polygon, containerColor = c.container, contentColor = c.content) },
        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick)
}

/** Kept for call sites that open a category from a hero-style entry; renders as a list row in the new system. */
@Composable internal fun ToneTile(title: String, subtitle: String, icon: ImageVector, tone: IonTone, modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp, motif: Int = 0, ornament: Int = motif, onClick: () -> Unit) {
    ListRow(title, modifier.heightIn(min = minHeight), supporting = subtitle,
        leading = { ShapeBadge(icon, containerColor = tone.container, contentColor = tone.content) }, onClick = onClick)
}

/** Emphasis levels of a notice card. */
internal enum class Notice { INFO, WARNING, ERROR }

/**
 * Inline notice (Material has no compose banner): a tonal card with icon, used for connection problems, unconfirmed
 * commands and hints. Colour is always paired with an icon and text.
 */
@Composable internal fun Banner(text: String, icon: ImageVector = Icons.Rounded.Info, error: Boolean = false, warning: Boolean = false,
    modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val ion = ionColors()
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        error -> scheme.errorContainer to scheme.onErrorContainer
        warning -> ion.warning to ion.onWarning
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    val container by animateColorAsState(bg, MaterialTheme.motionScheme.fastEffectsSpec(), label = "notice")
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(IonShapes.li), color = container, contentColor = fg) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (action != null) 6.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, null, Modifier.size(24.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            action?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { it() } }
        }
    }
}

/** A labelled value inside a summary card: label small, number in tabular title. One TalkBack stop. */
@Composable internal fun StatFigure(label: String, value: String, unit: String, modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLargeEmphasized) {
    val secondary = LocalContentColor.current.copy(alpha = .74f)
    val noData = stringResource(R.string.no_data)
    val spoken = if (value == "—") "$label, $noData" else "$label, $value $unit"
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = valueStyle.copy(fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                    maxFontSize = valueStyle.fontSize), modifier = Modifier.weight(1f, fill = false))
            if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

