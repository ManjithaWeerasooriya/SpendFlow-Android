package com.example.spendflow.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.turbineScope
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
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
            assertEquals(empty, (analyticsStates.awaitItem() as AppResult.Success).data.summary)
            assertEquals(Money.zero("LKR"), empty.totalIncome)

            transactions.rows.value = listOf(income, expense, prior)
            val initial = (homeStates.awaitItem() as AppResult.Success).data
            val initialReport = (analyticsStates.awaitItem() as AppResult.Success).data
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
            assertEquals(edited, (analyticsStates.awaitItem() as AppResult.Success).data.summary)
            assertEquals(BigDecimal("-0.2"), edited.savingsRate)
            assertEquals(BigDecimal("1.2"), edited.expenseToIncomeRatio)

            transactions.rows.value = listOf(expense, prior)
            val deleted = (homeStates.awaitItem() as AppResult.Success).data
            assertEquals(deleted, (analyticsStates.awaitItem() as AppResult.Success).data.summary)
            assertNull(deleted.savingsRate)
            assertNull(deleted.expenseToIncomeRatio)

            analytics.moveMonth(-1)
            val previous = (analyticsStates.awaitItem() as AppResult.Success).data
            assertEquals(feed.currentMonth.minusMonths(1), previous.month)
            assertEquals(9_000_000L, previous.expenseMinor)

            homeStates.cancelAndIgnoreRemainingEvents()
            analyticsStates.cancelAndIgnoreRemainingEvents()
        }
    }

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())

        override fun observeTransactions(from: Instant, until: Instant) = rows.map { items ->
            AppResult.Success(items.filter { it.dateTime >= from && it.dateTime < until })
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
