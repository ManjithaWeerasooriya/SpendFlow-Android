package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository : UserOwnedRepository<Budget> {
    /** Sorted by name. */
    fun observeBudgets(): Flow<AppResult<List<Budget>>>
}
