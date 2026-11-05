package com.example.spendflow.ui.goals

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.*
import com.example.spendflow.testing.FakeWriteFailures
import com.example.spendflow.testing.MainDispatcherRule
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.testing.FakeTransactionRepository
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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

    private val clock = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC)
    private val deadline = LocalDate.of(2027, 3, 31)
    private fun model(goals: Goals) = GoalsViewModel(goals, users, FakeWriteFailures(), GetMonthlySummary(FakeTransactionRepository(), users), clock)

    @Test fun `creates a goal with zero saved and completes at exact target`() = runTest {
        val goals = Goals()
        val model = model(goals)
        assertTrue(model.save(null, "Laptop", "100.50", "0", deadline))
        val created = goals.rows.single()
        assertEquals(Money(10050, "LKR"), created.target)
        assertFalse(created.isCompleted)
        assertTrue(model.save(created, "Laptop", "100.50", "100.50", deadline))
        assertTrue(goals.rows.single().isCompleted)
        assertEquals(created.id, goals.rows.single().id)
    }

    @Test fun `invalid goal fields never create a record`() = runTest {
        val goals = Goals()
        val model = model(goals)
        assertFalse(model.save(null, "", "0", "-1", deadline))
        assertNotNull(model.errors.value.name)
        assertNotNull(model.errors.value.target)
        assertNotNull(model.errors.value.saved)
        for (amount in listOf("1.234", "1000000000", "abc")) {
            assertFalse(model.save(null, "Laptop", amount, "0", deadline))
        }
        assertTrue(goals.rows.isEmpty())
    }

    @Test fun `a past or missing deadline blocks creation but an unchanged past one can be edited`() = runTest {
        val goals = Goals()
        val model = model(goals)
        assertFalse(model.save(null, "Mac", "500", "0", LocalDate.of(2026, 10, 10)))
        assertNotNull(model.errors.value.deadline)
        assertFalse(model.save(null, "Mac", "500", "0", null))
        assertNotNull(model.errors.value.deadline)
        assertTrue(goals.rows.isEmpty())
        val past = LocalDate.of(2026, 1, 1)
        val existing = SavingsGoal("goal", "Mac", Money(50000, "LKR"), Money(0, "LKR"), past)
        goals.rows += existing
        assertTrue(model.save(existing, "Mac", "500", "10", past))
    }

    @Test fun `form errors flag target saved and deadline together`() {
        val e = goalFormErrors("Mac", "0", "-5", LocalDate.of(2020, 1, 1), null, "LKR", LocalDate.of(2026, 10, 11))
        assertNotNull(e.target); assertNotNull(e.saved); assertNotNull(e.deadline)
        assertFalse(goalFormErrors("Mac", "500000", "240000", LocalDate.of(2027, 3, 31), null, "LKR", LocalDate.of(2026, 10, 11)).hasErrors)
    }
}
