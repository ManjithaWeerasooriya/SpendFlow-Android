package com.example.spendflow.ui.transactions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.time.MonthBounds
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.testing.FakeCategoryRepository
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class DashboardViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val transactions = Transactions()
    private val zone = ZoneId.systemDefault()
    private val january = YearMonth.of(2024, 1)
    private fun model(state: SavedStateHandle = SavedStateHandle()) = TransactionFeedViewModel(
        transactions, FakeCategoryRepository(), GetMonthlySummary(transactions, Profile), Goals, state,
        com.example.spendflow.testing.FakeBudgetRepository(),
    )
    private fun saved(month: YearMonth = january) = SavedStateHandle(mapOf("home_month" to month.toString()))
    private suspend fun ReceiveTurbine<DashboardUiState>.ready(): DashboardUiState {
        var result = awaitItem()
        while (result.content == UiState.Loading) result = awaitItem()
        return result
    }
    private fun DashboardUiState.data() = (content as UiState.Success).data
    private fun entry(id: String, type: TransactionType, amount: Long, category: String? = null, month: YearMonth = january): Transaction {
        val money = Money(amount, "LKR")
        return Transaction(id, type, money, BigDecimal.ONE, money, month.atDay(10).atStartOfDay(zone).toInstant(),
            category, null, accountId = if (type == TransactionType.TRANSFER) "a" else null,
            transferAccountId = if (type == TransactionType.TRANSFER) "b" else null)
    }

    @Test fun `defaults to current month and rejects future navigation`() = runTest {
        val model = model()
        assertEquals(DashboardUiState(YearMonth.now(zone), false), model.dashboard.value)
        model.dashboard.test {
            assertEquals(model.currentMonth, ready().month)
            model.moveHomeMonth(1)
            expectNoEvents()
        }
    }

    @Test fun `restores month and navigates December January without changing feed selection`() = runTest {
        val handle = saved()
        handle["feed_filter"] = "INCOME"
        val model = model(handle)
        model.dashboard.test {
            assertEquals(january, ready().month)
            model.moveHomeMonth(-1)
            val december = ready()
            assertEquals(january.minusMonths(1), december.month)
            assertEquals(december.month, december.data().summary.month)
            assertTrue(december.canMoveNext)
            assertEquals("2023-12", handle.get<String>("home_month"))
            model.moveHomeMonth(1)
            assertEquals(january, ready().month)
            assertEquals("INCOME", model.filter.value)
            assertEquals(YearMonth.now(zone), model.currentMonth)
        }
        val restored = model(SavedStateHandle(mapOf("home_month" to handle.get<String>("home_month"))))
        assertEquals(january, restored.dashboard.value.month)
    }

    @Test fun `rapid switches cancel pending month and ignore its later data and errors`() = runTest {
        val model = model(saved())
        val december = january.minusMonths(1)
        val november = january.minusMonths(2)
        val decemberEnd = MonthBounds(december, zone).endExclusive
        transactions.paused.value = decemberEnd
        transactions.rows.value = listOf(entry("nov", TransactionType.EXPENSE, 900, month = november))
        model.dashboard.test {
            ready()
            model.moveHomeMonth(-1)
            assertEquals(DashboardUiState(december, true), awaitItem())
            model.moveHomeMonth(-1)
            val selected = ready()
            assertEquals(november, selected.month)
            assertEquals(november, selected.data().summary.month)
            assertEquals(900L, selected.data().summary.totalExpenses.amountMinor)
            transactions.errors.value = mapOf(decemberEnd to AppError.Network)
            transactions.paused.value = null
            expectNoEvents()
            assertTrue(decemberEnd in transactions.cancelled)
        }
    }

    @Test fun `add edit delete and date moves update the selected month reactively`() = runTest {
        val model = model(saved())
        model.dashboard.test {
            assertFalse(ready().data().hasTransactions)
            val income = entry("pay", TransactionType.INCOME, 18500000, "salary")
            val expense = entry("food", TransactionType.EXPENSE, 11240000, "food")
            transactions.rows.value = listOf(income, expense)
            val populated = ready().data()
            assertEquals(Money(7260000, "LKR"), populated.summary.netSavings)
            val edited = expense.copy(amount = Money(12000000, "LKR"), baseAmount = Money(12000000, "LKR"))
            transactions.rows.value = listOf(income, edited)
            assertEquals(12000000L, ready().data().summary.totalExpenses.amountMinor)
            transactions.rows.value = listOf(income, edited.copy(dateTime = january.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()))
            assertEquals(0L, ready().data().summary.totalExpenses.amountMinor)
            model.moveHomeMonth(1)
            val next = ready()
            assertEquals(january.plusMonths(1), next.month)
            assertEquals(12000000L, next.data().summary.totalExpenses.amountMinor)
            model.moveHomeMonth(-1)
            assertEquals(january, ready().month)
            transactions.rows.value = emptyList()
            val empty = ready().data()
            assertFalse(empty.hasTransactions)
            assertNull(empty.summary.savingsRate)
            assertEquals(0, empty.incomeSourceCount)
        }
    }

    @Test fun `source count uses distinct historical groups including uncategorized and saved FX amounts`() = runTest {
        transactions.rows.value = listOf(
            entry("a", TransactionType.INCOME, 100, "archived"),
            entry("b", TransactionType.INCOME, 200, "archived"),
            entry("c", TransactionType.INCOME, 300, "missing"),
            entry("d", TransactionType.INCOME, 400),
            entry("fx", TransactionType.INCOME, 500, "missing").copy(amount = Money(1, "USD"), rate = BigDecimal("999")),
            entry("expense", TransactionType.EXPENSE, 500, "expense-category"),
        )
        model(saved()).dashboard.test {
            val data = ready().data()
            assertEquals(3, data.incomeSourceCount)
            assertEquals(setOf("archived", "missing", null), data.summary.incomeBySource.keys)
            assertEquals(Money(1500, "LKR"), data.summary.totalIncome)
        }
    }

    @Test fun `expense only and transfer only are populated while empty month has no transactions`() = runTest {
        model(saved()).dashboard.test {
            assertFalse(ready().data().hasTransactions)
            transactions.rows.value = listOf(entry("expense", TransactionType.EXPENSE, 5000, "food"))
            val expenseOnly = ready().data()
            assertTrue(expenseOnly.hasTransactions)
            assertEquals(Money(-5000, "LKR"), expenseOnly.summary.netSavings)
            assertNull(expenseOnly.summary.savingsRate)
            assertNull(expenseOnly.summary.expenseToIncomeRatio)
            transactions.rows.value = listOf(entry("transfer", TransactionType.TRANSFER, 5000))
            val transferOnly = ready().data()
            assertTrue(transferOnly.hasTransactions)
            assertEquals(Money.zero("LKR"), transferOnly.summary.totalIncome)
            assertEquals(Money.zero("LKR"), transferOnly.summary.totalExpenses)
            assertEquals(0, transferOnly.incomeSourceCount)
            assertNull(transferOnly.summary.savingsRate)
        }
    }

    @Test fun `pending and failed query are not empty and recover for the correct month`() = runTest {
        val end = MonthBounds(january, zone).endExclusive
        transactions.paused.value = end
        model(saved()).dashboard.test {
            assertEquals(DashboardUiState(january, true), awaitItem())
            transactions.errors.value = mapOf(end to AppError.Network)
            transactions.paused.value = null
            assertEquals(DashboardUiState(january, true, UiState.Error(AppError.Network)), awaitItem())
            transactions.errors.value = emptyMap()
            assertFalse(ready().data().hasTransactions)
        }
    }

    @Test fun `home selection does not change Transactions month history recent or filter`() = runTest {
        val model = model(saved())
        transactions.rows.value = listOf(entry("now", TransactionType.INCOME, 700, month = model.currentMonth))
        turbineScope {
            val monthly = model.monthly.filterNotNull().testIn(backgroundScope)
            val history = model.history.filterNotNull().testIn(backgroundScope)
            val recent = model.recent.filterNotNull().testIn(backgroundScope)
            val dashboard = model.dashboard.testIn(backgroundScope)
            val totals = monthly.awaitItem()
            val all = history.awaitItem()
            val latest = recent.awaitItem()
            dashboard.ready()
            model.setFilter("EXPENSE")
            model.moveHomeMonth(-1)
            assertEquals(january.minusMonths(1), dashboard.ready().month)
            assertEquals("EXPENSE", model.filter.value)
            assertEquals(totals, model.monthly.value)
            assertEquals(all, model.history.value)
            assertEquals(latest, model.recent.value)
            monthly.expectNoEvents(); history.expectNoEvents(); recent.expectNoEvents()
        }
    }

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())
        val paused = MutableStateFlow<Instant?>(null)
        val errors = MutableStateFlow<Map<Instant, AppError>>(emptyMap())
        val cancelled = mutableSetOf<Instant>()
        override fun observeTransactions(from: Instant, until: Instant) = combine(rows, paused, errors) { items, pause, failures ->
            when {
                pause == until -> null
                failures[until] != null -> AppResult.Error(failures.getValue(until))
                else -> AppResult.Success(items.filter { it.dateTime >= from && it.dateTime < until })
            }
        }.filterNotNull().onCompletion { cancelled += until }
        override fun observeRecent(limit: Int) = rows.map { AppResult.Success(it.sortedByDescending { row -> row.dateTime }.take(limit)) }
        override fun newId() = error("Unexpected write")
        override suspend fun get(id: String) = error("Unexpected read")
        override suspend fun create(item: Transaction) = error("Unexpected write")
        override suspend fun update(item: Transaction) = error("Unexpected write")
        override suspend fun delete(id: String) = error("Unexpected write")
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
