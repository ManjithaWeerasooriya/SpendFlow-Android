package com.example.spendflow.testing

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.BudgetRepository
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.PaymentMethod
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/** In-memory transactions; queries see writes at once, like Firestore's cache. Set [error] to make calls fail. */
class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val rows = MutableStateFlow(initial)
    var error: AppError? = null
    var creates = 0
    var updates = 0
    var deletes = 0
    private var nextId = 0

    override fun newId() = "tx${nextId++}"
    override suspend fun get(id: String): AppResult<Transaction> = error?.let { AppResult.Error(it) }
        ?: rows.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: AppResult.Error(AppError.NotFound)
    override suspend fun create(item: Transaction): AppResult<Unit> {
        error?.let { return AppResult.Error(it) }
        creates++
        rows.value += item
        return AppResult.Success(Unit)
    }
    override suspend fun update(item: Transaction): AppResult<Unit> {
        error?.let { return AppResult.Error(it) }
        updates++
        rows.value = rows.value.map { if (it.id == item.id) item.copy(updatedAt = Instant.now()) else it }
        return AppResult.Success(Unit)
    }
    override suspend fun delete(id: String): AppResult<Unit> {
        error?.let { return AppResult.Error(it) }
        deletes++
        rows.value = rows.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }
    override fun observeTransactions(from: Instant, until: Instant): Flow<AppResult<List<Transaction>>> =
        rows.map { all -> AppResult.Success(all.filter { it.dateTime >= from && it.dateTime < until }.sortedByDescending { it.dateTime }) }
    override fun observeRecent(limit: Int): Flow<AppResult<List<Transaction>>> =
        rows.map { all -> AppResult.Success(all.sortedByDescending { it.dateTime }.take(limit)) }
}

/** In-memory caps and budgets. Set [writeError] to make writes fail. */
class FakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
    val items = MutableStateFlow(initial)
    var writeError: AppError? = null
    private var nextId = 0

    override fun newId() = "cap${nextId++}"
    override fun observeBudgets(): Flow<AppResult<List<Budget>>> = items.map { AppResult.Success(it.sortedBy { b -> b.name }) }
    override suspend fun get(id: String): AppResult<Budget> =
        items.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: AppResult.Error(AppError.NotFound)
    override suspend fun create(item: Budget) = write { items.value += item }
    override suspend fun update(item: Budget) = write { items.value = items.value.map { if (it.id == item.id) item else it } }
    override suspend fun delete(id: String) = write { items.value = items.value.filterNot { it.id == id } }

    private fun write(change: () -> Unit): AppResult<Unit> {
        writeError?.let { return AppResult.Error(it) }
        change()
        return AppResult.Success(Unit)
    }
}

fun expense(id: String, categoryId: String, amountMinor: Long, at: Instant, currency: String = "LKR") = Transaction(
    id = id,
    type = TransactionType.EXPENSE,
    amount = Money(amountMinor, currency),
    rate = BigDecimal.ONE,
    baseAmount = Money(amountMinor, currency),
    dateTime = at,
    categoryId = categoryId,
    paymentMethod = PaymentMethod.CASH,
)
