package com.example.spendflow.ui.main

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.network.ConnectivityMonitor
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.domain.model.SavingsGoal
import kotlinx.coroutines.flow.flowOf
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.testing.FakeBudgetRepository
import com.example.spendflow.testing.FakeCategoryRepository
import com.example.spendflow.testing.FakeTransactionRepository
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class Goals : GoalRepository {
        override fun newId() = "g"
        override suspend fun get(id: String): AppResult<SavingsGoal> = AppResult.Error(AppError.NotFound)
        override suspend fun create(item: SavingsGoal): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun update(item: SavingsGoal): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
        override fun observeGoals() = flowOf(AppResult.Success(emptyList<SavingsGoal>()))
        override fun observeConfirmedGoals() = observeGoals()
    }

    private class FakeConnectivity(initial: Boolean) : ConnectivityMonitor {
        val state = MutableStateFlow(initial)
        override val online: Flow<Boolean> = state
    }

    private class PendingTransactions(val pending: MutableStateFlow<Set<String>>) :
        TransactionRepository by FakeTransactionRepository() {
        override fun observePendingIds(): Flow<Set<String>> = pending
    }

    @Test
    fun countsPendingAndTracksOffline() = runTest {
        val net = FakeConnectivity(initial = false)
        val pending = MutableStateFlow(setOf("a", "b"))
        val model = SyncStatusViewModel(
            net, PendingTransactions(pending), Goals(),
            FakeCategoryRepository(), FakeBudgetRepository(),
        )
        model.status.onEach { }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(SyncStatus(offline = true, pendingCount = 2), model.status.value)

        net.state.value = true
        pending.value = emptySet()
        runCurrent()
        assertEquals(SyncStatus(offline = false, pendingCount = 0), model.status.value)
    }
}
