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
    val month = state.getStateFlow("analytics_month", currentMonth().toString())
    val incomeMode = state.getStateFlow("analytics_income", false)
    fun setIncomeMode(income: Boolean) { state["analytics_income"] = income }

    internal fun canMoveNext(): Boolean = YearMonth.parse(month.value) < currentMonth()

    fun moveMonth(delta: Long) {
        val next = YearMonth.parse(month.value).plusMonths(delta)
        if (next <= currentMonth()) state["analytics_month"] = next.toString()
    }

    internal data class MonthReport(val month: YearMonth, val result: AppResult<AnalyticsReport>) {
        fun forMonth(selected: YearMonth): AppResult<AnalyticsReport>? = result.takeIf { month == selected }
    }

    internal val report: StateFlow<MonthReport?> = month
        .flatMapLatest { value ->
            val selected = YearMonth.parse(value)
            combine(
                summaries.observeComparison(selected, zone),
                categories.observeCategories(includeArchived = true),
            ) { comparison, cats ->
                val result = when (comparison) {
                    is AppResult.Error -> comparison
                    is AppResult.Success -> AppResult.Success(
                        AnalyticsReport.build(comparison.data, (cats as? AppResult.Success)?.data.orEmpty(), LocalDate.now(zone), zone),
                    )
                }
                val monthReport: MonthReport? = MonthReport(selected, result)
                monthReport
            }.onStart { emit(null) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
