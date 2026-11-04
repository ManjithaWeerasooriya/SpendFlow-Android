package com.example.spendflow.ui.analytics

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneOffset

class AnalyticsContentTest {
    private val month = YearMonth.of(2026, 10)
    private val date = month.atDay(10).atStartOfDay().toInstant(ZoneOffset.UTC)

    @Test
    fun `an empty successful report is distinct from a repository error`() {
        assertEquals(AnalyticsContent.Error(AppError.Network), AppResult.Error(AppError.Network).toAnalyticsContent())
        assertEquals(AnalyticsContent.Empty, content(emptyList()))
    }

    @Test
    fun `a stored expense that rounds to zero still shows a populated report and the income note`() {
        val expense = Transaction(
            "expense", TransactionType.EXPENSE, Money(1, "USD"), BigDecimal("0.1"),
            Money.zero("LKR"), date, "food", null,
        )

        val content = content(listOf(expense)) as AnalyticsContent.Populated
        assertTrue(content.showIncomeRequired)
        assertEquals(Money.zero("LKR"), content.report.summary.totalExpenses)
        assertEquals(1, content.report.transactionCount)
    }

    @Test
    fun `a month with only transfers is not empty and still excludes transfers from charts`() {
        val transfer = Transaction(
            "transfer", TransactionType.TRANSFER, Money(500, "LKR"), BigDecimal.ONE,
            Money(500, "LKR"), date, null, null, accountId = "a", transferAccountId = "b",
        )

        val content = content(listOf(transfer)) as AnalyticsContent.Populated
        assertFalse(content.showIncomeRequired)
        assertEquals(0, content.report.transactionCount)
        assertTrue(content.report.days.all { it.incomeMinor == 0L && it.expenseMinor == 0L })
    }

    private fun content(rows: List<Transaction>): AnalyticsContent =
        AppResult.Success(AnalyticsReport.build(month, "LKR", rows, emptyList(), zone = ZoneOffset.UTC)).toAnalyticsContent()
}
