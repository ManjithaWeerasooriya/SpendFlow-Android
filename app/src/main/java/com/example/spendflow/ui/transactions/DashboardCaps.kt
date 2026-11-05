package com.example.spendflow.ui.transactions

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.time.YearMonth

internal enum class CapIssue { DUPLICATE, START_DATE, CURRENCY, INVALID_LIMIT, CATEGORY_TYPE }
internal enum class CapCategoryLabel { ARCHIVED, MISSING }

internal data class DashboardCap(
    val categoryId: String?,
    val name: String,
    val spending: Money,
    val limit: Money?,
    val fraction: BigDecimal?,
    val issue: CapIssue? = null,
    val categoryLabel: CapCategoryLabel? = null,
)

internal data class CapPreviews(val overall: DashboardCap?, val categories: List<DashboardCap>)
internal sealed interface DashboardCapsContent {
    data object Loading : DashboardCapsContent
    data object Historical : DashboardCapsContent
    data class Error(val error: AppError) : DashboardCapsContent
    data class Ready(val previews: CapPreviews) : DashboardCapsContent
}
internal data class DashboardCapsState(val month: YearMonth, val content: DashboardCapsContent = DashboardCapsContent.Loading)

/** A display projection of existing SF-15 totals; never reads or reaggregates transactions. */
internal fun dashboardCaps(
    dashboard: DashboardUiState,
    budgets: AppResult<List<Budget>>?,
    categories: AppResult<List<Category>>?,
    currentMonth: YearMonth,
): DashboardCapsState {
    fun state(content: DashboardCapsContent) = DashboardCapsState(dashboard.month, content)
    if (dashboard.month != currentMonth) return state(DashboardCapsContent.Historical)
    when (budgets) {
        null -> return state(DashboardCapsContent.Loading)
        is AppResult.Error -> return state(DashboardCapsContent.Error(budgets.error))
        is AppResult.Success -> Unit
    }
    val monthly = budgets.data.filter { it.period == BudgetPeriod.MONTHLY }
    if (monthly.isEmpty()) return state(DashboardCapsContent.Ready(CapPreviews(null, emptyList())))
    val summary = when (val content = dashboard.content) {
        UiState.Loading -> return state(DashboardCapsContent.Loading)
        is UiState.Error -> return state(DashboardCapsContent.Error(content.error))
        is UiState.Success -> content.data.summary
    }
    when (categories) {
        null -> return state(DashboardCapsContent.Loading)
        is AppResult.Error -> return state(DashboardCapsContent.Error(categories.error))
        is AppResult.Success -> Unit
    }
    val names = categories.data.associateBy { it.id }
    val firstDay = dashboard.month.atDay(1)
    val rows = monthly.groupBy { it.categoryId }.map { (id, group) ->
        val applicable = group.filter { it.startDate <= firstDay }
        // Later-starting records do not create a duplicate of an applicable full-month cap.
        val chosen = applicable.singleOrNull()
        val category = names[id]
        val spending = if (id == null) summary.totalExpenses else
            summary.expensesByCategory[id] ?: Money.zero(summary.totalExpenses.currencyCode)
        val issue = when {
            applicable.size > 1 -> CapIssue.DUPLICATE
            chosen == null -> CapIssue.START_DATE
            chosen.limit.amountMinor <= 0 -> CapIssue.INVALID_LIMIT
            chosen.limit.currencyCode != spending.currencyCode -> CapIssue.CURRENCY
            category != null && category.type != CategoryType.EXPENSE -> CapIssue.CATEGORY_TYPE
            else -> null
        }
        DashboardCap(
            categoryId = id,
            name = category?.name ?: (chosen?.name ?: group.map { it.name }.distinct().sorted().joinToString(" / ")),
            spending = spending,
            // With duplicates or no applicable cap there is no authoritative limit to display.
            limit = chosen?.limit,
            fraction = if (issue == null) capFraction(spending, requireNotNull(chosen).limit) else null,
            issue = issue,
            categoryLabel = when {
                id == null -> null
                category == null -> CapCategoryLabel.MISSING
                category.isArchived -> CapCategoryLabel.ARCHIVED
                else -> null
            },
        )
    }
    val ranked = rows.filter { it.categoryId != null }.sortedWith { a, b ->
        when {
            a.fraction != null && b.fraction == null -> -1
            a.fraction == null && b.fraction != null -> 1
            a.fraction != null && b.fraction != null -> {
                // Compare exact cross-products, not rounded percentages or overflowing Longs.
                val order = BigInteger.valueOf(b.spending.amountMinor).multiply(BigInteger.valueOf(a.limit!!.amountMinor))
                    .compareTo(BigInteger.valueOf(a.spending.amountMinor).multiply(BigInteger.valueOf(b.limit!!.amountMinor)))
                if (order != 0) order else a.categoryId!!.compareTo(b.categoryId!!)
            }
            else -> a.categoryId!!.compareTo(b.categoryId!!)
        }
    }.take(3)
    return state(DashboardCapsContent.Ready(CapPreviews(rows.firstOrNull { it.categoryId == null }, ranked)))
}

/** Fraction for presentation only; unavailable caps never become a misleading zero percent. */
internal fun capFraction(spending: Money, limit: Money): BigDecimal? =
    if (limit.amountMinor <= 0 || spending.currencyCode != limit.currencyCode) null
    else BigDecimal.valueOf(spending.amountMinor).divide(BigDecimal.valueOf(limit.amountMinor), MathContext.DECIMAL128)
