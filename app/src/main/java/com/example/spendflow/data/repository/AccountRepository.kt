package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository : UserOwnedRepository<Account> {
    /** Sorted by name. Archived accounts are left out unless [includeArchived]. */
    fun observeAccounts(includeArchived: Boolean = false): Flow<AppResult<List<Account>>>
}
