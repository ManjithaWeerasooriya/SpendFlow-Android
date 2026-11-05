package com.example.spendflow.core.ui.components

import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Locale

class PercentFormatterTest {
    @Test
    fun `monthly ratios round to the expected percentages for display`() {
        val month = YearMonth.of(2026, 10)
        val savings = MonthlySummary(month, Money(185_000, "LKR"), Money(112_400, "LKR"))
        val overspending = MonthlySummary(month, Money(100_000, "LKR"), Money(120_000, "LKR"))

        assertEquals("39%", format(checkNotNull(savings.savingsRate)))
        assertEquals("61%", format(checkNotNull(savings.expenseToIncomeRatio)))
        assertEquals("-20%", format(checkNotNull(overspending.savingsRate)))
        assertEquals("120%", format(checkNotNull(overspending.expenseToIncomeRatio)))
    }

    @Test
    fun `whole percentages round half up at positive and negative boundaries`() {
        assertEquals("60%", format(BigDecimal("0.6049")))
        assertEquals("61%", format(BigDecimal("0.605")))
        assertEquals("61%", format(BigDecimal("0.6051")))
        assertEquals("-21%", format(BigDecimal("-0.205")))
    }

    private fun format(fraction: BigDecimal) = PercentFormatter.formatFraction(fraction, Locale.US)
}
