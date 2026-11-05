package com.example.spendflow.ui.transactions

import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.MonthlySummary
import java.time.YearMonth

/** Month and content travel together, including while a new month's query is pending. */
internal data class DashboardUiState(
    val month: YearMonth,
    val canMoveNext: Boolean,
    val content: UiState<DashboardSummary> = UiState.Loading,
)

internal data class DashboardSummary(
    val summary: MonthlySummary,
    // From the transaction snapshot, not monetary totals: transfers also make a month populated.
    val hasTransactions: Boolean,
) {
    val incomeSourceCount: Int get() = summary.incomeBySource.size
}
