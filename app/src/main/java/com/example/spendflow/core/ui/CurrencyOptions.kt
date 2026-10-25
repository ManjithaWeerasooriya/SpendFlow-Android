package com.example.spendflow.core.ui

import java.util.Currency
import java.util.Locale

/** Readable selector labels; persisted values remain ISO currency codes. */
object CurrencyOptions {
    private val common = linkedMapOf(
        "LKR" to "Sri Lankan Rupee",
        "USD" to "US Dollar",
        "AUD" to "Australian Dollar",
        "EUR" to "Euro",
        "GBP" to "British Pound",
        "CAD" to "Canadian Dollar",
        "SGD" to "Singapore Dollar",
        "INR" to "Indian Rupee",
        "AED" to "UAE Dirham",
        "JPY" to "Japanese Yen",
        "CHF" to "Swiss Franc",
        "NZD" to "New Zealand Dollar",
    )

    val codes: List<String> = common.keys.toList() + Currency.getAvailableCurrencies()
        .filter { it.defaultFractionDigits >= 0 && it.currencyCode !in common }
        .map { it.currencyCode }.sorted()

    val labels: List<String> = codes.map { code ->
        "$code — ${common[code] ?: Currency.getInstance(code).getDisplayName(Locale.getDefault())}"
    }
}
