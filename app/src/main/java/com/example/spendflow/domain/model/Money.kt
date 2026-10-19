package com.example.spendflow.domain.model

/**
 * An amount in the currency's minor unit (cents for USD) to avoid floating-point rounding.
 * [currencyCode] is an ISO 4217 code such as "USD" or "LKR".
 */
data class Money(val amountMinor: Long, val currencyCode: String) {
    init {
        requireCurrencyCode(currencyCode)
    }

    val isNegative: Boolean get() = amountMinor < 0

    operator fun plus(other: Money): Money = Money(amountMinor + requireSameCurrency(other), currencyCode)

    operator fun minus(other: Money): Money = Money(amountMinor - requireSameCurrency(other), currencyCode)

    operator fun unaryMinus(): Money = copy(amountMinor = -amountMinor)

    private fun requireSameCurrency(other: Money): Long {
        require(currencyCode == other.currencyCode) {
            "Currency mismatch: $currencyCode vs ${other.currencyCode}"
        }
        return other.amountMinor
    }

    companion object {
        fun zero(currencyCode: String) = Money(0, currencyCode)
    }
}

internal fun requireCurrencyCode(code: String) = require(code.length == 3 && code.all { it in 'A'..'Z' }) {
    "currencyCode must be an ISO 4217 code, was '$code'"
}
