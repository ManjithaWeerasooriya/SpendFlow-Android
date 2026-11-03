package com.example.spendflow.domain.model

import java.time.YearMonth

/** Monthly income and expenses in the user's base currency, excluding transfers. */
data class MonthlySummary(
    val month: YearMonth,
    val totalIncome: Money,
    val totalExpenses: Money,
) {
    val netSavings: Money = totalIncome - totalExpenses
}
