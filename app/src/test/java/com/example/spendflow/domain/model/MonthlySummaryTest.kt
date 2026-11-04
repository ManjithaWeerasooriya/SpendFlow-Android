package com.example.spendflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class MonthlySummaryTest {
    @Test
    fun `ratios are fractions without display rounding`() {
        val summary = summary(income = 10_000, expenses = 6_049)

        assertEquals(BigDecimal("0.3951"), summary.savingsRate)
        assertEquals(BigDecimal("0.6049"), summary.expenseToIncomeRatio)
    }

    @Test
    fun `both ratios are unavailable with zero income whether or not there are expenses`() {
        for (expenses in listOf(5_000L, 0L)) {
            val summary = summary(income = 0, expenses = expenses)

            assertNull(summary.savingsRate)
            assertNull(summary.expenseToIncomeRatio)
        }
    }

    @Test
    fun `income without expenses yields full savings and zero spending`() {
        val summary = summary(income = 185_000, expenses = 0)

        assertEquals(BigDecimal.ONE, summary.savingsRate)
        assertEquals(BigDecimal.ZERO, summary.expenseToIncomeRatio)
    }

    @Test
    fun `overspending yields negative savings and spending above one without clamping`() {
        val summary = summary(income = 100_000, expenses = 120_000)

        assertEquals(BigDecimal("-0.2"), summary.savingsRate)
        assertEquals(BigDecimal("1.2"), summary.expenseToIncomeRatio)
    }

    private fun summary(income: Long, expenses: Long) =
        MonthlySummary(YearMonth.of(2026, 10), Money(income, "LKR"), Money(expenses, "LKR"))
}
