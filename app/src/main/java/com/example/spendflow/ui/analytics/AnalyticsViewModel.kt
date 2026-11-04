package com.example.spendflow.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.domain.usecase.GetMonthlySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    summaries: GetMonthlySummary,
    categories: CategoryRepository,
    private val state: SavedStateHandle,
) : ViewModel() {
    internal val zone: ZoneId = ZoneId.systemDefault()
    private fun currentMonth(): YearMonth = YearMonth.now(zone)
    private val month = state.getStateFlow("analytics_month", currentMonth().toString())
    private val incomeMode = state.getStateFlow("analytics_income", false)
    fun setIncomeMode(income: Boolean) { state["analytics_income"] = income }

    fun moveMonth(delta: Long) {
        val next = YearMonth.parse(month.value).plusMonths(delta)
        if (next <= currentMonth()) state["analytics_month"] = next.toString()
    }

    internal val uiState: StateFlow<AnalyticsUiState> = month
        .flatMapLatest { value ->
            val selected = YearMonth.parse(value)
            val content = combine(
                summaries.observeComparison(selected, zone),
                categories.observeCategories(includeArchived = true)
                    .map { (it as? AppResult.Success)?.data.orEmpty() }
                    .onStart { emit(emptyList()) },
            ) { comparison, cats ->
                val result: AppResult<AnalyticsReport> = when (comparison) {
                    is AppResult.Error -> comparison
                    is AppResult.Success -> AppResult.Success(
                        AnalyticsReport.build(comparison.data, cats, LocalDate.now(zone), zone),
                    )
                }
                result.toAnalyticsContent()
            }
            combine(content.onStart { emit(AnalyticsContent.Loading) }, incomeMode) { report, income ->
                AnalyticsUiState(selected, selected < currentMonth(), income, report)
            }
        }
        // Drop the previous month's in-flight work when the selection changes.
        .buffer(0)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            YearMonth.parse(month.value).let { selected ->
                AnalyticsUiState(selected, selected < currentMonth(), incomeMode.value, AnalyticsContent.Loading)
            },
        )
}
