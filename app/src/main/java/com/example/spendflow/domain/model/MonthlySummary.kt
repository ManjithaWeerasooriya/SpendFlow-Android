package com.example.spendflow.domain.model

import java.time.YearMonth

/**
 * Monthly figures in the user's base currency.
 *
 * [expensesByCategory] and [incomeBySource] retain stored category IDs, even when the category
 * is archived or no longer exists. A null key groups transactions with no stored category ID.
 * [totalsByClassification] groups by transaction type and omits types with no transactions.
 * Transfers appear only in that breakdown, never in income, expenses, or net savings.
 */
data class MonthlySummary(
    val month: YearMonth,
    val totalIncome: Money,
    val totalExpenses: Money,
    val expensesByCategory: Map<String?, Money> = emptyMap(),
    val incomeBySource: Map<String?, Money> = emptyMap(),
    val totalsByClassification: Map<TransactionType, Money> = emptyMap(),
) {
    val netSavings: Money = totalIncome - totalExpenses
}
