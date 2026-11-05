package com.example.spendflow.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SavingsGoal(
    val id: String,
    val name: String,
    val target: Money,
    val saved: Money,
    /** Null only for goals created before deadlines existed. */
    val deadline: LocalDate? = null,
    val contributions: List<GoalContribution> = emptyList(),
    val isPrimary: Boolean = false,
    val markedCompleted: Boolean = false,
) {
    init {
        require(name.isNotBlank() && name.length <= 100)
        require(target.amountMinor in 1..99_999_999_999L)
        require(saved.currencyCode == target.currencyCode)
        require(saved.amountMinor in 0..99_999_999_999L)
    }
    val isCompleted get() = saved.amountMinor >= target.amountMinor || markedCompleted
    val remaining get() = Money((target.amountMinor - saved.amountMinor).coerceAtLeast(0), target.currencyCode)

    fun monthsRemaining(today: LocalDate): Int? = deadline?.let { wholeMonthsBetween(today, it) }
    fun requiredMonthly(today: LocalDate): Money? = deadline?.let {
        Money(requiredMonthlySaving(target.amountMinor, saved.amountMinor, it, today), target.currencyCode)
    }
}

/** A manual deposit toward a goal, stored inside the goal document. */
data class GoalContribution(val id: String, val amount: Money, val date: LocalDate, val note: String? = null)

/** The goal shown on Home: the first primary, else the first unfinished, else the first. */
fun primaryGoal(goals: List<SavingsGoal>): SavingsGoal? =
    goals.firstOrNull { it.isPrimary } ?: goals.firstOrNull { !it.isCompleted } ?: goals.firstOrNull()

/** Whole months from [today] to [deadline], rounded down, never below 1. */
fun wholeMonthsBetween(today: LocalDate, deadline: LocalDate): Int =
    ChronoUnit.MONTHS.between(today, deadline).toInt().coerceAtLeast(1)

/** Minor units to save each month, rounded up so the goal is reached by the deadline. */
fun requiredMonthlySaving(target: Long, saved: Long, deadline: LocalDate, today: LocalDate): Long {
    val left = (target - saved).coerceAtLeast(0)
    val months = wholeMonthsBetween(today, deadline).toLong()
    return (left + months - 1) / months
}
