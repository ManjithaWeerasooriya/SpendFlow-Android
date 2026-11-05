package com.example.spendflow.ui.transactions

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class DashboardExpenseBreakdownTest {
    private val month = YearMonth.of(2026, 10)
    private fun selected(totals: Map<String?, Long>, total: Long = totals.values.sum()) = DashboardUiState(
        month, true, UiState.Success(DashboardSummary(MonthlySummary(month, Money(0, "LKR"), Money(total, "LKR"),
            expensesByCategory = totals.mapValues { Money(it.value, "LKR") }), true)),
    )
    private fun data(state: DashboardUiState, categories: AppResult<List<Category>>? = AppResult.Success(emptyList())) =
        (dashboardExpenses(state, categories).content as UiState.Success).data

    @Test fun `all grouped amounts reconcile with total and percentages use the shared denominator`() {
        val result = data(selected(mapOf("a" to 60, "b" to 30, null to 10)))
        assertEquals(100L, result.slices.sumOf { it.amount.amountMinor })
        assertEquals(listOf(BigDecimal("0.6"), BigDecimal("0.3"), BigDecimal("0.1")), result.slices.map { it.fraction })
        // Explicitly prove the denominator is totalExpenses, not a newly summed chart subset.
        assertEquals(BigDecimal("0.1"), data(selected(mapOf("a" to 10), total = 100)).slices.single().fraction)
    }

    @Test fun `null archived and multiple missing IDs remain distinct without fabricated counts`() {
        val result = data(selected(mapOf(null to 10, "old" to 20, "missing-one" to 30, "missing-two" to 40)),
            AppResult.Success(listOf(Category("old", "Old food", CategoryType.EXPENSE, isArchived = true))))
        assertEquals(4, result.slices.size)
        val archived = result.slices.first { it.categoryId == "old" }
        assertEquals("Old food", archived.name)
        assertEquals(ExpenseCategoryLabel.ARCHIVED, archived.label)
        assertEquals(setOf("missing-one", "missing-two"), result.slices.filter { it.categoryId?.startsWith("missing") == true }.map { it.categoryId }.toSet())
        assertEquals(ExpenseCategoryLabel.UNKNOWN, result.slices.first { it.categoryId == "missing-one" }.label)
        assertTrue(result.slices.any { it.categoryId == null })
    }

    @Test fun `ties use stable IDs and colors survive rank changes and renaming`() {
        val first = data(selected(mapOf("b" to 10, "a" to 10, null to 10)))
        assertEquals(listOf(null, "a", "b"), first.slices.map { it.categoryId })
        val next = data(selected(mapOf("a" to 5, "b" to 20, null to 1)),
            AppResult.Success(listOf(Category("a", "Renamed", CategoryType.EXPENSE))))
        assertEquals(listOf("b", "a", null), next.slices.map { it.categoryId })
        assertEquals(first.slices.associate { it.categoryId to it.colorIndex }, next.slices.associate { it.categoryId to it.colorIndex })
        assertEquals("Renamed", next.slices.first { it.categoryId == "a" }.name)
        assertTrue(next.slices.all { it.colorIndex in 0..4 })
    }

    @Test fun `zero totals and zero base amounts never divide by zero`() {
        assertTrue(data(selected(emptyMap())).slices.isEmpty())
        val roundedToZero = data(selected(mapOf("tiny-fx" to 0)))
        assertEquals(0L, roundedToZero.total.amountMinor)
        assertEquals(BigDecimal.ZERO, roundedToZero.slices.single().fraction)
    }

    @Test fun `delayed failed and recovered metadata preserve monetary totals`() {
        val selected = selected(mapOf("food" to 100))
        val pending = data(selected, null)
        assertTrue(pending.metadataLoading)
        assertEquals(ExpenseCategoryLabel.PENDING, pending.slices.single().label)
        val failed = data(selected, AppResult.Error(AppError.Network))
        assertEquals(AppError.Network, failed.metadataError)
        assertEquals(pending.total, failed.total)
        val loaded = data(selected, AppResult.Success(listOf(Category("food", "Food", CategoryType.EXPENSE))))
        assertEquals("Food", loaded.slices.single().name)
        assertEquals(pending.total, loaded.total)
        assertEquals(pending.slices.single().fraction, loaded.slices.single().fraction)
    }

    @Test fun `loading and summary errors retain month and cannot become empty success`() {
        assertEquals(DashboardExpenseState(month), dashboardExpenses(DashboardUiState(month, true), null))
        assertEquals(DashboardExpenseState(month, UiState.Error(AppError.NotFound)),
            dashboardExpenses(DashboardUiState(month, true, UiState.Error(AppError.NotFound)), AppResult.Success(emptyList())))
    }

    @Test fun `all categories remain present beyond the palette size`() {
        val result = data(selected((1..15).associate { "category-$it" to it.toLong() }))
        assertEquals(15, result.slices.size)
        assertEquals(120L, result.total.amountMinor)
        assertEquals(120L, result.slices.sumOf { it.amount.amountMinor })
    }
}
