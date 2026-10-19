package com.example.spendflow.domain.model

import java.time.LocalDate

/**
 * A template that produces a [Transaction] every [interval] x [frequency]
 * (interval 2 with WEEKLY means every two weeks), from [startDate] until [endDate] if set.
 */
data class RecurringTransaction(
    val id: String,
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val categoryId: String?,
    val transferAccountId: String? = null,
    val note: String? = null,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextOccurrence: LocalDate,
    val isActive: Boolean = true,
) {
    init {
        requireValidMovement(type, amount, accountId, categoryId, transferAccountId)
        require(interval >= 1) { "Interval must be at least 1" }
        require(endDate == null || !endDate.isBefore(startDate)) { "End date is before start date" }
        require(!nextOccurrence.isBefore(startDate)) { "Next occurrence is before start date" }
    }
}
