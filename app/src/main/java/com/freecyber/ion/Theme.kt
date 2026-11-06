package com.freecyber.ion

import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Single source of color, shape and typography roles. See docs/design-system.md.
private val Ink=Color(0xFF281C37)
private val Lilac=Color(0xFFD7B9FF)
private val Lime=Color(0xFFE1F5A2)
internal val IonPreviewLight=IonTone(Color(0xFFFAF7F2),Color(0xFF6937D4))
internal val IonPreviewDark=IonTone(Color(0xFF09080D),Color(0xFFD7B9FF))
@Immutable data class IonTone(val container: Color,val content: Color)
@Immutable
data class IonColors(val hero: Color,val onHero: Color,val accent: Color,val onAccent: Color,
    val warning: Color,val onWarning: Color,val blue: IonTone,val peach: IonTone,val lilac: IonTone)
private val DarkScheme=darkColorScheme(
    primary=Lilac,onPrimary=Ink,primaryContainer=Color(0xFF493B68),onPrimaryContainer=Color(0xFFEBDDFF),
    secondary=Color(0xFFCCC2DB),onSecondary=Color(0xFF332D40),
    secondaryContainer=Color(0xFF332D40),onSecondaryContainer=Color(0xFFEAE1F5),
    tertiary=Lime,onTertiary=Color(0xFF293B15),tertiaryContainer=Color(0xFF354824),onTertiaryContainer=Lime,
    background=Color(0xFF09080D),onBackground=Color(0xFFF0EBF3),surface=Color(0xFF09080D),onSurface=Color(0xFFF0EBF3),
    onSurfaceVariant=Color(0xFFCAC3D2),surfaceContainerLowest=Color(0xFF09080D),surfaceContainerLow=Color(0xFF17131F),
    surfaceContainer=Color(0xFF242129),surfaceContainerHigh=Color(0xFF2F2B35),surfaceContainerHighest=Color(0xFF3A3541),
    outline=Color(0xFF958C9F),outlineVariant=Color(0xFF4A4453),
    error=Color(0xFFFFB4AB),onError=Color(0xFF690005),errorContainer=Color(0xFF55262A),onErrorContainer=Color(0xFFFFDAD6))
private val LightScheme=lightColorScheme(
    primary=Color(0xFF6937D4),onPrimary=Color.White,primaryContainer=Color(0xFFEBDDFF),onPrimaryContainer=Color(0xFF30204F),
    secondary=Color(0xFF645A72),onSecondary=Color.White,secondaryContainer=Color(0xFFECE4F2),onSecondaryContainer=Ink,
    tertiary=Color(0xFF4D652E),onTertiary=Color.White,tertiaryContainer=Lime,onTertiaryContainer=Color(0xFF293B15),
    background=Color(0xFFFAF7F2),onBackground=Ink,surface=Color(0xFFFAF7F2),onSurface=Ink,onSurfaceVariant=Color(0xFF635B6C),
    surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFF4EFF6),surfaceContainer=Color(0xFFEEE8F1),
    surfaceContainerHigh=Color(0xFFE7E0EC),surfaceContainerHighest=Color(0xFFE1D9E7),
    outline=Color(0xFF7D7388),outlineVariant=Color(0xFFCEC5D6),
    error=Color(0xFFBA1A1A),onError=Color.White,errorContainer=Color(0xFFFFDAD6),onErrorContainer=Color(0xFF410002))
private val DarkIon=IonColors(Color(0xFF2D1943),Color(0xFFF0DDFF),Lime,Color(0xFF293B15),Color(0xFF46391D),Color(0xFFFFDFA0),
    IonTone(Color(0xFF192E47),Color(0xFFD0E2FF)),IonTone(Color(0xFF4A2D2C),Color(0xFFFFCFC2)),IonTone(Color(0xFF382246),Color(0xFFE7CDFF)))
private val LightIon=IonColors(Color(0xFF2D1943),Color(0xFFF0DDFF),Lime,Color(0xFF293B15),Color(0xFFFFEAC2),Color(0xFF594111),
    IonTone(Color(0xFFD2E1FF),Color(0xFF203D65)),IonTone(Color(0xFFFFCCBC),Color(0xFF6C3023)),IonTone(Color(0xFFE5D1FF),Color(0xFF452567)))
val LocalIonColors=staticCompositionLocalOf {DarkIon}

