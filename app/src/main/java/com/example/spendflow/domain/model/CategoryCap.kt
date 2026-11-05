package com.example.spendflow.domain.model

import java.time.YearMonth

/**
 * This month's spending in a category against its cap. [spent] is null when the cap is in a different
 * currency than the month's totals, which happens after the base currency is changed in Settings.
 */
data class CapUsage(
    val categoryId: String,
    val cap: Budget,
    val spent: Money?,
    val thresholds: CapThresholds,
) {
    val level: CapLevel = spent?.let { thresholds.levelFor(it.amountMinor, cap.limit.amountMinor) } ?: CapLevel.UNDER

    /** Negative once the cap is overspent. */
    val remaining: Money? = spent?.let { cap.limit - it }

    val overBy: Money? = remaining?.takeIf { it.isNegative }?.let { -it }
}

object CategoryCaps {
    /**
     * Each category's monthly cap. A category has at most one; should older data hold several, the
     * first by ID wins, so every screen and the warnings agree on the same one.
     */
    fun active(budgets: List<Budget>): Map<String, Budget> = budgets
        .filter { it.categoryId != null && it.period == BudgetPeriod.MONTHLY }
        .sortedBy { it.id }
        .groupBy { it.categoryId!! }
        .mapValues { (_, caps) -> caps.first() }

    /** Usage of every active cap in [summary]'s month. Caps never block saving; this only reports. */
    fun usage(budgets: List<Budget>, summary: MonthlySummary, thresholds: CapThresholds): Map<String, CapUsage> =
        active(budgets).mapValues { (categoryId, cap) ->
            val currency = summary.totalExpenses.currencyCode
            val spent = if (cap.limit.currencyCode == currency) {
                summary.expensesByCategory[categoryId] ?: Money.zero(currency)
            } else {
                null
            }
            CapUsage(categoryId, cap, spent, thresholds)
        }
}

/** An in-app warning that [usage] reached [level] in [month]. */
data class CapWarning(val month: YearMonth, val level: CapLevel, val usage: CapUsage) {
    val budgetId: String get() = usage.cap.id
}

/** Decides which cap warnings to show: each threshold at most once per cap per month. */
object CapEvaluator {
    /** The highest threshold the cap has reached among those it alerts at. */
    fun alertLevel(usage: CapUsage): CapLevel =
        usage.cap.alerts.filter { it <= usage.level }.maxOrNull() ?: CapLevel.UNDER

    /**
     * Warnings for caps that reached a higher alert level than [lastShown] returns for them this month.
     * Thresholds crossed together give one warning, at the highest level; going back under a threshold
     * (an edit or a delete) never shows it again in the same month.
     */
    fun newWarnings(usages: Collection<CapUsage>, month: YearMonth, lastShown: (budgetId: String) -> CapLevel): List<CapWarning> =
        usages.mapNotNull { usage ->
            val level = alertLevel(usage)
            if (level > lastShown(usage.cap.id)) CapWarning(month, level, usage) else null
        }
}
