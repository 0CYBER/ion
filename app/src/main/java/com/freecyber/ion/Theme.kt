package com.freecyber.ion

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/*
 * Ion's design tokens, built on the Material 3 Expressive system. See docs/design-system.md.
 *
 * Colour: Material You. On Android 12+ the scheme comes from the wallpaper (dynamic colour) unless the rider
 * turns it off; otherwise a Tonal Spot scheme is generated from one of the [IonPalette] seeds, so every role
 * (primary, containers, the seven surface containers, outline…) keeps Material's tone relationships and contrast.
 * Screens use MaterialTheme.colorScheme roles only; [IonColors] adds the two semantic roles Material lacks.
 */

/** Seed colours offered when wallpaper colours are off or unavailable (Android 8–11). VIOLET is the Ion brand. */
enum class IonPalette(val seed: Color, val title: Int) {
    VIOLET(Color(0xFF6937D4), R.string.palette_violet),
    BLUE(Color(0xFF1F5FBF), R.string.palette_blue),
    TEAL(Color(0xFF00796B), R.string.palette_teal),
    GREEN(Color(0xFF4C7A1E), R.string.palette_green),
    AMBER(Color(0xFFB0660B), R.string.palette_amber),
    ROSE(Color(0xFFB02A5B), R.string.palette_rose);
    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: VIOLET }
}

/** Which of the two designs draws the app: Orion (the original Ion look, default) or Material 3 Expressive. */
enum class UiDesign(val title: Int, val hint: Int) {
    ORION(R.string.design_orion, R.string.design_orion_hint),
    MATERIAL(R.string.design_material, R.string.design_material_hint);
    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ORION }
}

/** How the app picks light or dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Everything the theme needs from preferences. */
@Immutable data class ThemeSettings(val mode: ThemeMode = ThemeMode.SYSTEM, val dynamic: Boolean = true,
    val palette: IonPalette = IonPalette.VIOLET)

internal val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Immutable data class IonTone(val container: Color, val content: Color)

/**
 * Roles Material 3 has no slot for. Warning is a fixed amber, harmonised in tone with the scheme (tone 90/10 in light,
 * 30/90 in dark), because a battery or overheating caution must not change hue with the wallpaper.
 */
@Immutable data class IonColors(val warning: Color, val onWarning: Color, val success: Color, val onSuccess: Color)
private val LightIon = IonColors(Color(0xFFFFDEA6), Color(0xFF271900), Color(0xFFB7F1B9), Color(0xFF00210B))
private val DarkIon = IonColors(Color(0xFF5D4200), Color(0xFFFFDEA6), Color(0xFF15512A), Color(0xFFB7F1B9))
val LocalIonColors = staticCompositionLocalOf { LightIon }

/** Material shape scale (extra small 4 … extra extra large 48) plus the few component shapes Ion reuses. */
object IonShapes {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val li = 20.dp
    val xl = 28.dp; val xli = 32.dp; val xxl = 48.dp
    /** Inner corner of a segmented list: a group reads as one card split by 2 dp seams. */
    val seam = 4.dp
    /** Outer corner of a segmented group. */
    val group = 24.dp
    val card = RoundedCornerShape(xl)
    val hero = RoundedCornerShape(xli)
}

private fun variableFamily(resource: Int) = FontFamily(listOf(400, 500, 600, 700, 800).map { weight ->
    Font(resource, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))) })
/** Onest: a geometric grotesque close to Google Sans, with full Cyrillic. Shipped locally (third-party/Onest-OFL.txt). */
val IonSans = variableFamily(R.font.onest_variable)
val IonMono = variableFamily(R.font.jetbrains_mono_variable)

