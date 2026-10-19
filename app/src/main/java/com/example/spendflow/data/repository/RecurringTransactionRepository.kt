package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.RecurringTransaction
import kotlinx.coroutines.flow.Flow

interface RecurringTransactionRepository : UserOwnedRepository<RecurringTransaction> {
    /** Sorted by next occurrence, soonest first. Paused ones are left out when [activeOnly]. */
    fun observeRecurring(activeOnly: Boolean = false): Flow<AppResult<List<RecurringTransaction>>>
}
