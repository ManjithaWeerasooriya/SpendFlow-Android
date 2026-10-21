package com.example.spendflow.core.ui.components

import com.example.spendflow.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AmountFormatterTest {

    @Test
    fun `formats whole amounts with grouping and no decimals`() {
        assertEquals("LKR 72,600", AmountFormatter.format(Money(72_600_00, "LKR"), locale = Locale.US))
    }

    @Test
    fun `shows currency decimals when asked`() {
        assertEquals("LKR 2,450.00", AmountFormatter.format(Money(2_450_00, "LKR"), showDecimals = true, locale = Locale.US))
        assertEquals("JPY 1,200", AmountFormatter.format(Money(1_200, "JPY"), showDecimals = true, locale = Locale.US))
    }

    @Test
    fun `negative amounts use a minus sign before the code`() {
        val parts = AmountFormatter.parts(Money(-2_450_00, "LKR"), showDecimals = true, signed = false, locale = Locale.US)
        assertEquals(AmountFormatter.Parts(AmountFormatter.MINUS, "LKR", "2,450.00"), parts)
    }

    @Test
    fun `signed positive amounts get a plus`() {
        assertEquals("+LKR 150,000", AmountFormatter.format(Money(150_000_00, "LKR"), signed = true, locale = Locale.US))
        assertEquals("LKR 0", AmountFormatter.format(Money.zero("LKR"), signed = true, locale = Locale.US))
    }

    @Test
    fun `progress percent floors and can pass 100`() {
        assertEquals(48, progressPercent(Money(240_000_00, "LKR"), Money(500_000_00, "LKR")))
        assertEquals(105, progressPercent(Money(10_500_00, "LKR"), Money(10_000_00, "LKR")))
        assertEquals(0, progressPercent(Money(100, "LKR"), Money.zero("LKR")))
    }
}
