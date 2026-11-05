package com.example.spendflow.ui.transactions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.testing.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class DashboardSectionsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val month = YearMonth.now()
    private val transactions = Transactions()
    private val categories = FakeCategoryRepository(listOf(Category("food", "Food", CategoryType.EXPENSE)))
    private val budgets = FakeBudgetRepository()
    private val goals = Goals()
    private fun model(labels: CategoryRepository = categories) = TransactionFeedViewModel(transactions, labels, GetMonthlySummary(transactions, Profile), goals, SavedStateHandle(), budgets)
    private fun budget(limit: Long = 1000) = Budget("food-cap", "Food", Money(limit, "LKR"), BudgetPeriod.MONTHLY, month.atDay(1), "food")
    private suspend fun ReceiveTurbine<DashboardCapsState>.ready(): DashboardCapsState {
        var state = awaitItem()
        while (state.content == DashboardCapsContent.Loading) state = awaitItem()
        return state
    }
    private fun DashboardCapsState.previews() = (content as DashboardCapsContent.Ready).previews
    private fun expense(id: String, minor: Long, day: Int = 1) = Transaction(
        id, TransactionType.EXPENSE, Money(minor, "LKR"), BigDecimal.ONE, Money(minor, "LKR"),
        month.atDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant(), "food", null,
    )

    private suspend fun ReceiveTurbine<DashboardExpenseState>.expenses(): DashboardExpenseState {
        var state = awaitItem()
        while (state.content == UiState.Loading) state = awaitItem()
        return state
    }
    private fun DashboardExpenseState.data() = (content as UiState.Success).data

    @Test fun `chart reacts to stored FX add edit delete renames and excludes transfers`() = runTest {
        val model = model()
        model.expenseBreakdown.test {
            assertEquals(0L, expenses().data().total.amountMinor)
            val fx = expense("fx", 500).copy(amount = Money(10, "USD"), rate = BigDecimal("999"))
            transactions.rows.value = listOf(fx)
            assertEquals(500L, expenses().data().slices.single().amount.amountMinor)
            transactions.rows.value = listOf(fx, fx.copy(id = "move", type = TransactionType.TRANSFER,
                categoryId = null, accountId = "a", transferAccountId = "b"))
            expectNoEvents()
            transactions.rows.value = listOf(fx.copy(baseAmount = Money(800, "LKR")))
            assertEquals(800L, expenses().data().total.amountMinor)
            categories.update(Category("food", "Renamed food", CategoryType.EXPENSE))
            assertEquals("Renamed food", expenses().data().slices.single().name)
            transactions.rows.value = emptyList()
            assertEquals(0L, expenses().data().total.amountMinor)
        }
    }

    @Test fun `chart stays month associated during rapid switches and transaction date moves`() = runTest {
        val model = model()
        val expense = expense("food", 500)
        transactions.rows.value = listOf(expense)
        model.expenseBreakdown.test {
            assertEquals(500L, expenses().data().total.amountMinor)
            transactions.paused.value = true
            model.moveHomeMonth(-1)
            assertEquals(DashboardExpenseState(month.minusMonths(1)), awaitItem())
            model.moveHomeMonth(-1)
            assertEquals(DashboardExpenseState(month.minusMonths(2)), awaitItem())
            transactions.rows.value = listOf(expense.copy(dateTime = month.minusMonths(2).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant()))
            transactions.paused.value = false
            val moved = expenses()
            assertEquals(month.minusMonths(2), moved.month)
            assertEquals(500L, moved.data().total.amountMinor)
            model.moveHomeMonth(2)
            assertEquals(0L, expenses().data().total.amountMinor)
        }
    }

    @Test fun `chart metadata can arrive late while monetary data and the existing query stay shared`() = runTest {
        val results = MutableStateFlow<AppResult<List<Category>>?>(null)
        val labels = object : CategoryRepository by categories {
            override fun observeCategories(type: CategoryType?, includeArchived: Boolean) = results.filterNotNull()
        }
        val model = model(labels)
        transactions.rows.value = listOf(expense("food", 500))
        turbineScope {
            val chart = model.expenseBreakdown.testIn(backgroundScope)
            val dashboard = model.dashboard.filter { it.content is UiState.Success }.testIn(backgroundScope)
            val caps = model.capPreviews.testIn(backgroundScope)
            val pending = chart.expenses().data()
            assertEquals(500L, pending.total.amountMinor)
            assertTrue(pending.metadataLoading)
            dashboard.awaitItem()
            results.value = AppResult.Error(AppError.Network)
            assertEquals(AppError.Network, chart.expenses().data().metadataError)
            results.value = AppResult.Success(listOf(Category("food", "Loaded food", CategoryType.EXPENSE)))
            assertEquals("Loaded food", chart.expenses().data().slices.single().name)
            assertEquals(1, transactions.monthlySubscriptions)
            caps.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `income only transfer only and empty months all yield zero expense charts`() = runTest {
        model().expenseBreakdown.test {
            assertEquals(0L, expenses().data().total.amountMinor)
            transactions.rows.value = listOf(expense("pay", 500).copy(type = TransactionType.INCOME))
            // Chart projection is identical despite populated-month metadata changing.
            expectNoEvents()
            transactions.rows.value = listOf(expense("move", 500).copy(type = TransactionType.TRANSFER,
                categoryId = null, accountId = "a", transferAccountId = "b"))
            expectNoEvents()
        }
    }

    @Test fun `goal states updates completion and deletion preserve lifetime selection across months`() = runTest {
        val model = model()
        val first = SavingsGoal("a", "First", Money(1000, "LKR"), Money(200, "LKR"))
        val second = first.copy(id = "b", name = "Second")
        model.goalPreview.test {
            assertEquals(UiState.Loading, awaitItem())
            goals.results.value = AppResult.Error(AppError.Network)
            assertEquals(UiState.Error(AppError.Network), awaitItem())
            goals.results.value = AppResult.Success(emptyList())
            assertEquals(UiState.Success(null), awaitItem())
            goals.results.value = AppResult.Success(listOf(first, second))
            assertEquals(UiState.Success(first), awaitItem())
            val completed = first.copy(saved = first.target)
            goals.results.value = AppResult.Success(listOf(completed, second))
            assertEquals(UiState.Success(second), awaitItem())
            goals.results.value = AppResult.Success(listOf(completed))
            assertEquals(UiState.Success(completed), awaitItem())
            model.moveHomeMonth(-1)
            expectNoEvents()
            goals.results.value = AppResult.Success(emptyList())
            assertEquals(UiState.Success(null), awaitItem())
        }
    }

    @Test fun `cap creation edit removal category changes and stored FX spending update reactively`() = runTest {
        val model = model()
        model.capPreviews.test {
            assertEquals(CapPreviews(null, emptyList()), ready().previews())
            budgets.create(budget())
            assertEquals(BigDecimal.ZERO, ready().previews().categories.single().fraction)
            // Stored conversion is authoritative, even if the original amount and rate disagree.
            val fx = expense("fx", 500).copy(amount = Money(10, "USD"), rate = BigDecimal("999"))
            transactions.rows.value = listOf(fx)
            assertEquals(BigDecimal("0.5"), ready().previews().categories.single().fraction)
            transactions.rows.value = listOf(fx, fx.copy(
                id = "transfer", type = TransactionType.TRANSFER, categoryId = null,
                accountId = "one", transferAccountId = "two",
            ))
            expectNoEvents()
            transactions.rows.value = listOf(fx.copy(baseAmount = Money(800, "LKR")))
            assertEquals(BigDecimal("0.8"), ready().previews().categories.single().fraction)
            budgets.update(budget(2000))
            assertEquals(BigDecimal("0.4"), ready().previews().categories.single().fraction)
            categories.update(Category("food", "Renamed", CategoryType.EXPENSE, isArchived = true))
            val archived = ready().previews().categories.single()
            assertEquals("Renamed", archived.name)
            assertEquals(CapCategoryLabel.ARCHIVED, archived.categoryLabel)
            transactions.rows.value = emptyList()
            assertEquals(BigDecimal.ZERO, ready().previews().categories.single().fraction)
            budgets.delete("food-cap")
            assertEquals(CapPreviews(null, emptyList()), ready().previews())
        }
    }

    @Test fun `historical months and pending new query cannot display stale current caps`() = runTest {
        val model = model()
        budgets.create(budget())
        transactions.rows.value = listOf(expense("food", 500))
        model.capPreviews.test {
            assertEquals(BigDecimal("0.5"), ready().previews().categories.single().fraction)
            model.moveHomeMonth(-1)
            val historical = ready()
            assertEquals(month.minusMonths(1), historical.month)
            assertEquals(DashboardCapsContent.Historical, historical.content)
            transactions.paused.value = true
            model.moveHomeMonth(1)
            assertEquals(DashboardCapsState(month), awaitItem())
            budgets.update(budget(2000))
            expectNoEvents()
            transactions.paused.value = false
            val updated = ready()
            assertEquals(month, updated.month)
            assertEquals(BigDecimal("0.25"), updated.previews().categories.single().fraction)
        }
    }

    @Test fun `budget failure does not hide successful summary recent feed or goals`() = runTest {
        val model = model()
        transactions.rows.value = listOf(expense("food", 500))
        val goal = SavingsGoal("a", "Goal", Money(1000, "LKR"), Money(200, "LKR"))
        goals.results.value = AppResult.Success(listOf(goal))
        turbineScope {
            val caps = model.capPreviews.testIn(backgroundScope)
            val summary = model.dashboard.filter { it.content is UiState.Success }.testIn(backgroundScope)
            val recent = model.recent.filterNotNull().testIn(backgroundScope)
            val goalStates = model.goalPreview.filter { it is UiState.Success }.testIn(backgroundScope)
            caps.ready()
            val selected = summary.awaitItem()
            val rows = recent.awaitItem()
            assertEquals(UiState.Success(goal), goalStates.awaitItem())
            budgets.results.value = AppResult.Error(AppError.Network)
            assertEquals(DashboardCapsContent.Error(AppError.Network), caps.ready().content)
            assertEquals(selected, model.dashboard.value)
            assertEquals(rows, model.recent.value)
            summary.expectNoEvents(); recent.expectNoEvents(); goalStates.expectNoEvents()
        }
    }

    @Test fun `recent stream keeps latest ten across months and backfills after deletion`() = runTest {
        val model = model()
        transactions.rows.value = (1..12).map {
            expense("$it", 100, it).let { row ->
                if (it <= 2) row.copy(dateTime = month.minusMonths(1).atDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant()) else row
            }
        }
        model.recent.filterNotNull().test {
            val first = (awaitItem() as AppResult.Success).data
            assertEquals(10, transactions.requestedLimit)
            assertEquals((12 downTo 3).map(Int::toString), first.map { it.id })
            transactions.rows.value = transactions.rows.value.filterNot { it.id == "12" }
            assertEquals((11 downTo 2).map(Int::toString), (awaitItem() as AppResult.Success).data.map { it.id })
            model.moveHomeMonth(-1)
            expectNoEvents()
            transactions.error.value = AppError.Network
            assertEquals(AppResult.Error(AppError.Network), awaitItem())
            transactions.error.value = null
            awaitItem()
            transactions.rows.value = emptyList()
            assertTrue((awaitItem() as AppResult.Success).data.isEmpty())
        }
    }

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())
        val paused = MutableStateFlow(false)
        val error = MutableStateFlow<AppError?>(null)
        var requestedLimit: Int? = null
        var monthlySubscriptions = 0
        override fun observeTransactions(from: Instant, until: Instant) = combine(rows, paused, error) { items, pause, failure ->
            when { pause -> null; failure != null -> AppResult.Error(failure)
                else -> AppResult.Success(items.filter { it.dateTime >= from && it.dateTime < until }) }
        }.filterNotNull().onStart { monthlySubscriptions++ }
        override fun observeRecent(limit: Int): Flow<AppResult<List<Transaction>>> {
            requestedLimit = limit
            return combine(rows, error) { items, failure ->
                failure?.let { AppResult.Error(it) } ?: AppResult.Success(items.sortedByDescending { it.dateTime }.take(limit))
            }
        }
        override fun newId() = error("Unused")
        override suspend fun get(id: String) = error("Unused")
        override suspend fun create(item: Transaction) = error("Unused")
        override suspend fun update(item: Transaction) = error("Unused")
        override suspend fun delete(id: String) = error("Unused")
    }
    private class Goals : GoalRepository {
        val results = MutableStateFlow<AppResult<List<SavingsGoal>>?>(null)
        override fun observeGoals() = results.filterNotNull()
        override fun observeConfirmedGoals() = observeGoals()
        override fun newId() = error("Unused")
        override suspend fun get(id: String) = error("Unused")
        override suspend fun create(item: SavingsGoal) = error("Unused")
        override suspend fun update(item: SavingsGoal) = error("Unused")
        override suspend fun delete(id: String) = error("Unused")
    }
    private object Profile : UserRepository {
        override fun observeProfile() = flowOf(AppResult.Success(User("user", null, null, "LKR")))
        override suspend fun createProfile(user: User) = error("Unused")
        override suspend fun createProfileIfMissing(user: User) = error("Unused")
        override suspend fun updateProfile(user: User) = error("Unused")
    }
}
