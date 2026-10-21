package com.example.spendflow.core.ui.components

import com.example.spendflow.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Splits a [Money] into the pieces the amount components style separately: "−", "LKR", "2,450.00". */
object AmountFormatter {

    data class Parts(val sign: String, val currencyCode: String, val number: String) {
        override fun toString() = "$sign$currencyCode $number"
    }

    const val MINUS = "−"

    fun parts(money: Money, showDecimals: Boolean, signed: Boolean, locale: Locale = Locale.getDefault()): Parts {
        val digits = runCatching { Currency.getInstance(money.currencyCode).defaultFractionDigits }
            .getOrDefault(2)
            .coerceAtLeast(0)
        val shown = if (showDecimals) digits else 0
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = shown
            maximumFractionDigits = shown
            roundingMode = RoundingMode.HALF_UP
        }.format(BigDecimal.valueOf(money.amountMinor, digits).abs())
        val sign = when {
            money.amountMinor < 0 -> MINUS
            signed && money.amountMinor > 0 -> "+"
            else -> ""
        }
        return Parts(sign, money.currencyCode, number)
    }

    fun format(money: Money, showDecimals: Boolean = false, signed: Boolean = false, locale: Locale = Locale.getDefault()) =
        parts(money, showDecimals, signed, locale).toString()
}
