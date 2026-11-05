package com.example.spendflow.ui.analytics

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import java.time.YearMonth

internal sealed interface AnalyticsContent {
    data object Loading : AnalyticsContent
    data class Error(val error: AppError) : AnalyticsContent
    data object Empty : AnalyticsContent
    data class Populated(val report: AnalyticsReport) : AnalyticsContent {
        val showIncomeRequired: Boolean
            get() = report.summary.totalIncome.amountMinor == 0L && report.summary.expensesByCategory.isNotEmpty()
    }
}

internal data class AnalyticsUiState(
    val month: YearMonth,
    val canMoveNext: Boolean,
    val incomeMode: Boolean,
    val content: AnalyticsContent,
)

internal fun AppResult<AnalyticsReport>.toAnalyticsContent(): AnalyticsContent =
    when (this) {
        is AppResult.Error -> AnalyticsContent.Error(error)
        is AppResult.Success -> if (data.hasTransactions) AnalyticsContent.Populated(data) else AnalyticsContent.Empty
    }
