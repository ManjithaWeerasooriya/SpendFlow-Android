package com.example.spendflow.core.ui.components

import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

class ExpenseRatioDisplayTest {
    @Test
    fun `ordinary ratios use a localized label and matching progress`() {
        val display = display(185_000, 112_400)
        assertEquals("61%", display.percent)
        assertEquals(61, display.progress)
        assertFalse(display.overIncome)
        assertEquals("61\u00a0%", display(185_000, 112_400, Locale.FRANCE).percent)
    }

    @Test
    fun `overspending preserves the percentage and caps only the bar`() {
        val display = display(100_000, 120_000)
        assertEquals("120%", display.percent)
        assertEquals(100, display.progress)
        assertTrue(display.overIncome)
        assertFalse(display(100_000, 100_000).overIncome)
    }

    @Test
    fun `unavailable is distinct from a valid zero percent`() {
        val expenseOnly = summary(0, 5_000)
        assertNull(ExpenseRatioDisplay.from(expenseOnly.expenseToIncomeRatio, Locale.US))
        assertNull(ExpenseRatioDisplay.from(summary(0, 0).expenseToIncomeRatio, Locale.US))
        assertEquals(ExpenseRatioDisplay("0%", 0, false), display(100_000, 0))
    }

    private fun display(income: Long, expenses: Long, locale: Locale = Locale.US): ExpenseRatioDisplay =
        checkNotNull(ExpenseRatioDisplay.from(summary(income, expenses).expenseToIncomeRatio, locale))

    private fun summary(income: Long, expenses: Long) =
        MonthlySummary(YearMonth.of(2026, 10), Money(income, "LKR"), Money(expenses, "LKR"))
}
