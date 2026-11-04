package com.example.spendflow.core.ui.components

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object PercentFormatter {
    /** Formats a fraction as a localized whole percent, rounding half up. */
    fun formatFraction(fraction: BigDecimal, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }.format(fraction)
}
