package com.example.spendflow.ui

import androidx.lifecycle.SavedStateHandle
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.testing.FakeWriteFailures
import com.example.spendflow.testing.MainDispatcherRule
import com.example.spendflow.ui.categories.CategoriesViewModel
import com.example.spendflow.ui.goals.GoalsViewModel
import com.example.spendflow.ui.quickadd.QuickAddViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** AC3/AC4: repositories that throw must surface as errors, and the Quick Add draft must be restorable. */
class ThrowingRepositoriesTest {
    @get:Rule val main = MainDispatcherRule()

    private class Goals(var failing: Boolean = true) : GoalRepository {
        override fun newId() = "g"
        override suspend fun create(item: SavingsGoal): AppResult<Unit> = error("boom")
        override suspend fun update(item: SavingsGoal): AppResult<Unit> = error("boom")
        override suspend fun get(id: String): AppResult<SavingsGoal> = error("boom")
        override suspend fun delete(id: String): AppResult<Unit> = error("boom")
        override fun observeGoals(): Flow<AppResult<List<SavingsGoal>>> =
            if (failing) flow { throw IllegalStateException("boom") } else flowOf(AppResult.Success(emptyList()))
        override fun observeConfirmedGoals() = observeGoals()
    }

    private class Categories : CategoryRepository {
        override fun newId() = "c"
        override suspend fun create(item: Category): AppResult<Unit> = error("boom")
        override suspend fun update(item: Category): AppResult<Unit> = error("boom")
        override suspend fun get(id: String): AppResult<Category> = error("boom")
        override suspend fun delete(id: String): AppResult<Unit> = error("boom")
        override fun observeCategories(type: CategoryType?, includeArchived: Boolean): Flow<AppResult<List<Category>>> =
            flow { throw IllegalStateException("boom") }
    }

    private class Users : UserRepository {
        override fun observeProfile(): Flow<AppResult<User?>> = flow { throw IllegalStateException("boom") }
        override suspend fun createProfile(user: User): AppResult<Unit> = error("boom")
        override suspend fun createProfileIfMissing(user: User): AppResult<Unit> = error("boom")
        override suspend fun updateProfile(user: User): AppResult<Unit> = error("boom")
    }

    private object NoTx : TransactionRepository {
        override fun newId() = "t"
        override suspend fun create(item: Transaction): AppResult<Unit> = error("boom")
        override suspend fun get(id: String): AppResult<Transaction> = error("boom")
        override suspend fun update(item: Transaction): AppResult<Unit> = error("boom")
        override suspend fun delete(id: String): AppResult<Unit> = error("boom")
        override fun observeTransactions(from: java.time.Instant, until: java.time.Instant): Flow<AppResult<List<Transaction>>> =
            flow { throw IllegalStateException("boom") }
        override fun observeRecent(limit: Int): Flow<AppResult<List<Transaction>>> = flow { throw IllegalStateException("boom") }
    }

    private val rates = object : ExchangeRates {
        override fun lastRate(currency: String, base: String) = null
        override fun remember(currency: String, base: String, rate: java.math.BigDecimal) {}
        override suspend fun latest(currency: String, base: String) = java.math.BigDecimal.ONE
    }

    private fun goalsModel(repo: GoalRepository) = GoalsViewModel(repo, Users(), FakeWriteFailures(), com.example.spendflow.domain.usecase.GetMonthlySummary(NoTx, Users()), java.time.Clock.systemUTC())

    @Test fun `goals flow that throws becomes an error and retry recovers`() = runTest {
        val repo = Goals()
        val model = goalsModel(repo)
        model.goals.launchIn(backgroundScope)
        advanceUntilIdle()
        assertTrue(model.goals.value is AppResult.Error)
        repo.failing = false
        model.retry()
        advanceUntilIdle()
        assertTrue(model.goals.value is AppResult.Success)
    }

    @Test fun `goal save that throws resets saving`() = runTest {
        val model = goalsModel(Goals())
        model.save(null, "Laptop", "10", "0", java.time.LocalDate.of(2027, 1, 1))
        assertFalse(model.saving.value)
    }

    @Test fun `goal actions that throw report an error instead of crashing`() = runTest {
        val model = goalsModel(Goals())
        val goal = SavingsGoal("g", "Laptop", Money(100, "LKR"), Money(0, "LKR"))
        val events = mutableListOf<AppResult<Unit>>()
        model.events.onEach { events += it }.launchIn(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        model.markCompleted(goal)
        advanceUntilIdle()
        model.setPrimary(goal)
        advanceUntilIdle()
        model.delete(goal)
        advanceUntilIdle()
        assertEquals(3, events.count { it is AppResult.Error })
        assertFalse(model.saving.value)
    }

    @Test fun `categories flow that throws shows an error state`() = runTest {
        val model = CategoriesViewModel(Categories(), FakeWriteFailures(), SavedStateHandle())
        model.uiState.launchIn(backgroundScope)
        advanceUntilIdle()
        assertTrue(model.uiState.value is UiState.Error)
    }

    @Test fun `quick add with throwing repositories does not crash`() = runTest {
        val model = QuickAddViewModel(Categories(), Users(), NoTx, SavedStateHandle(), rates)
        assertTrue(model.categories.value is AppResult.Error)
        assertFalse(model.saving.value)
    }

    @Test fun `quick add draft is restored from saved state`() {
        val model = QuickAddViewModel(
            Categories(), Users(), NoTx,
            SavedStateHandle(mapOf("amount" to "12.50", "note" to "lunch")), rates,
        )
        assertEquals("12.50", model.draft.get<String>("amount"))
        assertEquals("lunch", model.draft.get<String>("note"))
    }
}
