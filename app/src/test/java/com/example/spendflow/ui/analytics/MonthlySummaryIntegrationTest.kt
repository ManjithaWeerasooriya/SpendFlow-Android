package com.example.spendflow.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.components.PercentFormatter
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.domain.model.User
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.testing.FakeCategoryRepository
import com.example.spendflow.testing.MainDispatcherRule
import com.example.spendflow.ui.transactions.TransactionFeedViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
            val analyticsStates = analytics.uiState.settled().testIn(backgroundScope)
            val empty = (homeStates.awaitItem() as AppResult.Success).data
            val emptyState = analyticsStates.awaitItem()
            assertEquals(feed.currentMonth, emptyState.month)
            assertEquals(AnalyticsContent.Empty, emptyState.content)
            assertEquals(Money.zero("LKR"), empty.totalIncome)

            transactions.rows.value = listOf(income, expense, prior)
            val initial = (homeStates.awaitItem() as AppResult.Success).data
            val initialContent = analyticsStates.awaitItem().content as AnalyticsContent.Populated
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
            assertEquals(edited, (analyticsStates.awaitItem().content as AnalyticsContent.Populated).report.summary)
            assertEquals(BigDecimal("-0.2"), edited.savingsRate)
            assertEquals(BigDecimal("1.2"), edited.expenseToIncomeRatio)

            transactions.rows.value = listOf(expense, prior)
            val deleted = (homeStates.awaitItem() as AppResult.Success).data
            val expenseOnly = analyticsStates.awaitItem().content as AnalyticsContent.Populated
            assertTrue(expenseOnly.showIncomeRequired)
            assertEquals(deleted, expenseOnly.report.summary)
            assertEquals(11_240_000L, expenseOnly.report.days[14].expenseMinor)
            assertNull(deleted.savingsRate)
            assertNull(deleted.expenseToIncomeRatio)

            transactions.rows.value = listOf(prior)
            assertEquals(empty, (homeStates.awaitItem() as AppResult.Success).data)
            assertEquals(AnalyticsContent.Empty, analyticsStates.awaitItem().content)

            analytics.moveMonth(-1)
            val previous = (analyticsStates.awaitItem().content as AnalyticsContent.Populated).report
            assertEquals(feed.currentMonth.minusMonths(1), previous.month)
            assertEquals(9_000_000L, previous.expenseMinor)

            homeStates.cancelAndIgnoreRemainingEvents()
            analyticsStates.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fresh analytics selects the current month and cannot move past it`() = runTest {
        val model = analytics(Transactions(), SavedStateHandle())
        val current = YearMonth.now(model.zone)

        assertEquals(AnalyticsUiState(current, false, false, AnalyticsContent.Loading), model.uiState.value)
        model.uiState.settled().test {
            assertEquals(AnalyticsUiState(current, false, false, AnalyticsContent.Empty), awaitItem())
            model.moveMonth(1)
            expectNoEvents()
            model.moveMonth(-1)
            assertEquals(AnalyticsUiState(current.minusMonths(1), true, false, AnalyticsContent.Empty), awaitItem())
            model.moveMonth(1)
            assertEquals(AnalyticsUiState(current, false, false, AnalyticsContent.Empty), awaitItem())
        }
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

        model.uiState.settled().test {
            assertMonth(awaitItem(), january, day = 1, amount = 900)
            model.moveMonth(-1)
            assertMonth(awaitItem(), december, day = 31, amount = 600)
            model.moveMonth(1)
            assertMonth(awaitItem(), january, day = 1, amount = 900)
        }

        model.moveMonth(-1)
        model.setIncomeMode(true)
        val restoredHandle = SavedStateHandle(mapOf(
            "analytics_month" to handle.get<String>("analytics_month"),
            "analytics_income" to handle.get<Boolean>("analytics_income"),
        ))
        val restored = analytics(transactions, restoredHandle)
        assertEquals(december, restored.uiState.value.month)
        assertTrue(restored.uiState.value.incomeMode)
        restored.uiState.settled().test {
            assertMonth(awaitItem(), december, day = 31, amount = 600)
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `a pending month cannot replace the new selection after switching away`() = runTest {
        val january = YearMonth.of(2024, 1)
        val december = january.minusMonths(1)
        val november = december.minusMonths(1)
        val transactions = Transactions()
        transactions.readError.value = AppError.Network
        val model = analytics(transactions, SavedStateHandle(mapOf("analytics_month" to january.toString())))
        transactions.pausedUntil.value = january.atDay(1).atStartOfDay(model.zone).toInstant()
        transactions.rows.value = listOf(expense("november", november, 15, 900, model))

        assertEquals(AnalyticsUiState(january, true, false, AnalyticsContent.Loading), model.uiState.value)
        model.uiState.test {
            val first = awaitItem()
            val error = if (first.content == AnalyticsContent.Loading) awaitItem() else first
            assertEquals(AnalyticsUiState(january, true, false, AnalyticsContent.Error(AppError.Network)), error)
            model.moveMonth(-1)
            assertEquals(AnalyticsUiState(december, true, false, AnalyticsContent.Loading), awaitItem())
            transactions.readError.value = null
            model.moveMonth(-1)
            var state = awaitItem()
            if (state.content == AnalyticsContent.Loading) {
                assertEquals(november, state.month)
                state = awaitItem()
            }
            assertMonth(state, november, day = 15, amount = 900)

            transactions.pausedUntil.value = null
            runCurrent()
            expectNoEvents()
            assertEquals(state, model.uiState.value)
        }
    }

    @Test
    fun `monthly errors and totals render while category metadata is pending and labels update when it arrives`() = runTest {
        val january = YearMonth.of(2024, 1)
        val transactions = Transactions()
        val categoryResults = MutableStateFlow<AppResult<List<Category>>?>(null)
        val categories = object : CategoryRepository by FakeCategoryRepository() {
            override fun observeCategories(type: CategoryType?, includeArchived: Boolean) = categoryResults.filterNotNull()
        }
        val model = analytics(transactions, SavedStateHandle(mapOf("analytics_month" to january.toString())), categories)
        transactions.readError.value = AppError.Network
        transactions.rows.value = listOf(expense("food", january, 2, 700, model))

        model.uiState.settled().test {
            assertEquals(AnalyticsUiState(january, true, false, AnalyticsContent.Error(AppError.Network)), awaitItem())
            transactions.readError.value = null
            val initial = (awaitItem().content as AnalyticsContent.Populated).report
            assertNull(initial.expensesByCategory.single().name)
            categoryResults.value = AppResult.Success(listOf(Category("food", "Food", CategoryType.EXPENSE)))
            val updated = (awaitItem().content as AnalyticsContent.Populated).report
            assertEquals("Food", updated.expensesByCategory.single().name)
            assertEquals(initial.summary, updated.summary)
        }
    }

    private fun Flow<AnalyticsUiState>.settled() = filter { it.content != AnalyticsContent.Loading }

    private fun analytics(
        transactions: Transactions,
        state: SavedStateHandle,
        categories: CategoryRepository = FakeCategoryRepository(),
    ) = AnalyticsViewModel(GetMonthlySummary(transactions, Profile), categories, state)

    private fun expense(id: String, month: YearMonth, day: Int, amount: Long, model: AnalyticsViewModel): Transaction {
        val money = Money(amount, "LKR")
        return Transaction(
            id, TransactionType.EXPENSE, money, BigDecimal.ONE, money,
            month.atDay(day).atStartOfDay(model.zone).toInstant(), "food", null,
        )
    }

    private fun assertMonth(state: AnalyticsUiState, month: YearMonth, day: Int, amount: Long) {
        val report = (state.content as AnalyticsContent.Populated).report
        assertEquals(month, state.month)
        assertEquals(month, report.month)
        assertEquals(month, report.summary.month)
        assertEquals(Money(amount, "LKR"), report.summary.totalExpenses)
        assertEquals(amount, report.days[day - 1].expenseMinor)
    }

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())
        val readError = MutableStateFlow<AppError?>(null)
        val pausedUntil = MutableStateFlow<Instant?>(null)

        override fun observeTransactions(from: Instant, until: Instant) =
            combine(rows, readError, pausedUntil) { items, error, paused ->
                when {
                    paused == until -> null
                    error != null -> AppResult.Error(error)
                    else -> AppResult.Success(items.filter { it.dateTime >= from && it.dateTime < until })
                }
            }.filterNotNull()

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
