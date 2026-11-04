package com.example.spendflow.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.components.PercentFormatter
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.domain.model.User
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.testing.FakeCategoryRepository
import com.example.spendflow.testing.MainDispatcherRule
import com.example.spendflow.ui.transactions.TransactionFeedViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.Locale

class MonthlySummaryIntegrationTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `feed and analytics agree on live monthly summaries while analytics keeps comparison and chart data`() = runTest {
        val transactions = Transactions()
        val categories = FakeCategoryRepository()
        val summaries = GetMonthlySummary(transactions, Profile)
        val feed = TransactionFeedViewModel(transactions, categories, summaries, Goals, SavedStateHandle())
        val analytics = AnalyticsViewModel(
            summaries, categories, SavedStateHandle(mapOf("analytics_month" to feed.currentMonth.toString())),
        )
        val date = feed.currentMonth.atDay(15).atStartOfDay(analytics.zone)
        val income = Transaction(
            "income", TransactionType.INCOME, Money(50_000, "USD"), BigDecimal("370"),
            Money(18_500_000, "LKR"), date.toInstant(), "salary", null,
        )
        val expense = Transaction(
            "expense", TransactionType.EXPENSE, Money(11_240_000, "LKR"), BigDecimal.ONE,
            Money(11_240_000, "LKR"), date.toInstant(), "food", null,
        )
        val prior = expense.copy(
            id = "prior", amount = Money(9_000_000, "LKR"), baseAmount = Money(9_000_000, "LKR"),
            dateTime = date.minusMonths(1).toInstant(),
        )
        transactions.rows.value = listOf(prior)

