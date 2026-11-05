package com.example.spendflow.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.spendflow.core.common.*
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.testing.*
import com.example.spendflow.ui.categories.ExpenseCategoriesViewModel
import com.example.spendflow.ui.quickadd.QuickAddViewModel
import com.example.spendflow.ui.transactions.TransactionFeedViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class TransactionMutationIntegrationTest {
    @get:Rule val main = MainDispatcherRule()
    private val users = FakeProfileRepository(User("u", null, null, "LKR"))
    private val goals = object : GoalRepository {
        override fun observeGoals() = flowOf(AppResult.Success(emptyList<SavingsGoal>()))
        override fun observeConfirmedGoals() = observeGoals()
        override fun newId() = "goal"
        override suspend fun get(id: String) = AppResult.Error(AppError.NotFound)
        override suspend fun create(item: SavingsGoal) = AppResult.Success(Unit)
        override suspend fun update(item: SavingsGoal) = AppResult.Success(Unit)
        override suspend fun delete(id: String) = AppResult.Success(Unit)
    }
    private val budgets = FakeBudgetRepository(listOf(Budget("cap", "Food", Money(500000, "LKR"), BudgetPeriod.MONTHLY, LocalDate.now().withDayOfMonth(1), "food")))
    private val warnings = com.example.spendflow.core.notifications.CapWarnings(
        object : com.example.spendflow.core.notifications.CapAlertHistory {
            override fun lastShown(uid: String, month: YearMonth, budgetId: String) = CapLevel.UNDER
            override fun record(uid: String, month: YearMonth, budgetId: String, level: CapLevel) = Unit
        },
        FakeUserSession("u"),
    )
    private fun capUsage(summary: GetMonthlySummary) = com.example.spendflow.domain.usecase.ObserveCapUsage(
        budgets, summary, users,
        object : com.example.spendflow.domain.usecase.CurrentMonth {
            override val zone: ZoneId = ZoneId.systemDefault()
            override fun months() = flowOf(YearMonth.now(zone))
        },
    )
    private val rates = object : ExchangeRates {
        override fun lastRate(currency: String, base: String): BigDecimal? = null
        override fun remember(currency: String, base: String, rate: BigDecimal) = Unit
        override suspend fun latest(currency: String, base: String) = BigDecimal.ONE
    }

    @Test fun `real edit and delete actions recalculate Home analytics charts and category spending`() = runTest {
        val date = YearMonth.now().atDay(7).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val row = Transaction("stored", TransactionType.EXPENSE, Money(245000, "LKR"), BigDecimal.ONE, Money(245000, "LKR"), date, "food", PaymentMethod.CASH)
        val repository = FakeTransactionRepository(listOf(row))
        val categories = FakeCategoryRepository(listOf(Category("food", "Groceries", CategoryType.EXPENSE)))
        val summary = GetMonthlySummary(repository, users)
        val feed = TransactionFeedViewModel(repository, categories, summary, goals, capUsage(summary), warnings, SavedStateHandle())
        val analytics = AnalyticsViewModel(summary, categories, SavedStateHandle())
        val caps = ExpenseCategoriesViewModel(categories, budgets, users, capUsage(summary), warnings)
        val edit = QuickAddViewModel(categories, users, repository, SavedStateHandle(mapOf("transactionId" to row.id)), rates)
        fun capSpent() = (caps.capOverview.value as AppResult.Success).data.usages.getValue("food").spent?.amountMinor
        turbineScope {
            val home = feed.monthly.filterNotNull().testIn(backgroundScope)
            val reports = analytics.uiState.filter { it.content != AnalyticsContent.Loading }.testIn(backgroundScope)
            val deletes = feed.deletionEvents.testIn(backgroundScope)
            assertEquals(245000L, (home.awaitItem() as AppResult.Success).data.totalExpenses.amountMinor)
            reports.awaitItem()
            assertEquals(245000L, capSpent())
            assertTrue(edit.save("3000", "food", false, PaymentMethod.CASH, "Corrected"))
            val changed = (home.awaitItem() as AppResult.Success).data
            assertEquals(300000L, changed.expensesByCategory["food"]?.amountMinor)
            val chart = (reports.awaitItem().content as AnalyticsContent.Populated).report
            assertEquals(changed, chart.summary)
            assertEquals(300000L, chart.days[6].expenseMinor)
            assertEquals(300000L, capSpent())
            feed.deleteTransaction(row.id)
            assertEquals(AppResult.Success(Unit), deletes.awaitItem())
            assertEquals(0L, (home.awaitItem() as AppResult.Success).data.totalExpenses.amountMinor)
            assertEquals(AnalyticsContent.Empty, reports.awaitItem().content)
            assertEquals(0L, capSpent())
            assertTrue(repository.rows.value.isEmpty())
        }
    }

    @Test fun `delete failure retains record and allows retry`() = runTest {
        val date = Instant.now()
        val row = Transaction("stored", TransactionType.INCOME, Money(100, "LKR"), BigDecimal.ONE, Money(100, "LKR"), date, null, null)
        val repository = FakeTransactionRepository(listOf(row))
        val summary = GetMonthlySummary(repository, users)
        val feed = TransactionFeedViewModel(repository, FakeCategoryRepository(), summary, goals, capUsage(summary), warnings, SavedStateHandle())
        repository.error = AppError.PermissionDenied
        feed.deletionEvents.test {
            feed.deleteTransaction(row.id)
            assertEquals(AppResult.Error(AppError.PermissionDenied), awaitItem())
            assertEquals(listOf(row), repository.rows.value)
            assertNull(feed.deleting.value)
            repository.error = null
            feed.deleteTransaction(row.id)
            assertEquals(AppResult.Success(Unit), awaitItem())
            assertTrue(repository.rows.value.isEmpty())
        }
    }
}
