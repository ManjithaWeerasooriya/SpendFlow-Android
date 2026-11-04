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

internal fun AnalyticsViewModel.MonthReport?.contentFor(month: YearMonth): AnalyticsContent =
    when (val result = this?.forMonth(month)) {
        null -> AnalyticsContent.Loading
        is AppResult.Error -> AnalyticsContent.Error(result.error)
        is AppResult.Success -> if (result.data.hasTransactions) AnalyticsContent.Populated(result.data) else AnalyticsContent.Empty
    }
