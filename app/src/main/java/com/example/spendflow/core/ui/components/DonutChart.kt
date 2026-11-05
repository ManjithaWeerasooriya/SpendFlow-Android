package com.example.spendflow.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/** Pure drawing input. Amounts, denominators, labels and interactions belong to the caller. */
internal data class DonutSlice(val share: Double, val color: Color)

/** Decorative ring: callers provide an accessible textual legend. */
@Composable
internal fun DonutChart(slices: List<DonutSlice>, track: Color, selected: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = min(size.width, size.height) * .12f
        val diameter = min(size.width, size.height) - stroke * 1.4f
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        var angle = -90f
        slices.forEachIndexed { index, item ->
            val sweep = (item.share * 360).toFloat()
            drawArc(
                color = item.color,
                startAngle = angle + min(1f, sweep / 4),
                sweepAngle = (sweep - min(2f, sweep / 2)).coerceAtLeast(0f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = if (selected == null || selected == index) 1f else 80 / 255f,
                style = Stroke(stroke),
            )
            angle += sweep
        }
    }
}
