package com.example.spendflow.ui.analytics

import com.example.spendflow.domain.model.*
import com.example.spendflow.domain.usecase.GetMonthlySummary
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
    val summary: MonthlySummary = MonthlySummary(month, Money(incomeMinor, currency), Money(expenseMinor, currency)),
    val hasTransactions: Boolean = transactionCount > 0,
) {
    val netMinor get() = summary.netSavings.amountMinor
    val savingsRate get() = summary.savingsRate?.movePointRight(2)?.toDouble()?.toInt()

    companion object {
        fun build(month: YearMonth, currency: String, items: List<Transaction>, categories: List<Category>,
                  today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): AnalyticsReport =
            build(GetMonthlySummary.compare(month, currency, items, zone), categories, today, zone)

        internal fun build(
            comparison: GetMonthlySummary.Comparison,
            categories: List<Category>,
            today: LocalDate,
            zone: ZoneId,
        ): AnalyticsReport {
            val summary = comparison.current
            val month = summary.month
            val current = comparison.currentEntries
            val income = current.filter { it.type == TransactionType.INCOME }
            val expense = current.filter { it.type == TransactionType.EXPENSE }
            val categoryNames = categories.associate { it.id to it.name }
            fun breakdown(totals: Map<String?, Money>, transactions: List<Transaction>): List<CategoryTotal> {
                val counts = transactions.groupingBy { it.categoryId }.eachCount()
                return totals.map { (id, amount) ->
                    CategoryTotal(id, categoryNames[id], amount.amountMinor, counts[id] ?: 0)
                }.sortedWith(compareByDescending<CategoryTotal> { it.amountMinor }.thenBy { it.name.orEmpty() })
            }
            val incomeByDay = income.groupBy { it.dateTime.atZone(zone).dayOfMonth }
            val expensesByDay = expense.groupBy { it.dateTime.atZone(zone).dayOfMonth }
            val days = (1..month.lengthOfMonth()).map { day ->
                DailyCashFlow(day, incomeByDay[day].orEmpty().sumOf { it.baseAmount.amountMinor },
                    expensesByDay[day].orEmpty().sumOf { it.baseAmount.amountMinor })
            }
            val elapsedDays = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
            return AnalyticsReport(
                month = month,
                currency = summary.totalIncome.currencyCode,
                incomeMinor = summary.totalIncome.amountMinor,
                expenseMinor = summary.totalExpenses.amountMinor,
                previousExpenseMinor = comparison.previous.totalExpenses.amountMinor,
                transactionCount = current.size,
                incomeSources = summary.incomeBySource.size,
                averageDailyExpenseMinor = summary.totalExpenses.amountMinor / elapsedDays,
                biggestSpendingDay = days.maxByOrNull { it.expenseMinor }?.takeIf { it.expenseMinor > 0 }?.day,
                expensesByCategory = breakdown(summary.expensesByCategory, expense),
                incomeBySource = breakdown(summary.incomeBySource, income),
                days = days,
                entries = current.sortedByDescending { it.dateTime },
                summary = summary,
                hasTransactions = comparison.hasCurrentTransactions,
            )
        }
    }
}
