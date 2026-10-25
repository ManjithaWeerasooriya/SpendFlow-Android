package com.example.spendflow.ui.goals

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.*
import com.example.spendflow.testing.FakeWriteFailures
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GoalsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Goals : GoalRepository {
        val rows = mutableListOf<SavingsGoal>()
        override fun newId() = "goal"
        override suspend fun create(item: SavingsGoal): AppResult<Unit> { rows += item; return AppResult.Success(Unit) }
        override suspend fun update(item: SavingsGoal): AppResult<Unit> { rows[0] = item; return AppResult.Success(Unit) }
        override suspend fun get(id: String) = AppResult.Success(rows.first())
        override suspend fun delete(id: String) = AppResult.Success(Unit)
        override fun observeGoals() = flowOf(AppResult.Success(rows.toList()))
        override fun observeConfirmedGoals() = observeGoals()
    }
    private val users = object : UserRepository {
        override fun observeProfile() = flowOf(AppResult.Success(User("u", null, null, "LKR")))
        override suspend fun createProfile(user: User) = AppResult.Success(Unit)
        override suspend fun createProfileIfMissing(user: User) = AppResult.Success(Unit)
        override suspend fun updateProfile(user: User) = AppResult.Success(Unit)
    }

    @Test fun `creates a goal with zero saved and completes at exact target`() = runTest {
        val goals = Goals()
        val model = GoalsViewModel(goals, users, FakeWriteFailures())
        assertTrue(model.save(null, "Laptop", "100.50", "0"))
        val created = goals.rows.single()
        assertEquals(Money(10050, "LKR"), created.target)
        assertFalse(created.isCompleted)
        assertTrue(model.save(created, "Laptop", "100.50", "100.50"))
        assertTrue(goals.rows.single().isCompleted)
        assertEquals(created.id, goals.rows.single().id)
    }

    @Test fun `invalid goal fields never create a record`() = runTest {
        val goals = Goals()
        val model = GoalsViewModel(goals, users, FakeWriteFailures())
        assertFalse(model.save(null, "", "0", "-1"))
        assertNotNull(model.errors.value.name)
        assertNotNull(model.errors.value.target)
        assertNotNull(model.errors.value.saved)
        for (amount in listOf("1.234", "1000000000", "abc")) {
            assertFalse(model.save(null, "Laptop", amount, "0"))
        }
        assertTrue(goals.rows.isEmpty())
    }
}
