package com.example.spendflow.domain.model

import java.math.BigDecimal
import java.math.MathContext
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

    /** Net savings divided by income as a fraction (0.39 means 39%), or null when income is zero. */
    val savingsRate: BigDecimal?
        get() = fractionOfIncome(netSavings.amountMinor)

    /** Expenses divided by income as a fraction (1.2 means 120%), or null when income is zero. */
    val expenseToIncomeRatio: BigDecimal?
        get() = fractionOfIncome(totalExpenses.amountMinor)

    private fun fractionOfIncome(numeratorMinor: Long): BigDecimal? {
        val incomeMinor = totalIncome.amountMinor
        if (incomeMinor == 0L) return null
        // Keep 34 significant digits for repeating decimals; percent rounding belongs to display.
        return BigDecimal.valueOf(numeratorMinor).divide(BigDecimal.valueOf(incomeMinor), MathContext.DECIMAL128)
    }
}
