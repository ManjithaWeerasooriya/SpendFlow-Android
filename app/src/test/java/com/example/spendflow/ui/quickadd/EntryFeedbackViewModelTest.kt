package com.example.spendflow.ui.quickadd

import app.cash.turbine.test
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.domain.model.*
import com.example.spendflow.testing.FakeUserSession
import com.example.spendflow.testing.FakeWriteFailures
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class EntryFeedbackViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())
        val deleted = mutableListOf<String>()
        var deleteError: AppError? = null
        override fun newId() = "entry"
        override suspend fun create(item: Transaction): AppResult<Unit> {
            rows.value = rows.value + item
            return AppResult.Success(Unit)
        }
        override suspend fun get(id: String) = AppResult.Success(rows.value.first { it.id == id })
        override suspend fun update(item: Transaction) = AppResult.Success(Unit)
        override suspend fun delete(id: String): AppResult<Unit> {
            deleted += id
            deleteError?.let { return AppResult.Error(it) }
            rows.value = rows.value.filterNot { it.id == id }
            return AppResult.Success(Unit)
        }
        override fun observeTransactions(from: Instant, until: Instant) = rows.map { items ->
            AppResult.Success(items.filter { it.dateTime >= from && it.dateTime < until })
        }
        override fun observeRecent(limit: Int) = rows.map { AppResult.Success(it.take(limit)) }
    }

    private fun entry(id: String = "entry", income: Boolean = false) = SavedEntry(
        Transaction(id, if (income) TransactionType.INCOME else TransactionType.EXPENSE,
            Money(245000, "LKR"), BigDecimal.ONE, Money(245000, "LKR"), Instant.now(),
            "groceries", null), "Groceries"
    )

    @Test fun `undo removes only saved entry and monthly totals revert for expenses and income`() = runTest {
        for (income in listOf(false, true)) {
            val transactions = Transactions()
            val model = EntryFeedbackViewModel(transactions, FakeUserSession(), FakeWriteFailures())
            val existing = entry("existing", income)
            transactions.create(existing.transaction)
            val saved = entry(income = income)
            transactions.create(saved.transaction)
            val month = transactions.observeMonth(YearMonth.now())
            month.test {
                assertEquals(490000L, (awaitItem() as AppResult.Success).data.sumOf { it.baseAmount.amountMinor })
                model.saved(saved)
                val confirmation = requireNotNull(model.confirmation.value)
                assertEquals("Groceries", confirmation.entry.categoryName)
                model.events.test {
                    model.undo(confirmation)
                    assertTrue(awaitItem() is AppResult.Success)
                }
                val after = (awaitItem() as AppResult.Success).data
                assertEquals(listOf(existing.transaction), after)
                assertEquals(245000L, after.sumOf { it.baseAmount.amountMinor })
                assertNull(model.confirmation.value)
                model.undo(confirmation)
                assertEquals(listOf("entry"), transactions.deleted)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test fun `confirmation lasts five seconds and expires without deleting`() = runTest {
        val transactions = Transactions()
        val model = EntryFeedbackViewModel(transactions, FakeUserSession(), FakeWriteFailures())
        model.saved(entry())
        val confirmation = requireNotNull(model.confirmation.value)
        advanceTimeBy(4999)
        assertEquals(confirmation, model.confirmation.value)
        advanceTimeBy(1)
        runCurrent()
        assertNull(model.confirmation.value)
        model.undo(confirmation)
        assertTrue(transactions.deleted.isEmpty())
    }

    @Test fun `undo failure reports error and preserves transaction`() = runTest {
        val transactions = Transactions().apply { deleteError = AppError.Network }
        val model = EntryFeedbackViewModel(transactions, FakeUserSession(), FakeWriteFailures())
        val entry = entry()
        transactions.create(entry.transaction)
        model.saved(entry)
        model.events.test {
            model.undo(requireNotNull(model.confirmation.value))
            assertEquals(AppResult.Error(AppError.Network), awaitItem())
        }
        assertEquals(listOf(entry.transaction), transactions.rows.value)
    }

    @Test fun `old undo action cannot delete a newer entry or another users entry`() = runTest {
        val transactions = Transactions()
        val session = FakeUserSession()
        val model = EntryFeedbackViewModel(transactions, session, FakeWriteFailures())
        model.saved(entry("old"))
        val old = requireNotNull(model.confirmation.value)
        model.saved(entry("new"))
        val latest = requireNotNull(model.confirmation.value)
        model.undo(old)
        assertEquals(latest, model.confirmation.value)
        session.signIn("someone-else")
        model.undo(latest)
        assertTrue(transactions.deleted.isEmpty())
        assertNull(model.confirmation.value)
    }

    @Test fun `server rejection clears confirmation and reports the failure`() = runTest {
        val failures = FakeWriteFailures()
        val model = EntryFeedbackViewModel(Transactions(), FakeUserSession(), failures)
        model.saved(entry())
        model.rejectedWrites.test {
            failures.reject("users/test-user/transactions/entry", AppError.PermissionDenied)
            assertEquals(AppError.PermissionDenied, awaitItem())
        }
        assertNull(model.confirmation.value)
    }
}
