package com.example.spendflow.testing

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.BudgetRepository
import com.example.spendflow.domain.model.Budget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

class FakeBudgetRepository : BudgetRepository {
    val results = MutableStateFlow<AppResult<List<Budget>>?>(AppResult.Success(emptyList()))
    private val items get() = (results.value as? AppResult.Success)?.data.orEmpty()
    override fun observeBudgets() = results.filterNotNull()
    override fun newId() = "budget-${items.size}"
    override suspend fun get(id: String): AppResult<Budget> = items.firstOrNull { it.id == id }
        ?.let { AppResult.Success(it) } ?: AppResult.Error(AppError.NotFound)
    override suspend fun create(item: Budget): AppResult<Unit> {
        results.value = AppResult.Success(items + item)
        return AppResult.Success(Unit)
    }
    override suspend fun update(item: Budget): AppResult<Unit> {
        results.value = AppResult.Success(items.map { if (it.id == item.id) item else it })
        return AppResult.Success(Unit)
    }
    override suspend fun delete(id: String): AppResult<Unit> {
        results.value = AppResult.Success(items.filterNot { it.id == id })
        return AppResult.Success(Unit)
    }
}
