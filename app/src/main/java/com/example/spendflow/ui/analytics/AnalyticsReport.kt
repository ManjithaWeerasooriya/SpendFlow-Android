package com.example.spendflow.ui.analytics

import com.example.spendflow.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CategoryTotal(val categoryId: String?, val name: String?, val amountMinor: Long, val count: Int)
data class DailyCashFlow(val day: Int, val incomeMinor: Long, val expenseMinor: Long)
data class AnalyticsReport(
    val month: YearMonth,
    val currency: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val previousExpenseMinor: Long,
    val transactionCount: Int,
    val incomeSources: Int,
    val averageDailyExpenseMinor: Long,
    val biggestSpendingDay: Int?,
    val expensesByCategory: List<CategoryTotal>,
    val incomeBySource: List<CategoryTotal>,
    val days: List<DailyCashFlow>,
    val entries: List<Transaction>,
) {
    val netMinor get() = incomeMinor - expenseMinor
    val savingsRate get() = if (incomeMinor > 0) (netMinor.toDouble() / incomeMinor * 100).toInt() else null

    companion object {
        fun build(month: YearMonth, currency: String, items: List<Transaction>, categories: List<Category>,
                  today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): AnalyticsReport {
            val eligible = items.filter { it.type != TransactionType.TRANSFER && it.baseAmount.currencyCode == currency }
            fun Transaction.date() = dateTime.atZone(zone).toLocalDate()
            val current = eligible.filter { YearMonth.from(it.date()) == month }
            val income = current.filter { it.type == TransactionType.INCOME }
            val expense = current.filter { it.type == TransactionType.EXPENSE }
            val incomeTotal = income.sumOf { it.baseAmount.amountMinor }
            val expenseTotal = expense.sumOf { it.baseAmount.amountMinor }
            fun breakdown(transactions: List<Transaction>) = transactions.groupBy { it.categoryId }.map { (id, rows) ->
                CategoryTotal(id, categories.find { it.id == id }?.name, rows.sumOf { it.baseAmount.amountMinor }, rows.size)
            }.sortedWith(compareByDescending<CategoryTotal> { it.amountMinor }.thenBy { it.name.orEmpty() })
            val days = (1..month.lengthOfMonth()).map { day ->
                DailyCashFlow(day, income.filter { it.date().dayOfMonth == day }.sumOf { it.baseAmount.amountMinor },
                    expense.filter { it.date().dayOfMonth == day }.sumOf { it.baseAmount.amountMinor })
            }
            val elapsedDays = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
            return AnalyticsReport(month, currency, incomeTotal, expenseTotal,
                eligible.filter { it.type == TransactionType.EXPENSE && YearMonth.from(it.date()) == month.minusMonths(1) }.sumOf { it.baseAmount.amountMinor },
                current.size, income.map { it.categoryId }.distinct().size, expenseTotal / elapsedDays,
                days.maxByOrNull { it.expenseMinor }?.takeIf { it.expenseMinor > 0 }?.day,
                breakdown(expense), breakdown(income), days, current.sortedByDescending { it.dateTime })
        }
    }
}
