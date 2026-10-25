package com.example.spendflow.ui.analytics

import com.example.spendflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class AnalyticsReportTest {
    private val month = YearMonth.of(2026, 10)
    private fun expense(id: String, minor: Long, date: String = "2026-10-10T12:00:00Z", category: String = "food") =
        Transaction(id, TransactionType.EXPENSE, Money(minor, "LKR"), BigDecimal.ONE, Money(minor, "LKR"), Instant.parse(date), category, null)
    private fun report(rows: List<Transaction>) = AnalyticsReport.build(month, "LKR", rows,
        listOf(Category("food", "Groceries", CategoryType.EXPENSE, isArchived = true)), LocalDate.of(2026, 10, 25), ZoneOffset.UTC)

    @Test fun `uses stored base amounts excludes transfers and calculates daily totals`() {
        val income = Transaction("usd", TransactionType.INCOME, Money(50000, "USD"), BigDecimal("300"), Money(15000000, "LKR"), Instant.parse("2026-10-03T12:00:00Z"), "salary", null)
        val transfer = Transaction("move", TransactionType.TRANSFER, Money(500, "LKR"), BigDecimal.ONE, Money(500, "LKR"), Instant.parse("2026-10-10T12:00:00Z"), null, null, accountId = "a", transferAccountId = "b")
        val value = report(listOf(income, transfer, expense("one", 245000), expense("old", 100000, "2026-09-10T12:00:00Z")))
        assertEquals(15000000L, value.incomeMinor)
        assertEquals(245000L, value.expenseMinor)
        assertEquals(14755000L, value.netMinor)
        assertEquals(100000L, value.previousExpenseMinor)
        assertEquals(2, value.transactionCount)
        assertEquals(9800L, value.averageDailyExpenseMinor)
        assertEquals(10, value.biggestSpendingDay)
        assertEquals(245000L, value.days[9].expenseMinor)
        assertEquals("Groceries", value.expensesByCategory.single().name)
        assertEquals(1, value.incomeSources)
        assertEquals(31, value.days.size)
    }

    @Test fun `undo and empty months recalculate without stale totals`() {
        val rows = listOf(expense("first", 10000), expense("second", 25000))
        assertEquals(35000L, report(rows).expenseMinor)
        assertEquals(10000L, report(rows.dropLast(1)).expenseMinor)
        val empty = report(emptyList())
        assertEquals(0L, empty.netMinor)
        assertNull(empty.savingsRate)
        assertNull(empty.biggestSpendingDay)
        assertTrue(empty.expensesByCategory.isEmpty())
        assertTrue(empty.days.all { it.incomeMinor == 0L && it.expenseMinor == 0L })
    }

    @Test fun `groups missing categories and keeps leap months and timezone boundaries correct`() {
        val value = AnalyticsReport.build(YearMonth.of(2024, 2), "LKR",
            listOf(expense("boundary", 100, "2024-01-31T23:30:00Z", "deleted")), emptyList(), LocalDate.of(2026, 10, 25), ZoneOffset.ofHours(1))
        assertEquals(29, value.days.size)
        assertEquals(100L, value.expenseMinor)
        assertEquals(100L, value.days.first().expenseMinor)
        assertNull(value.expensesByCategory.single().name)
        assertEquals(3L, value.averageDailyExpenseMinor)
    }

    @Test fun `does not mix currencies or include next months entries`() {
        val wrongCurrency = expense("usd", 100).copy(amount = Money(100, "USD"), baseAmount = Money(100, "USD"))
        val value = report(listOf(wrongCurrency, expense("next", 200, "2026-11-01T00:00:00Z")))
        assertEquals(0, value.transactionCount)
        assertEquals(0L, value.expenseMinor)
    }
}
