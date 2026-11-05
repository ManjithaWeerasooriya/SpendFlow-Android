package com.example.spendflow.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.ui.theme.SpendFlowTheme

/** Slice colours, in order; category rows use the same colour for their dot and bar. */
@Composable
@ReadOnlyComposable
internal fun analyticsPalette(): List<Color> = SpendFlowTheme.colors.run { listOf(violet, income, info, warning, overCap) }

/**
 * Ring of [slices] starting at 12 o'clock. Unselected slices fade while one is [selected].
 * Not exposed to accessibility: the category rows beside it carry the exact values.
 */
@Composable
internal fun DonutChart(slices: List<CategoryTotal>, selected: Int?, modifier: Modifier = Modifier) {
    val palette = analyticsPalette()
    val total = slices.sumOf { it.amountMinor }
    com.example.spendflow.core.ui.components.DonutChart(
        slices.mapIndexed { index, item ->
            com.example.spendflow.core.ui.components.DonutSlice(
                if (total > 0) item.amountMinor.toDouble() / total else 0.0,
                palette[index % palette.size],
            )
        },
        SpendFlowTheme.colors.tile, selected, modifier,
    )
}

/** Income (green) and expense (violet) bars per day over a light grid. */
@Composable
internal fun CashFlowChart(days: List<DailyCashFlow>, modifier: Modifier = Modifier) {
    val palette = analyticsPalette()
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val max = days.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) }?.coerceAtLeast(1) ?: 1
        val left = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        for (i in 0..3) {
            val y = bottom * i / 3
            drawLine(grid, Offset(left, y), Offset(size.width - left, y), strokeWidth = 1.dp.toPx())
        }
        val step = (size.width - 2 * left) / days.size.coerceAtLeast(1)
        val corner = CornerRadius(2f, 2f)
        fun bar(x: Float, width: Float, minor: Long, color: Color) {
            val top = bottom - (minor.toDouble() / max * (bottom - 8)).toFloat()
            drawRoundRect(color, Offset(x, top), Size(width, bottom - top), corner)
        }
        days.forEachIndexed { index, day ->
            val x = left + index * step
            bar(x, step * .36f, day.incomeMinor, palette[1])
            bar(x + step * .42f, step * .36f, day.expenseMinor, palette[0])
        }
    }
}

@ThemePreviews
@Composable
private fun ChartsPreview() = SpendFlowTheme {
    Column {
        DonutChart(
            listOf(CategoryTotal("a", "Shopping", 1_280_000, 1), CategoryTotal("b", "Food", 620_000, 1), CategoryTotal("c", "Bus", 185_000, 1)),
            selected = null,
            modifier = Modifier.size(240.dp),
        )
        CashFlowChart(
            (1..30).map { DailyCashFlow(it, if (it % 7 == 0) 50_000 else 0, (it * 3_000L) % 40_000) },
            Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}
