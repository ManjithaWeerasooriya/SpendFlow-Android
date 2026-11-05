package com.example.spendflow.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.data.repository.*
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.usecase.GetMonthlySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransactionFeedViewModel @Inject constructor(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    summaries: GetMonthlySummary,
    goals: GoalRepository,
    private val state: androidx.lifecycle.SavedStateHandle,
    budgets: BudgetRepository,
) : ViewModel() {
    val filter = state.getStateFlow("feed_filter", "ALL")
    fun setFilter(value: String) { state["feed_filter"] = value }
    val goals = goals.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val goalPreview = this.goals.map { result ->
        when (result) {
            null -> UiState.Loading
            is AppResult.Error -> UiState.Error(result.error)
            is AppResult.Success -> UiState.Success(result.data.sortedBy { it.isCompleted }.firstOrNull())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)
    val history = transactions.observeTransactions(Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59.999999999Z"))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recent = transactions.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val zone = ZoneId.systemDefault()
    val currentMonth: YearMonth = YearMonth.now(zone)
    val monthly = summaries(currentMonth, zone).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val homeMonth = state.getStateFlow("home_month", currentMonth.toString())

    fun moveHomeMonth(delta: Long) {
        val next = YearMonth.parse(homeMonth.value).plusMonths(delta)
        if (next <= YearMonth.now(zone)) state["home_month"] = next.toString()
    }

    // Reuse the engine's snapshot presence flag, including transfer-only months. This bounded
    // two-month query also calculates the previous summary; Home does not display comparisons.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    internal val dashboard = homeMonth.flatMapLatest { value ->
        val selected = YearMonth.parse(value)
        summaries.observeComparison(selected, zone).map { result ->
            val content = when (result) {
                is AppResult.Error -> UiState.Error(result.error)
                is AppResult.Success -> UiState.Success(
                    DashboardSummary(result.data.current, result.data.hasCurrentTransactions),
                )
            }
            DashboardUiState(selected, selected < YearMonth.now(zone), content)
        }.onStart { emit(DashboardUiState(selected, selected < YearMonth.now(zone))) }
    }.buffer(0).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        YearMonth.parse(homeMonth.value).let { DashboardUiState(it, it < YearMonth.now(zone)) },
    )
    val categories = categories.observeCategories(includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val budgets = budgets.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val capPreviews = combine(dashboard, this.budgets, this.categories) { selected, limits, labels ->
        dashboardCaps(selected, limits, labels, YearMonth.now(zone))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardCapsState(YearMonth.parse(homeMonth.value)))
}
