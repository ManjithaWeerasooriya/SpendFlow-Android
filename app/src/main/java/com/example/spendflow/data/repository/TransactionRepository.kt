package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.map
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

interface TransactionRepository : UserOwnedRepository<Transaction> {
    /** Transactions with [from] <= dateTime < [until], newest first. */
    fun observeTransactions(from: Instant, until: Instant): Flow<AppResult<List<Transaction>>>

    /** Calendar month in the user's time zone, optionally filtered by type and category. */
    fun observeMonth(
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
        type: TransactionType? = null,
        categoryId: String? = null,
    ): Flow<AppResult<List<Transaction>>> = observeTransactions(
        month.atDay(1).atStartOfDay(zone).toInstant(),
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
    ).map { result ->
        result.map { items ->
            items.filter { (type == null || it.type == type) && (categoryId == null || it.categoryId == categoryId) }
        }
    }

    /** The most recent [limit] transactions across all months, newest first. */
    fun observeRecent(limit: Int = 10): Flow<AppResult<List<Transaction>>>
}
