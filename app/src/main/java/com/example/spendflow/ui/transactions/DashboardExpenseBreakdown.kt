package com.example.spendflow.ui.transactions

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.Money
import java.math.BigDecimal
import java.math.MathContext
import java.time.YearMonth

internal enum class ExpenseCategoryLabel { NAMED, ARCHIVED, UNKNOWN, PENDING }
internal data class ExpenseCategorySlice(
    val categoryId: String?,
    val name: String?,
    val label: ExpenseCategoryLabel,
    val amount: Money,
    val fraction: BigDecimal,
    val colorIndex: Int,
)
internal data class ExpenseBreakdown(
    val total: Money,
    val slices: List<ExpenseCategorySlice>,
    val metadataLoading: Boolean,
    val metadataError: AppError?,
)
internal data class DashboardExpenseState(val month: YearMonth, val content: UiState<ExpenseBreakdown> = UiState.Loading)

/** Stable across ranks, renames, months and process recreation; null has its own namespace. */
internal fun expenseCategoryColorIndex(id: String?): Int =
    Math.floorMod((if (id == null) "uncategorized" else "category:$id").hashCode(), 5)

/** A projection of SF-15's grouped base amounts, with no new query or transaction aggregation. */
internal fun dashboardExpenses(selected: DashboardUiState, categories: AppResult<List<Category>>?): DashboardExpenseState {
    val content = when (val result = selected.content) {
        UiState.Loading -> UiState.Loading
        is UiState.Error -> UiState.Error(result.error)
        is UiState.Success -> {
            val summary = result.data.summary
            val total = summary.totalExpenses
            val names = (categories as? AppResult.Success)?.data.orEmpty().associateBy { it.id }
            val rows = summary.expensesByCategory.map { (id, amount) ->
                val category = names[id]
                ExpenseCategorySlice(
                    id, category?.name,
                    when {
                        category?.isArchived == true -> ExpenseCategoryLabel.ARCHIVED
                        category != null -> ExpenseCategoryLabel.NAMED
                        categories !is AppResult.Success -> ExpenseCategoryLabel.PENDING
                        else -> ExpenseCategoryLabel.UNKNOWN
                    },
                    amount,
                    if (total.amountMinor == 0L) BigDecimal.ZERO else BigDecimal.valueOf(amount.amountMinor)
                        .divide(BigDecimal.valueOf(total.amountMinor), MathContext.DECIMAL128),
                    expenseCategoryColorIndex(id),
                )
            }.sortedWith(compareByDescending<ExpenseCategorySlice> { it.amount.amountMinor }
                .thenBy { it.categoryId != null }.thenBy { it.categoryId.orEmpty() })
            UiState.Success(ExpenseBreakdown(total, rows, categories == null, (categories as? AppResult.Error)?.error))
        }
    }
    return DashboardExpenseState(selected.month, content)
}
