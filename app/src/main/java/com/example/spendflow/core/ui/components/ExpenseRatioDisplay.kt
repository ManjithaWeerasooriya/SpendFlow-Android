package com.example.spendflow.core.ui.components

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Presentation values for a determinate bar with a 0–100 range. */
internal data class ExpenseRatioDisplay(
    val percent: String,
    val progress: Int,
    val overIncome: Boolean,
) {
    companion object {
        fun from(fraction: BigDecimal?, locale: Locale = Locale.getDefault()): ExpenseRatioDisplay? {
            if (fraction == null) return null
            return ExpenseRatioDisplay(
                percent = PercentFormatter.formatFraction(fraction, locale),
                progress = fraction.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
                    .movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact(),
                overIncome = fraction > BigDecimal.ONE,
            )
        }
    }
}
