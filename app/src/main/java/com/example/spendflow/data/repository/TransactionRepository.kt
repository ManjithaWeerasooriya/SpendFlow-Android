package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TransactionRepository : UserOwnedRepository<Transaction> {
    /** Transactions with [from] <= dateTime < [until], newest first. */
    fun observeTransactions(from: Instant, until: Instant): Flow<AppResult<List<Transaction>>>
}