object IonShapes {
    val xs=4.dp; val s=8.dp; val m=16.dp; val row=20.dp
    val l=24.dp; val xl=28.dp; val dock=32.dp
    val hero=RoundedCornerShape(topStart=36.dp,topEnd=36.dp,bottomEnd=12.dp,bottomStart=36.dp)
    val discovery=RoundedCornerShape(topStart=12.dp,topEnd=32.dp,bottomEnd=32.dp,bottomStart=32.dp)
}
private fun variableFamily(resource: Int)=FontFamily(listOf(400,500,600,700,800).map {weight ->
    Font(resource,weight=FontWeight(weight),variationSettings=FontVariation.Settings(FontVariation.weight(weight)))})
val IonSans=variableFamily(R.font.onest_variable)
val IonMono=variableFamily(R.font.jetbrains_mono_variable)
/** Long Russian words ("Автопереподключение") must hyphenate instead of breaking at an arbitrary letter on large fonts. */
private fun TextStyle.ion()=copy(fontFamily=IonSans,hyphens=Hyphens.Auto,lineBreak=LineBreak.Paragraph)
private val BaseType=Typography().let {t -> t.copy(
    displayLarge=t.displayLarge.copy(fontFamily=IonSans),displayMedium=t.displayMedium.copy(fontFamily=IonSans),displaySmall=t.displaySmall.copy(fontFamily=IonSans),
    headlineLarge=t.headlineLarge.ion(),headlineMedium=t.headlineMedium.ion(),headlineSmall=t.headlineSmall.ion(),
    titleLarge=t.titleLarge.ion(),titleMedium=t.titleMedium.ion(),titleSmall=t.titleSmall.ion(),
    bodyLarge=t.bodyLarge.ion(),bodyMedium=t.bodyMedium.ion(),bodySmall=t.bodySmall.ion(),
    labelLarge=t.labelLarge.ion(),labelMedium=t.labelMedium.ion(),labelSmall=t.labelSmall.ion())}
private val IonTypography=Typography(
    displayLarge=BaseType.displayLarge.copy(fontWeight=FontWeight.Bold,letterSpacing=(-2).sp),
    displayMedium=BaseType.displayMedium.copy(fontWeight=FontWeight.Bold,letterSpacing=(-1.5).sp),
    displaySmall=BaseType.displaySmall.copy(fontWeight=FontWeight.Bold),
    headlineLarge=BaseType.headlineLarge.copy(fontWeight=FontWeight.ExtraBold,fontSize=36.sp,lineHeight=40.sp,letterSpacing=(-1).sp),
    headlineMedium=BaseType.headlineMedium.copy(fontWeight=FontWeight.Bold),
    headlineSmall=BaseType.headlineSmall.copy(fontWeight=FontWeight.SemiBold),
    titleLarge=BaseType.titleLarge.copy(fontWeight=FontWeight.SemiBold),
    titleMedium=BaseType.titleMedium,titleSmall=BaseType.titleSmall,
    bodyLarge=BaseType.bodyLarge,bodyMedium=BaseType.bodyMedium,bodySmall=BaseType.bodySmall,
    labelLarge=BaseType.labelLarge.copy(fontWeight=FontWeight.SemiBold),
    labelMedium=BaseType.labelMedium,labelSmall=BaseType.labelSmall)
object IonType {
    val page=TextStyle(fontFamily=IonSans,fontSize=48.sp,lineHeight=52.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-2).sp)
    val tile=TextStyle(fontFamily=IonSans,fontSize=25.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.7).sp)
    val figure=TextStyle(fontFamily=IonMono,fontSize=40.sp,lineHeight=48.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-2).sp)
    val speed=figure.copy(fontSize=80.sp,lineHeight=88.sp,letterSpacing=(-5).sp)
    val speedCompact=speed.copy(fontSize=56.sp,lineHeight=64.sp,letterSpacing=(-3).sp)
}
@Composable internal fun IonTheme(dark: Boolean,content: @Composable ()->Unit) {
    CompositionLocalProvider(LocalIonColors provides if(dark) DarkIon else LightIon) {
        MaterialExpressiveTheme(colorScheme=if(dark) DarkScheme else LightScheme,
            typography=IonTypography,motionScheme=MotionScheme.expressive(),
            shapes=Shapes(extraSmall=RoundedCornerShape(IonShapes.xs),small=RoundedCornerShape(IonShapes.s),
                medium=RoundedCornerShape(IonShapes.m),large=RoundedCornerShape(IonShapes.l),extraLarge=RoundedCornerShape(IonShapes.xl)),
            content=content)
    }
}
@Composable internal fun ionColors(): IonColors=LocalIonColors.current
@Composable internal fun systemDark(preference: Boolean?): Boolean=preference ?: isSystemInDarkTheme()
