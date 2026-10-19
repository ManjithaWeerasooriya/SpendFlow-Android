package com.example.spendflow.domain.model

import java.time.LocalDate

/**
 * A spending limit per [period], starting on [startDate].
 * A null [categoryId] means the budget covers all expenses.
 */
data class Budget(
    val id: String,
    val name: String,
    val limit: Money,
    val period: BudgetPeriod,
    val startDate: LocalDate,
    val categoryId: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Budget name must not be blank" }
        require(limit.amountMinor > 0) { "Budget limit must be positive" }
    }
}
