package com.example.spendflow.ui.quickadd

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.spendflow.R
import com.example.spendflow.core.common.*
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.testing.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionEditTest {
    @get:Rule val main = MainDispatcherRule()
    private val date = Instant.parse("2026-10-07T12:34:56Z")
    private val income = Transaction("stored", TransactionType.INCOME, Money(50000, "USD"), BigDecimal("300.123456"),
        Money(15006173, "LKR"), date, "salary", null, "Original note", accountId = "bank",
        recurringTransactionId = "recurring", createdAt = date.minusSeconds(60), updatedAt = date)
    private val categories = FakeCategoryRepository(listOf(
        Category("salary", "Salary", CategoryType.INCOME, defaultCurrency = "EUR", isArchived = true),
        Category("food", "Groceries", CategoryType.EXPENSE),
    ))
    private val users = object : UserRepository {
        override fun observeProfile() = flowOf(AppResult.Success(User("u", null, null, "LKR")))
        override suspend fun createProfile(user: User) = AppResult.Success(Unit)
        override suspend fun createProfileIfMissing(user: User) = AppResult.Success(Unit)
        override suspend fun updateProfile(user: User) = AppResult.Success(Unit)
    }
    private val rates = object : ExchangeRates {
        override fun lastRate(currency: String, base: String) = BigDecimal("999")
        override fun remember(currency: String, base: String, rate: BigDecimal) = Unit
        override suspend fun latest(currency: String, base: String) = error("Editing must not fetch an exchange rate automatically")
    }
    private fun model(repo: FakeTransactionRepository, state: SavedStateHandle = SavedStateHandle(mapOf("transactionId" to "stored"))) =
        QuickAddViewModel(categories, users, repo, state, rates)

    @Test fun `edit preloads all fields and saves to same ID without create undo`() = runTest {
        val repo = FakeTransactionRepository(listOf(income))
        val model = model(repo)
        assertFalse(model.loading.value)
        assertEquals("500.00", model.draft.get<String>("amount"))
        assertEquals("Original note", model.draft.get<String>("note"))
        assertEquals("USD", model.draft.get<String>("currency"))
        assertEquals("300.123456", model.draft.get<String>("rate"))
        assertEquals(date.toString(), model.draft.get<String>("date"))
        assertEquals("salary", model.draft.get<String>("category"))
        assertEquals(true, model.draft.get<Boolean>("income"))
        model.selectSource((categories.get("salary") as AppResult.Success).data)
        assertEquals("USD", model.draft.get<String>("currency"))
        assertEquals("300.123456", model.draft.get<String>("rate"))
        model.events.test {
            assertTrue(model.save("600", "salary", true, null, " Corrected "))
            assertEquals(AppResult.Success(Unit), awaitItem())
        }
        val saved = repo.rows.value.single()
        assertEquals("stored", saved.id)
        assertEquals(0, repo.creates)
        assertEquals(1, repo.updates)
        assertEquals(Money(60000, "USD"), saved.amount)
        assertEquals(Money(18007407, "LKR"), saved.baseAmount)
        assertEquals(income.rate, saved.rate)
        assertEquals(income.accountId, saved.accountId)
        assertEquals(income.recurringTransactionId, saved.recurringTransactionId)
        assertEquals(income.createdAt, saved.createdAt)
        assertEquals(date, saved.dateTime)
        assertEquals("Corrected", saved.note)
        assertNotEquals(income.updatedAt, saved.updatedAt)
        assertNull(model.takeSavedEntry())
    }

    @Test fun `invalid edits use Quick Add validation and failed update preserves draft for retry`() = runTest {
        val repo = FakeTransactionRepository(listOf(income))
        val model = model(repo)
        assertFalse(model.save("0", "salary", true, null, ""))
        assertEquals(R.string.entry_amount_positive, model.errors.value.amount)
        model.editRate("-1")
        assertFalse(model.save("500", "salary", true, null, ""))
        assertEquals(R.string.entry_rate_required, model.errors.value.rate)
        assertEquals(0, repo.updates)
        model.editRate("300")
        repo.error = AppError.Network
        model.events.test {
            assertTrue(model.save("500", "salary", true, null, ""))
            assertEquals(AppResult.Error(AppError.Network), awaitItem())
            assertFalse(model.completed)
            assertEquals(income, repo.rows.value.single())
            repo.error = null
            assertTrue(model.save("500", "salary", true, null, ""))
            assertEquals(AppResult.Success(Unit), awaitItem())
        }
    }

    @Test fun `recreation does not overwrite edited draft and missing record cannot create`() = runTest {
        val repo = FakeTransactionRepository(listOf(income))
        val handle = SavedStateHandle(mapOf("transactionId" to "stored"))
        model(repo, handle)
        handle["amount"] = "625.75"
        handle["note"] = "Unsaved correction"
        val recreated = model(repo, handle)
        assertEquals("625.75", recreated.draft.get<String>("amount"))
        assertEquals("Unsaved correction", recreated.draft.get<String>("note"))
        val missing = model(FakeTransactionRepository())
        assertEquals(AppError.NotFound, missing.loadError.value)
        assertFalse(missing.save("10", "salary", true, null, ""))
    }

    @Test fun `foreign expense payment method and transfer endpoints are preserved`() = runTest {
        val expense = income.copy(type = TransactionType.EXPENSE, categoryId = "food", paymentMethod = PaymentMethod.CASH)
        val repo = FakeTransactionRepository(listOf(expense))
        val editor = model(repo)
        assertEquals(PaymentMethod.CASH.ordinal + 1, editor.draft.get<Int>("payment"))
        assertTrue(editor.save("500", "food", false, PaymentMethod.CASH, "Updated note"))
        assertEquals(expense.amount, repo.rows.value.single().amount)
        assertEquals(expense.rate, repo.rows.value.single().rate)
        assertEquals(PaymentMethod.CASH, repo.rows.value.single().paymentMethod)
        val transfer = income.copy(type = TransactionType.TRANSFER, categoryId = null, transferAccountId = "savings")
        val transfers = FakeTransactionRepository(listOf(transfer))
        val transferEditor = model(transfers)
        assertTrue(transferEditor.isTransfer)
        assertTrue(transferEditor.save("500", null, false, null, "Bank transfer"))
        assertEquals("bank", transfers.rows.value.single().accountId)
        assertEquals("savings", transfers.rows.value.single().transferAccountId)
        assertEquals(TransactionType.TRANSFER, transfers.rows.value.single().type)
    }
}
