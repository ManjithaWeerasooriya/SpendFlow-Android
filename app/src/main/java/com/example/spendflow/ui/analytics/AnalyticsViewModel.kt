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
    val month = state.getStateFlow("analytics_month", YearMonth.now(zone).toString())
    val incomeMode = state.getStateFlow("analytics_income", false)
    fun setIncomeMode(income: Boolean) { state["analytics_income"] = income }
    fun moveMonth(delta: Long) {
        val next = YearMonth.parse(month.value).plusMonths(delta)
        if (next <= YearMonth.now(zone)) state["analytics_month"] = next.toString()
    }

    val report: StateFlow<AppResult<AnalyticsReport>?> = month
        .flatMapLatest { value ->
            combine(
                summaries.observeComparison(YearMonth.parse(value), zone),
                categories.observeCategories(includeArchived = true),
            ) { comparison, cats ->
                val result: AppResult<AnalyticsReport>? = when (comparison) {
                    is AppResult.Error -> comparison
                    is AppResult.Success -> AppResult.Success(
                        AnalyticsReport.build(comparison.data, (cats as? AppResult.Success)?.data.orEmpty(), LocalDate.now(zone), zone),
                    )
                }
                result
            }.onStart { emit(null) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
