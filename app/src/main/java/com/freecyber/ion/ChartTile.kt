package com.freecyber.ion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Live speed (filled) and power (line) over the last minutes; each series scales to its own peak. */
@Composable internal fun ChartTile(samples: List<Sample>,modifier: Modifier) {
    val speedColor=MaterialTheme.colorScheme.primary
    val powerColor=MaterialTheme.colorScheme.tertiary
    val maxSpeed=samples.maxOfOrNull {it.speed} ?: 0f
    val maxPower=samples.maxOfOrNull {it.power} ?: 0f
    val minutes=if(samples.size<2) 0 else ((samples.last().at-samples.first().at)/60_000).toInt().coerceAtLeast(1)
    val summary=stringResource(R.string.chart_summary,maxSpeed.toDouble().fmt(1),maxPower.toInt(),minutes)
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.xl)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.ShowChart,null,Modifier.size(22.dp),tint=speedColor)
                Text(stringResource(R.string.tile_chart),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                if(minutes>0) Text(stringResource(R.string.chart_window,minutes),style=MaterialTheme.typography.labelLarge,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(samples.size<2) Text(stringResource(R.string.trip_empty),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Canvas(Modifier.fillMaxWidth().height(120.dp).semantics {contentDescription=summary}) {
                    val t0=samples.first().at.toFloat(); val span=(samples.last().at-samples.first().at).coerceAtLeast(1).toFloat()
                    fun x(s: Sample)=(s.at-t0)/span*size.width
                    val sTop=maxOf(maxSpeed,10f)*1.1f
                    val area=Path().apply {
                        moveTo(0f,size.height)
                        samples.forEach {lineTo(x(it),size.height-it.speed/sTop*size.height)}
                        lineTo(size.width,size.height); close()
                    }
                    drawPath(area,speedColor.copy(alpha=.18f))
                    val line=Path().apply {samples.forEachIndexed {i,s -> val y=size.height-s.speed/sTop*size.height; if(i==0) moveTo(x(s),y) else lineTo(x(s),y)}}
                    drawPath(line,speedColor,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                    if(maxPower>0f) {
                        val pTop=maxPower*1.1f
                        val power=Path().apply {samples.forEachIndexed {i,s ->
                            val y=size.height-(s.power.coerceAtLeast(0f)/pTop)*size.height; if(i==0) moveTo(x(s),y) else lineTo(x(s),y)}}
                        drawPath(power,powerColor,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                    }
                    drawLine(speedColor.copy(alpha=.3f),Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx())
                }
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Legend(speedColor,stringResource(R.string.chart_speed_max,maxSpeed.toDouble().fmt(1)))
                    Legend(powerColor,stringResource(R.string.chart_power_max,maxPower.toInt()))
                }
            }
        }
    }
}

@Composable private fun Legend(color: Color,text: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(10.dp)) {drawCircle(color)}
        Text(text,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
