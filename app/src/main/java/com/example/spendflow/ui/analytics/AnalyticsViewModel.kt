package com.example.spendflow.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    transactions: TransactionRepository, categories: CategoryRepository, users: UserRepository,
    private val state: SavedStateHandle,
) : ViewModel() {
    val month = state.getStateFlow("analytics_month", YearMonth.now().toString())
    val incomeMode = state.getStateFlow("analytics_income", false)
    fun setIncomeMode(income: Boolean) { state["analytics_income"] = income }
    fun moveMonth(delta: Long) {
        val next = YearMonth.parse(month.value).plusMonths(delta)
        if (next <= YearMonth.now()) state["analytics_month"] = next.toString()
    }

    val report: StateFlow<AppResult<AnalyticsReport>?> = combine(month, users.observeProfile()) { m, user -> m to user }
        .flatMapLatest { (value, profile) ->
            if (profile is AppResult.Error) flowOf<AppResult<AnalyticsReport>?>(profile)
            else {
                val user = (profile as AppResult.Success).data ?: return@flatMapLatest flowOf<AppResult<AnalyticsReport>?>(null)
                val selected = YearMonth.parse(value)
                val zone = ZoneId.systemDefault()
                combine(
                    transactions.observeTransactions(selected.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant(), selected.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()),
                    categories.observeCategories(includeArchived = true),
                ) { rows, cats ->
                    when (rows) {
                        is AppResult.Error -> rows
                        is AppResult.Success -> AppResult.Success(AnalyticsReport.build(selected, user.baseCurrency, rows.data, (cats as? AppResult.Success)?.data.orEmpty()))
                    } as AppResult<AnalyticsReport>?
                }.onStart { emit(null) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