        turbineScope {
            val homeStates = feed.monthly.filterNotNull().testIn(backgroundScope)
            val analyticsStates = analytics.report.filterNotNull().testIn(backgroundScope)
            val empty = (homeStates.awaitItem() as AppResult.Success).data
            val emptyReport = analyticsStates.awaitItem()
            assertEquals(empty, (emptyReport.result as AppResult.Success).data.summary)
            assertEquals(AnalyticsContent.Empty, emptyReport.contentFor(feed.currentMonth))
            assertEquals(Money.zero("LKR"), empty.totalIncome)

            transactions.rows.value = listOf(income, expense, prior)
            val initial = (homeStates.awaitItem() as AppResult.Success).data
            val initialContent = analyticsStates.awaitItem().contentFor(feed.currentMonth) as AnalyticsContent.Populated
            val initialReport = initialContent.report
            assertFalse(initialContent.showIncomeRequired)
            assertEquals(initial, initialReport.summary)
            assertEquals(Money(7_260_000, "LKR"), initial.netSavings)
            assertEquals("39%", PercentFormatter.formatFraction(checkNotNull(initial.savingsRate), Locale.US))
            assertEquals("61%", PercentFormatter.formatFraction(checkNotNull(initial.expenseToIncomeRatio), Locale.US))
            assertEquals(9_000_000L, initialReport.previousExpenseMinor)
            assertEquals(11_240_000L, initialReport.days[14].expenseMinor)
            assertEquals(11_240_000L, initialReport.expensesByCategory.single().amountMinor)

            val larger = Money(22_200_000, "LKR")
            transactions.rows.value = listOf(income, expense.copy(amount = larger, baseAmount = larger), prior)
            val edited = (homeStates.awaitItem() as AppResult.Success).data
            assertEquals(edited, (analyticsStates.awaitItem().result as AppResult.Success).data.summary)
            assertEquals(BigDecimal("-0.2"), edited.savingsRate)
            assertEquals(BigDecimal("1.2"), edited.expenseToIncomeRatio)

            transactions.rows.value = listOf(expense, prior)
            val deleted = (homeStates.awaitItem() as AppResult.Success).data
            val expenseOnly = analyticsStates.awaitItem().contentFor(feed.currentMonth) as AnalyticsContent.Populated
            assertTrue(expenseOnly.showIncomeRequired)
            assertEquals(deleted, expenseOnly.report.summary)
            assertEquals(11_240_000L, expenseOnly.report.days[14].expenseMinor)
            assertNull(deleted.savingsRate)
            assertNull(deleted.expenseToIncomeRatio)

            transactions.rows.value = listOf(prior)
            assertEquals(empty, (homeStates.awaitItem() as AppResult.Success).data)
            assertEquals(AnalyticsContent.Empty, analyticsStates.awaitItem().contentFor(feed.currentMonth))

            analytics.moveMonth(-1)
            val previous = (analyticsStates.awaitItem().result as AppResult.Success).data
            assertEquals(feed.currentMonth.minusMonths(1), previous.month)
            assertEquals(9_000_000L, previous.expenseMinor)

            homeStates.cancelAndIgnoreRemainingEvents()
            analyticsStates.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fresh analytics selects the current month and cannot move past it`() {
        val model = analytics(Transactions(), SavedStateHandle())
        val current = YearMonth.now(model.zone)

        assertEquals(current.toString(), model.month.value)
        assertFalse(model.canMoveNext())
        model.moveMonth(1)
        assertEquals(current.toString(), model.month.value)

        model.moveMonth(-1)
        assertEquals(current.minusMonths(1).toString(), model.month.value)
        assertTrue(model.canMoveNext())
        model.moveMonth(1)
        assertEquals(current.toString(), model.month.value)
        assertFalse(model.canMoveNext())
    }

    @Test
    fun `year rollover keeps the selected month summary and daily data aligned and restores selection`() = runTest {
        val january = YearMonth.of(2024, 1)
        val december = january.minusMonths(1)
        val handle = SavedStateHandle(mapOf("analytics_month" to january.toString()))
        val transactions = Transactions()
        val model = analytics(transactions, handle)
        transactions.rows.value = listOf(
            expense("december", december, 31, 600, model),
            expense("january", january, 1, 900, model),
        )

        model.report.filterNotNull().test {
            assertMonth(awaitItem(), january, day = 1, amount = 900)
            model.moveMonth(-1)
            assertEquals(december.toString(), model.month.value)
            assertMonth(awaitItem(), december, day = 31, amount = 600)
            model.moveMonth(1)
            assertEquals(january.toString(), model.month.value)
            assertMonth(awaitItem(), january, day = 1, amount = 900)
        }

        model.moveMonth(-1)
        val restoredHandle = SavedStateHandle(mapOf("analytics_month" to handle.get<String>("analytics_month")))
        val restored = analytics(transactions, restoredHandle)
        assertEquals(december.toString(), restored.month.value)
        restored.report.filterNotNull().test {
            assertMonth(awaitItem(), december, day = 31, amount = 600)
        }
    }

    @Test
    fun `a report error from the previous selection is not used for the new month`() = runTest {
        val january = YearMonth.of(2024, 1)
        val december = january.minusMonths(1)
        val transactions = Transactions()
        transactions.readError.value = AppError.Network
        val model = analytics(transactions, SavedStateHandle(mapOf("analytics_month" to january.toString())))

        model.report.filterNotNull().test {
            val januaryError = awaitItem()
            assertEquals(AppResult.Error(AppError.Network), januaryError.forMonth(january))
            model.moveMonth(-1)
            assertEquals(december.toString(), model.month.value)
            assertNull(januaryError.forMonth(december))
            val decemberError = awaitItem()
            assertEquals(AppResult.Error(AppError.Network), decemberError.forMonth(december))
        }
    }

    private fun analytics(transactions: Transactions, state: SavedStateHandle) =
        AnalyticsViewModel(GetMonthlySummary(transactions, Profile), FakeCategoryRepository(), state)

    private fun expense(id: String, month: YearMonth, day: Int, amount: Long, model: AnalyticsViewModel): Transaction {
        val money = Money(amount, "LKR")
        return Transaction(
            id, TransactionType.EXPENSE, money, BigDecimal.ONE, money,
            month.atDay(day).atStartOfDay(model.zone).toInstant(), "food", null,
        )
    }

    private fun assertMonth(state: AnalyticsViewModel.MonthReport, month: YearMonth, day: Int, amount: Long) {
        val report = (state.forMonth(month) as AppResult.Success).data
        assertEquals(month, report.month)
        assertEquals(month, report.summary.month)
        assertEquals(Money(amount, "LKR"), report.summary.totalExpenses)
        assertEquals(amount, report.days[day - 1].expenseMinor)
    }

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())
        val readError = MutableStateFlow<AppError?>(null)

        override fun observeTransactions(from: Instant, until: Instant) = combine(rows, readError) { items, error ->
            if (error != null) AppResult.Error(error)
            else AppResult.Success(items.filter { it.dateTime >= from && it.dateTime < until })
        }

        override fun observeRecent(limit: Int) = flowOf(AppResult.Success(emptyList<Transaction>()))
        override fun newId() = error("Unused")
        override suspend fun get(id: String) = error("Unused")
        override suspend fun create(item: Transaction) = error("Unused")
        override suspend fun update(item: Transaction) = error("Unused")
        override suspend fun delete(id: String) = error("Unused")
    }

    private object Profile : UserRepository {
        override fun observeProfile() = flowOf(AppResult.Success(User("user", null, null, "LKR")))
        override suspend fun createProfile(user: User) = error("Unused")
        override suspend fun createProfileIfMissing(user: User) = error("Unused")
        override suspend fun updateProfile(user: User) = error("Unused")
    }

    private object Goals : GoalRepository {
        override fun observeGoals() = flowOf(AppResult.Success(emptyList<SavingsGoal>()))
        override fun observeConfirmedGoals() = observeGoals()
        override fun newId() = error("Unused")
        override suspend fun get(id: String) = error("Unused")
        override suspend fun create(item: SavingsGoal) = error("Unused")
        override suspend fun update(item: SavingsGoal) = error("Unused")
        override suspend fun delete(id: String) = error("Unused")
    }
}
