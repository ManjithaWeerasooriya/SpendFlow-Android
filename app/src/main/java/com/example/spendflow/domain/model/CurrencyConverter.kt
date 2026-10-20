package com.example.spendflow.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * Converts between currencies with a rate meaning "1 unit of the source currency = rate units of
 * the target". Respects each currency's minor units (JPY 0, LKR 2, BHD 3) and rounds half-even.
 */
object CurrencyConverter {

    fun convert(amount: Money, rate: BigDecimal, toCurrency: String): Money {
        require(rate.signum() > 0) { "Rate must be positive, was $rate" }
        val converted = BigDecimal.valueOf(amount.amountMinor)
            .movePointLeft(minorDigits(amount.currencyCode))
            .multiply(rate)
            .movePointRight(minorDigits(toCurrency))
            .setScale(0, RoundingMode.HALF_EVEN)
        return Money(converted.longValueExact(), toCurrency)
    }

    private fun minorDigits(code: String): Int {
        val digits = Currency.getInstance(code).defaultFractionDigits
        require(digits >= 0) { "$code has no minor unit (not a spendable currency)" }
        return digits
    }
}