/** Long Russian words ("Автопереподключение") must hyphenate instead of breaking at an arbitrary letter on large fonts. */
private fun TextStyle.ion() = copy(fontFamily = IonSans, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

/**
 * The Material 3 type scale, unchanged in size and line height, set in Onest. The emphasized styles (Expressive)
 * carry headings, selected states and key numbers; the baseline styles carry everything else.
 */
private val IonTypography = Typography().let { t -> t.copy(
    displayLarge = t.displayLarge.ion(), displayMedium = t.displayMedium.ion(), displaySmall = t.displaySmall.ion(),
    headlineLarge = t.headlineLarge.ion(), headlineMedium = t.headlineMedium.ion(), headlineSmall = t.headlineSmall.ion(),
    titleLarge = t.titleLarge.ion(), titleMedium = t.titleMedium.ion(), titleSmall = t.titleSmall.ion(),
    bodyLarge = t.bodyLarge.ion(), bodyMedium = t.bodyMedium.ion(), bodySmall = t.bodySmall.ion(),
    labelLarge = t.labelLarge.ion(), labelMedium = t.labelMedium.ion(), labelSmall = t.labelSmall.ion(),
    displayLargeEmphasized = t.displayLargeEmphasized.ion(), displayMediumEmphasized = t.displayMediumEmphasized.ion(),
    displaySmallEmphasized = t.displaySmallEmphasized.ion(), headlineLargeEmphasized = t.headlineLargeEmphasized.ion(),
    headlineMediumEmphasized = t.headlineMediumEmphasized.ion(), headlineSmallEmphasized = t.headlineSmallEmphasized.ion(),
    titleLargeEmphasized = t.titleLargeEmphasized.ion(), titleMediumEmphasized = t.titleMediumEmphasized.ion(),
    titleSmallEmphasized = t.titleSmallEmphasized.ion(), bodyLargeEmphasized = t.bodyLargeEmphasized.ion(),
    bodyMediumEmphasized = t.bodyMediumEmphasized.ion(), bodySmallEmphasized = t.bodySmallEmphasized.ion(),
    labelLargeEmphasized = t.labelLargeEmphasized.ion(), labelMediumEmphasized = t.labelMediumEmphasized.ion(),
    labelSmallEmphasized = t.labelSmallEmphasized.ion()) }

/** Readings: tabular figures so digits do not jitter while they change. Sizes follow the display/headline scale. */
object IonType {
    val number = TextStyle(fontFamily = IonSans, fontFeatureSettings = "tnum")
    /** Headline-size reading on metric cards (headlineLarge emphasized). */
    val figure = number.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    /** The speed: beyond display large, as Expressive allows for one hero number per screen. */
    val speed = number.copy(fontSize = 96.sp, lineHeight = 100.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp)
    val speedCompact = speed.copy(fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-1).sp)
}

private val IonShapeScale = Shapes(
    extraSmall = RoundedCornerShape(IonShapes.xs), small = RoundedCornerShape(IonShapes.s),
    medium = RoundedCornerShape(IonShapes.m), large = RoundedCornerShape(IonShapes.l),
    largeIncreased = RoundedCornerShape(IonShapes.li), extraLarge = RoundedCornerShape(IonShapes.xl),
    extraLargeIncreased = RoundedCornerShape(IonShapes.xli), extraExtraLarge = RoundedCornerShape(IonShapes.xxl))

/** A seed scheme, cached per seed and brightness: generating one walks the whole HCT tonal palette. */
private val seedSchemes = HashMap<Pair<Color, Boolean>, ColorScheme>()
internal fun seedScheme(seed: Color, dark: Boolean): ColorScheme = synchronized(seedSchemes) {
    seedSchemes.getOrPut(seed to dark) { dynamicColorScheme(seedColor = seed, isDark = dark, style = PaletteStyle.TonalSpot) }
}

@Composable internal fun colorSchemeFor(dark: Boolean, settings: ThemeSettings): ColorScheme {
    val context = LocalContext.current
    return if (settings.dynamic && dynamicColorAvailable) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else seedScheme(settings.palette.seed, dark)
}

/**
 * [dynamic] defaults to off so screenshots and tests render the same brand scheme on every machine;
 * MainActivity passes the rider's choice.
 */
@Composable internal fun IonTheme(dark: Boolean, settings: ThemeSettings = ThemeSettings(dynamic = false), content: @Composable () -> Unit) {
    val scheme = colorSchemeFor(dark, settings)
    CompositionLocalProvider(LocalIonColors provides if (dark) DarkIon else LightIon) {
        MaterialExpressiveTheme(colorScheme = scheme, typography = IonTypography, motionScheme = MotionScheme.expressive(),
            shapes = IonShapeScale) {
            // Text drawn straight on the background (outside any Surface) must not fall back to black.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
        }
    }
}

@Composable internal fun ionColors(): IonColors = LocalIonColors.current
@Composable internal fun systemDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true
}
@Composable internal fun systemDark(preference: Boolean?): Boolean = preference ?: isSystemInDarkTheme()
