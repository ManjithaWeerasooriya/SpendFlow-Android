package com.example.spendflow.ui.quickadd

import com.example.spendflow.R
import java.math.BigDecimal
import java.util.Currency

data class EntryErrors(
    val amount: Int? = null,
    val category: Int? = null,
    val rate: Int? = null,
    val general: Int? = null,
) {
    val hasErrors get() = amount != null || category != null || rate != null || general != null
}

object EntryValidation {
    val maximumAmount = BigDecimal("999999999.99")

    fun amountError(text: String, currency: String): Int? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return R.string.entry_amount_required
        if (!normalized.matches(Regex("-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return R.string.entry_amount_invalid
        val value = normalized.toBigDecimalOrNull() ?: return R.string.entry_amount_invalid
        if (value.signum() <= 0) return R.string.entry_amount_positive
        val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits.coerceIn(0, 2) }.getOrDefault(2)
        if (value.scale() > digits) return R.string.entry_amount_precision
        if (value > maximumAmount) return R.string.entry_amount_large
        return null
    }
}
