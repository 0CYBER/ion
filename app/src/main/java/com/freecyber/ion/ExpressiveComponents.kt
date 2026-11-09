@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable internal fun ScreenHeading(title: String,actionLabel: String,icon: ImageVector,onAction: ()->Unit,enabled: Boolean=true,
    secondaryLabel: String?=null,secondaryIcon: ImageVector?=null,onSecondary: (()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=IonType.page,color=MaterialTheme.colorScheme.onBackground,modifier=Modifier.weight(1f).semantics {heading()})
        if (secondaryIcon!=null&&onSecondary!=null) {
            androidx.compose.material3.FilledTonalIconButton(onClick=onSecondary,modifier=Modifier.size(48.dp)) {
                Icon(secondaryIcon,secondaryLabel,Modifier.size(24.dp))
            }
        }
        FilledIconButton(onClick=onAction,enabled=enabled,modifier=Modifier.size(60.dp),shape=MaterialShapes.Clover4Leaf.toShape(),
            colors=IconButtonDefaults.filledIconButtonColors(containerColor=ionColors().accent,contentColor=ionColors().onAccent)) {
            Icon(icon,actionLabel,Modifier.size(30.dp))
        }
    }
}

@Composable internal fun DetailHeading(title: String,subtitle: String,onBack: ()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FilledTonalButton(onClick=onBack,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(stringResource(R.string.back))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val style=if(maxWidth<360.dp) MaterialTheme.typography.headlineLarge else IonType.page
            Text(title,style=style,modifier=Modifier.padding(top=8.dp).semantics {heading()})
        }
        Text(subtitle,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=12.dp))
    }
}

/** Layout, type and contrasting shape carry the hierarchy, rather than a border on every card. */
@Composable internal fun ToneTile(title: String,subtitle: String,icon: ImageVector,tone: IonTone,
    modifier: Modifier=Modifier,minHeight: Dp=160.dp,motif: Int=0,onClick: ()->Unit) {
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val round by animateDpAsState(if(pressed) 16.dp else if(motif%2==0) 32.dp else 24.dp,MaterialTheme.motionScheme.fastSpatialSpec(),label="category-round")
    val cut by animateDpAsState(if(pressed) 16.dp else 10.dp,MaterialTheme.motionScheme.fastSpatialSpec(),label="category-cut")
    val rotation=animateFloatAsState(if(pressed) 24f else 0f,MaterialTheme.motionScheme.fastSpatialSpec(),label="category-motif")
    val scale=animateFloatAsState(if(pressed) .97f else 1f,MaterialTheme.motionScheme.fastSpatialSpec(),label="category-press")
    val shape=when(motif%3) {
        0 -> RoundedCornerShape(round,round,cut,round)
        1 -> RoundedCornerShape(cut,round,round,round)
        else -> RoundedCornerShape(round,cut,round,cut)
    }
    val ornament=when(motif%3) {
        0 -> MaterialShapes.Clover4Leaf.toShape()
        1 -> MaterialShapes.Flower.toShape()
        else -> MaterialShapes.Cookie4Sided.toShape()
    }
    Surface(onClick=onClick,interactionSource=interaction,modifier=modifier.graphicsLayer {scaleX=scale.value;scaleY=scale.value},shape=shape,color=tone.container,contentColor=tone.content) {
        Box {
            Box(Modifier.matchParentSize()) {
                Box(Modifier.size(160.dp).align(Alignment.BottomEnd).offset(x=42.dp,y=54.dp)
                    .graphicsLayer {rotationZ=rotation.value}.clip(ornament).background(tone.content.copy(alpha=.08f)))
            }
            Column(Modifier.fillMaxWidth().heightIn(min=minHeight).padding(20.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Box(Modifier.size(44.dp).align(Alignment.End).graphicsLayer {rotationZ=-rotation.value}.clip(ornament).background(tone.content.copy(alpha=.1f)),contentAlignment=Alignment.Center) {
                    Icon(icon,null,Modifier.size(24.dp))
                }
                Column(Modifier.padding(top=20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(title,style=IonType.tile)
                    if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable internal fun ActionRow(title: String,subtitle: String,icon: ImageVector,onClick: ()->Unit) {
    Surface(onClick=onClick,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.l)) {
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(icon,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight,null,Modifier.size(24.dp))
        }
    }
}
