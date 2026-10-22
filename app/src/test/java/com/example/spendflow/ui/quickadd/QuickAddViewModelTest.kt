package com.example.spendflow.ui.quickadd

import androidx.lifecycle.SavedStateHandle
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.testing.FakeCategoryRepository
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class QuickAddViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Transactions : TransactionRepository {
        val saved = mutableListOf<Transaction>()
        override fun newId() = "t1"
        override suspend fun create(item: Transaction): AppResult<Unit> { saved += item; return AppResult.Success(Unit) }
        override suspend fun get(id: String) = AppResult.Success(saved.first())
        override suspend fun update(item: Transaction) = AppResult.Success(Unit)
        override suspend fun delete(id: String) = AppResult.Success(Unit)
        override fun observeTransactions(from: Instant, until: Instant) = flowOf(AppResult.Success(saved.toList()))
        override fun observeRecent(limit: Int) = flowOf(AppResult.Success(saved.take(limit)))
    }
    private val users = object : UserRepository {
        override fun observeProfile() = flowOf(AppResult.Success(User("u1", null, null, "LKR")))
        override suspend fun createProfile(user: User) = AppResult.Success(Unit)
        override suspend fun createProfileIfMissing(user: User) = AppResult.Success(Unit)
        override suspend fun updateProfile(user: User) = AppResult.Success(Unit)
    }
    @Test fun `amount parsing is exact and rejects invalid values`() {
        assertEquals(Money(12345, "LKR"), QuickAddViewModel.parseAmount("123.45", "LKR"))
        assertEquals(Money(123, "JPY"), QuickAddViewModel.parseAmount("123", "JPY"))
        listOf("0", "-1", "0.001", "abc", "99999999999999999999999").forEach {
            assertNull(QuickAddViewModel.parseAmount(it, "LKR"))
        }
    }
    @Test fun `expense needs only amount and category and defaults to now`() = runTest {
        val transactions = Transactions()
        val model = QuickAddViewModel(FakeCategoryRepository(listOf(Category("food", "Food", CategoryType.EXPENSE))), users, transactions, SavedStateHandle())
        val before = Instant.now()
        assertTrue(model.save("25.50", "food", false, null, ""))
        val item = transactions.saved.single()
        assertEquals(Money(2550, "LKR"), item.amount)
        assertEquals(item.amount, item.baseAmount)
        assertNull(item.paymentMethod)
        assertNull(item.note)
        assertFalse(item.dateTime.isBefore(before))
        assertFalse(item.dateTime.isAfter(Instant.now()))
        assertFalse(model.save("25.50", "food", false, null, ""))
        assertEquals(1, transactions.saved.size)
    }
    @Test fun `invalid category does not write and edited date is retained`() = runTest {
        val transactions = Transactions()
        val date = "2026-10-01T08:15:00Z"
        val model = QuickAddViewModel(FakeCategoryRepository(listOf(Category("food", "Food", CategoryType.EXPENSE))), users, transactions, SavedStateHandle(mapOf("date" to date)))
        assertFalse(model.save("10", null, false, null, ""))
        assertFalse(model.save("10", "food", true, null, ""))
        assertTrue(transactions.saved.isEmpty())
        assertTrue(model.save("10", "food", false, PaymentMethod.CASH, " Lunch "))
        assertEquals(Instant.parse(date), transactions.saved.single().dateTime)
        assertEquals("Lunch", transactions.saved.single().note)
    }
}
