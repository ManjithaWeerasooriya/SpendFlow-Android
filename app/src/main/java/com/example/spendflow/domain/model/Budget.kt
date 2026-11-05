package com.example.spendflow.domain.model

import java.time.LocalDate

/**
 * A spending limit per [period], starting on [startDate].
 * A null [categoryId] means the budget covers all expenses. A monthly budget with a category is that
 * category's cap; [alerts] are the thresholds it warns at.
 */
data class Budget(
    val id: String,
    val name: String,
    val limit: Money,
    val period: BudgetPeriod,
    val startDate: LocalDate,
    val categoryId: String? = null,
    val alerts: Set<CapLevel> = CapLevel.ALERTS,
) {
    init {
        require(name.isNotBlank()) { "Budget name must not be blank" }
        require(limit.amountMinor > 0) { "Budget limit must be positive" }
        require(CapLevel.UNDER !in alerts) { "A cap can't alert below its first threshold" }
    }
}
