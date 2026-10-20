package com.example.spendflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class CurrencyConverterTest {

    private fun convert(minor: Long, from: String, rate: String, to: String) =
        CurrencyConverter.convert(Money(minor, from), BigDecimal(rate), to)

    @Test
    fun `converts between two-decimal currencies`() {
        // 12.50 USD at 1 USD = 300.25 LKR -> 3753.125 LKR -> rounds half-even to 3753.12
        assertEquals(Money(375_312, "LKR"), convert(1_250, "USD", "300.25", "LKR"))
    }

    @Test
    fun `respects currencies with zero and three minor digits`() {
        // 1000 JPY at 1 JPY = 2.05 LKR -> 2050.00 LKR
        assertEquals(Money(205_000, "LKR"), convert(1_000, "JPY", "2.05", "LKR"))
        // 10.00 USD at 1 USD = 0.377 BHD -> 3.770 BHD
        assertEquals(Money(3_770, "BHD"), convert(1_000, "USD", "0.377", "BHD"))
        // 10.000 BHD at 1 BHD = 400.5 JPY -> 4005 JPY
        assertEquals(Money(4_005, "JPY"), convert(10_000, "BHD", "400.5", "JPY"))
    }

    @Test
    fun `rounds half to even`() {
        // 0.05 USD at 0.5 -> 0.025 EUR -> 0.02 (even); 0.07 at 0.5 -> 0.035 -> 0.04 (even)
        assertEquals(Money(2, "EUR"), convert(5, "USD", "0.5", "EUR"))
        assertEquals(Money(4, "EUR"), convert(7, "USD", "0.5", "EUR"))
    }

    @Test
    fun `same currency at rate one is unchanged`() {
        assertEquals(Money(9_999, "LKR"), convert(9_999, "LKR", "1", "LKR"))
    }

    @Test
    fun `rejects non-positive rates and unknown currencies`() {
        assertThrows(IllegalArgumentException::class.java) { convert(100, "USD", "0", "LKR") }
        assertThrows(IllegalArgumentException::class.java) { convert(100, "USD", "-1", "LKR") }
        assertThrows(IllegalArgumentException::class.java) { convert(100, "USD", "1", "XYZ") }
        // Gold has no minor unit
        assertThrows(IllegalArgumentException::class.java) { convert(100, "USD", "1", "XAU") }
    }
}
