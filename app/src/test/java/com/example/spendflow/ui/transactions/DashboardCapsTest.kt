package com.example.spendflow.ui.transactions

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.*
import com.example.spendflow.data.remote.mapper.FirestoreMappingException
import com.example.spendflow.data.remote.mapper.budgetFromFirestore
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class DashboardCapsTest {
    private val month = YearMonth.of(2026, 11)
    private val categories = listOf("a", "b", "c", "d").map { Category(it, it.uppercase(), CategoryType.EXPENSE) }
    private fun cap(id: String, category: String? = id, limit: Long = 100) =
        Budget(id, "Budget $id", Money(limit, "LKR"), BudgetPeriod.MONTHLY, month.atDay(1), category)
    private fun selected(amounts: Map<String?, Long> = mapOf("a" to 80, "b" to 100, "c" to 120, null to 40)) = DashboardUiState(
        month, false, UiState.Success(DashboardSummary(MonthlySummary(
            month, Money(1000, "LKR"), Money(amounts.values.sum(), "LKR"),
            expensesByCategory = amounts.mapValues { Money(it.value, "LKR") },
        ), true)),
    )
    private fun previews(budgets: List<Budget>, labels: List<Category> = categories, selected: DashboardUiState = selected()): CapPreviews =
        (dashboardCaps(selected, AppResult.Success(budgets), AppResult.Success(labels), month).content as DashboardCapsContent.Ready).previews

    @Test fun `warnings reuse eligibility without the three preview limit`() {
        val budgets = listOf(cap("a"), cap("b"), cap("c"), cap("d"))
        val usages = dashboardWarningUsages(selected(), AppResult.Success(budgets), AppResult.Success(categories), month, CapThresholds.DEFAULT)
        assertEquals(setOf("a", "b", "c", "d"), usages.map { it.categoryId }.toSet())
        usages.forEach { usage ->
            val row = previews(listOf(usage.cap)).categories.single()
            assertEquals(row.spending, usage.spent)
            assertEquals(row.budgetId, usage.cap.id)
        }
    }

    @Test fun `warnings exclude duplicate late incompatible archived and missing caps`() {
        val budgets = listOf(cap("a"), cap("duplicate", "a"),
            cap("b").copy(startDate = month.atDay(2)), cap("c").copy(limit = Money(100, "USD")),
            cap("d"), cap("missing"), cap("overall", null))
        val labels = categories.map { if (it.id == "d") it.copy(isArchived = true) else it }
        assertTrue(dashboardWarningUsages(selected(), AppResult.Success(budgets), AppResult.Success(labels), month, CapThresholds.DEFAULT).isEmpty())
        val later = cap("0-later", "a").copy(startDate = month.atDay(2))
        val eligible = dashboardWarningUsages(selected(), AppResult.Success(listOf(later, cap("a"))), AppResult.Success(categories), month, CapThresholds.DEFAULT)
        assertEquals("a", eligible.single().cap.id)
    }

    @Test fun `warnings unavailable for historical loading and error states`() {
        val caps = AppResult.Success(listOf(cap("a")))
        val labels = AppResult.Success(categories)
        listOf(DashboardUiState(month, false), DashboardUiState(month, false, UiState.Error(AppError.Network)),
            selected().copy(month = month.minusMonths(1))).forEach {
            assertTrue(dashboardWarningUsages(it, caps, labels, month, CapThresholds.DEFAULT).isEmpty())
        }
        assertTrue(dashboardWarningUsages(selected(), AppResult.Error(AppError.Network), labels, month, CapThresholds.DEFAULT).isEmpty())
    }

    @Test fun `overall includes uncategorized and is separate from three ranked category caps`() {
        val rows = previews(listOf(cap("overall", null, 200), cap("a"), cap("b"), cap("c"), cap("d")))
        assertEquals(Money(340, "LKR"), rows.overall!!.spending)
        assertEquals(BigDecimal("1.7"), rows.overall.fraction)
        assertEquals(listOf("c", "b", "a"), rows.categories.map { it.categoryId })
        assertEquals(listOf(BigDecimal("1.2"), BigDecimal.ONE, BigDecimal("0.8")), rows.categories.map { it.fraction })
    }

    @Test fun `ties are stable by category ID independent of input or labels`() {
        val budgets = listOf(cap("d"), cap("b"), cap("a"), cap("c"))
        val equal = selected(mapOf("a" to 50, "b" to 50, "c" to 50, "d" to 50))
        assertEquals(listOf("a", "b", "c"), previews(budgets, selected = equal).categories.map { it.categoryId })
        assertEquals(previews(budgets, selected = equal), previews(budgets.reversed(), selected = equal))
    }

    @Test fun `duplicate applicable caps have no chosen limit and cannot displace valid caps`() {
        val duplicate = cap("dup", "a")
        val rows = previews(listOf(cap("a"), duplicate, cap("b"), cap("c"), cap("d")))
        assertEquals(listOf("c", "b", "d"), rows.categories.map { it.categoryId })
        val unavailable = previews(listOf(cap("a"), duplicate)).categories.single()
        assertEquals(CapIssue.DUPLICATE, unavailable.issue)
        assertNull(unavailable.fraction)
        assertNull(unavailable.limit)
        val overall = previews(listOf(cap("o1", null), cap("o2", null))).overall!!
        assertEquals(CapIssue.DUPLICATE, overall.issue)
        assertNull(overall.fraction)
    }

    @Test fun `later starts are unavailable and do not duplicate an applicable full month cap`() {
        val later = cap("later", "a").copy(startDate = month.atDay(2))
        assertEquals(CapIssue.START_DATE, previews(listOf(later)).categories.single().issue)
        assertNotNull(previews(listOf(cap("a"), later)).categories.single().fraction)
        assertEquals(CapIssue.START_DATE, previews(listOf(later.copy(startDate = month.plusMonths(1).atDay(1)))).categories.single().issue)
        assertNotNull(previews(listOf(cap("a").copy(startDate = month.minusMonths(1).atDay(15)))).categories.single().fraction)
    }

    @Test fun `missing and archived categories preserve spending and use appropriate labels`() {
        val rows = previews(listOf(cap("a"), cap("b")), listOf(categories[0].copy(isArchived = true)))
        val archived = rows.categories.first { it.categoryId == "a" }
        val missing = rows.categories.first { it.categoryId == "b" }
        assertEquals("A", archived.name)
        assertEquals(CapCategoryLabel.ARCHIVED, archived.categoryLabel)
        assertEquals(Money(80, "LKR"), archived.spending)
        assertEquals("Budget b", missing.name)
        assertEquals(CapCategoryLabel.MISSING, missing.categoryLabel)
        assertEquals(Money(100, "LKR"), missing.spending)
        assertNotNull(missing.fraction)
    }

    @Test fun `currency mismatch and incompatible category metadata never produce zero percent`() {
        val mismatch = previews(listOf(cap("a").copy(limit = Money(100, "USD")))).categories.single()
        assertEquals(CapIssue.CURRENCY, mismatch.issue)
        assertNull(mismatch.fraction)
        val wrongType = previews(listOf(cap("a")), listOf(Category("a", "Income", CategoryType.INCOME))).categories.single()
        assertEquals(CapIssue.CATEGORY_TYPE, wrongType.issue)
        assertNull(wrongType.fraction)
        assertNull(capFraction(Money(100, "LKR"), Money(0, "LKR")))
        assertNull(capFraction(Money(100, "LKR"), Money(-1, "LKR")))
        assertNull(capFraction(Money(100, "USD"), Money(100, "LKR")))
    }

    @Test fun `zero limits remain invalid under repository mapping`() {
        assertThrows(IllegalArgumentException::class.java) { cap("a", limit = 0) }
        assertThrows(FirestoreMappingException::class.java) {
            budgetFromFirestore("bad", mapOf("name" to "Invalid", "limitMinor" to 0L, "currencyCode" to "LKR", "period" to "MONTHLY", "startDate" to "2026-11-01"))
        }
    }

    @Test fun `nonmonthly periods are excluded and no caps need no monetary result`() {
        val budgets = listOf(cap("a").copy(period = BudgetPeriod.WEEKLY), cap("b").copy(period = BudgetPeriod.YEARLY))
        assertEquals(CapPreviews(null, emptyList()), previews(budgets, selected = DashboardUiState(month, false)))
    }

    @Test fun `historical loading and errors are distinct and never become zero spending`() {
        assertEquals(DashboardCapsContent.Historical, dashboardCaps(DashboardUiState(month.minusMonths(1), true), null, null, month).content)
        assertEquals(DashboardCapsContent.Loading, dashboardCaps(selected(), null, null, month).content)
        assertEquals(DashboardCapsContent.Error(AppError.Network), dashboardCaps(selected(), AppResult.Error(AppError.Network), null, month).content)
        val budgets = AppResult.Success(listOf(cap("a")))
        assertEquals(DashboardCapsContent.Loading, dashboardCaps(DashboardUiState(month, false), budgets, AppResult.Success(categories), month).content)
        assertEquals(DashboardCapsContent.Error(AppError.NotFound), dashboardCaps(DashboardUiState(month, false, UiState.Error(AppError.NotFound)), budgets, AppResult.Success(categories), month).content)
        assertEquals(DashboardCapsContent.Error(AppError.Network), dashboardCaps(selected(), budgets, AppResult.Error(AppError.Network), month).content)
    }

    @Test fun `ranking compares huge totals without overflow or rounded ties`() {
        val huge = selected(mapOf("a" to Long.MAX_VALUE - 1))
        val rows = previews(listOf(cap("a", limit = Long.MAX_VALUE), cap("b", limit = 1)), selected = huge)
        assertEquals("a", rows.categories.first().categoryId)
        assertTrue(rows.categories.first().fraction!! > BigDecimal("0.99"))
        assertTrue(capFraction(Money(Long.MAX_VALUE, "LKR"), Money(1, "LKR"))!! > BigDecimal.ONE)
    }
}
