package com.example.spendflow.testing

import com.example.spendflow.core.common.*
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val rows = MutableStateFlow(initial)
    var error: AppError? = null
    var creates = 0
    var updates = 0
    var deletes = 0
    override fun newId() = "new-transaction"
    override suspend fun get(id: String): AppResult<Transaction> = error?.let { AppResult.Error(it) }
        ?: rows.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: AppResult.Error(AppError.NotFound)
    override suspend fun create(item: Transaction): AppResult<Unit> {
        error?.let { return AppResult.Error(it) }; creates++; rows.value += item; return AppResult.Success(Unit)
    }
    override suspend fun update(item: Transaction): AppResult<Unit> {
        error?.let { return AppResult.Error(it) }; updates++
        rows.value = rows.value.map { if (it.id == item.id) item.copy(updatedAt = Instant.now()) else it }
        return AppResult.Success(Unit)
    }
    override suspend fun delete(id: String): AppResult<Unit> {
        error?.let { return AppResult.Error(it) }; deletes++; rows.value = rows.value.filterNot { it.id == id }; return AppResult.Success(Unit)
    }
    override fun observeTransactions(from: Instant, until: Instant) = rows.map { all ->
        AppResult.Success(all.filter { it.dateTime >= from && it.dateTime < until }.sortedByDescending { it.dateTime })
    }
    override fun observeRecent(limit: Int) = rows.map { AppResult.Success(it.sortedByDescending { row -> row.dateTime }.take(limit)) }
}
