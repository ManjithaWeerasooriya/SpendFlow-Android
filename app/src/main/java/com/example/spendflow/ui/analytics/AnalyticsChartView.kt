package com.example.spendflow.ui.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.spendflow.R
import com.google.android.material.color.MaterialColors
import kotlin.math.min

/** Native charts share the app's theme palette; the adjacent rows expose exact accessible values. */
class AnalyticsChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors get() = intArrayOf(R.attr.sfColorViolet, R.attr.sfColorIncome, R.attr.sfColorInfo, R.attr.sfColorWarning, R.attr.sfColorOverCap)
        .map { MaterialColors.getColor(this, it) }
    var slices: List<CategoryTotal> = emptyList()
        set(value) { field = value; invalidate() }
    var days: List<DailyCashFlow>? = null
        set(value) { field = value; invalidate() }
    var selectedSlice: Int? = null
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = colors
        val series = days
        if (series != null) {
            val max = series.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) }?.coerceAtLeast(1) ?: 1
            val left = 8f * resources.displayMetrics.density
            val bottom = height - 8f * resources.displayMetrics.density
            paint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
            paint.strokeWidth = resources.displayMetrics.density
            for (i in 0..3) canvas.drawLine(left, bottom * i / 3, width - left, bottom * i / 3, paint)
            val step = (width - 2 * left) / series.size.coerceAtLeast(1)
            series.forEachIndexed { index, day ->
                val x = left + index * step
                paint.color = palette[1]
                canvas.drawRoundRect(x, bottom - (day.incomeMinor.toDouble() / max * (bottom - 8)).toFloat(), x + step * .36f, bottom, 2f, 2f, paint)
                paint.color = palette[0]
                canvas.drawRoundRect(x + step * .42f, bottom - (day.expenseMinor.toDouble() / max * (bottom - 8)).toFloat(), x + step * .78f, bottom, 2f, 2f, paint)
            }
        } else {
            val stroke = min(width, height) * .12f
            val size = min(width, height) - stroke * 1.4f
            val rect = RectF((width - size) / 2, (height - size) / 2, (width + size) / 2, (height + size) / 2)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = MaterialColors.getColor(this, R.attr.sfColorTile)
            canvas.drawArc(rect, 0f, 360f, false, paint)
            val total = slices.sumOf { it.amountMinor }
            var angle = -90f
            slices.forEachIndexed { index, item ->
                val sweep = if (total > 0) (item.amountMinor.toDouble() / total * 360).toFloat() else 0f
                paint.color = palette[index % palette.size]
                paint.alpha = if (selectedSlice == null || selectedSlice == index) 255 else 80
                canvas.drawArc(rect, angle + min(1f, sweep / 4), (sweep - min(2f, sweep / 2)).coerceAtLeast(0f), false, paint)
                angle += sweep
            }
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }
    }
}
