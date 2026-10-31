package dev.vela.v12

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Design-system home: the ONLY file allowed to name raw colors, shape values and scheme roles.
// Screens read semantic tokens (MaterialTheme.colorScheme / LocalIonColors) — never hex literals.
private val Lilac = Color(0xFFC9BDFF)
private val Acid = Color(0xFFD7F5A5)
private val Ink = Color(0xFF221C35)

/** Extended slots beyond the Material scheme (Lumen-style): hero surface, live accent, warning pair. */
@Immutable
data class IonColors(val hero: Color, val onHero: Color, val accent: Color, val onAccent: Color,
    val warning: Color, val onWarning: Color)
val LocalIonColors = staticCompositionLocalOf {
    IonColors(Lilac, Ink, Acid, Color(0xFF2A3A14), Color(0xFF46371A), Color(0xFFFFDF9E))
}

/** One radius ladder for the whole app: 8/12/14/18/20/28/32. */
object IonShapes {
    val xs = 8.dp; val s = 12.dp; val m = 14.dp; val row = 18.dp
    val l = 20.dp; val xl = 28.dp; val dock = 32.dp
}

@Composable internal fun VelaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors: ColorScheme = if(dark) darkColorScheme(primary=Lilac,onPrimary=Ink,primaryContainer=Color(0xFF3A3156),onPrimaryContainer=Color(0xFFEAE2FF),
        tertiary=Acid,onTertiary=Color(0xFF263519),secondaryContainer=Color(0xFF302D3B),onSecondaryContainer=Color(0xFFE7E0F2),
        background=Color(0xFF101014),onBackground=Color(0xFFF4F0FA),surface=Color(0xFF15141A),onSurface=Color(0xFFF4F0FA),
        surfaceContainerLow=Color(0xFF19181F),surfaceContainer=Color(0xFF222029),surfaceContainerHigh=Color(0xFF2B2834),
        onSurfaceVariant=Color(0xFFC7C0D0),outline=Color(0xFF938A9F),outlineVariant=Color(0xFF45404F),
        errorContainer=Color(0xFF4B232B),onErrorContainer=Color(0xFFFFD9DE))
    else lightColorScheme(primary=Color(0xFF66509F),onPrimary=Color.White,primaryContainer=Color(0xFFE8DEFF),onPrimaryContainer=Ink,
        tertiary=Color(0xFF4C6328),onTertiary=Color.White,secondaryContainer=Color(0xFFECE5F4),onSecondaryContainer=Ink,
        background=Color(0xFFFAF8FE),onBackground=Ink,surface=Color(0xFFFAF8FE),onSurface=Ink,
        surfaceContainerLow=Color(0xFFF5F1FA),surfaceContainer=Color(0xFFF0EBF6),surfaceContainerHigh=Color(0xFFE7E0F0),onSurfaceVariant=Color(0xFF554D61))
    val ion = if(dark) IonColors(hero=Lilac,onHero=Ink,accent=Acid,onAccent=Color(0xFF263519),
        warning=Color(0xFF443616),onWarning=Color(0xFFFFDF9E))
        else IonColors(hero=Color(0xFFE8DEFF),onHero=Ink,accent=Acid,onAccent=Color(0xFF2A3A14),
        warning=Color(0xFFF3E3B0),onWarning=Color(0xFF4A3A0A))
    CompositionLocalProvider(LocalIonColors provides ion) {
        MaterialExpressiveTheme(colorScheme=colors,motionScheme=MotionScheme.expressive(),
            shapes=Shapes(extraSmall=RoundedCornerShape(IonShapes.xs),small=RoundedCornerShape(IonShapes.s),
                medium=RoundedCornerShape(IonShapes.m),large=RoundedCornerShape(IonShapes.l),extraLarge=RoundedCornerShape(IonShapes.xl)),
            content=content)
    }
}

/** Semantic accessor so feature code never touches LocalIonColors directly. */
@Composable internal fun ionColors(): IonColors = LocalIonColors.current
@Composable internal fun systemDark(preference: Boolean?): Boolean = preference ?: isSystemInDarkTheme()
