package com.example.spendflow.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.catchAsError
import com.example.spendflow.core.common.guarded
import com.example.spendflow.data.repository.*
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.usecase.GetMonthlySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.AppError
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionFeedViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    summaries: GetMonthlySummary,
    goals: GoalRepository,
    capUsage: com.example.spendflow.domain.usecase.ObserveCapUsage,
    private val capWarnings: com.example.spendflow.core.notifications.CapWarnings,
    private val state: androidx.lifecycle.SavedStateHandle,
    budgets: BudgetRepository,
) : ViewModel() {
    val filter = state.getStateFlow("feed_filter", "ALL")
    fun setFilter(value: String) { state["feed_filter"] = value }
    private val refresh = MutableStateFlow(0)
    fun retry() { refresh.value++ }
    val goals = refresh.flatMapLatest { goals.observeGoals().catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val goalPreview = this.goals.map { result ->
        when (result) {
            null -> UiState.Loading
            is AppResult.Error -> UiState.Error(result.error)
            is AppResult.Success -> UiState.Success(com.example.spendflow.domain.model.primaryGoal(result.data))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)
    val pendingIds = transactions.observePendingIds().catch { emit(emptySet()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val history = refresh.flatMapLatest { transactions.observeTransactions(Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59.999999999Z")).catchAsError() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recent = refresh.flatMapLatest { transactions.observeRecent().catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val zone = ZoneId.systemDefault()
    val currentMonth: YearMonth = YearMonth.now(zone)
    val monthly = refresh.flatMapLatest { summaries(currentMonth, zone).catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val homeMonth = state.getStateFlow("home_month", currentMonth.toString())

    fun moveHomeMonth(delta: Long) {
        val next = YearMonth.parse(homeMonth.value).plusMonths(delta)
        if (next <= YearMonth.now(zone)) state["home_month"] = next.toString()
    }

    // Reuse the engine's snapshot presence flag, including transfer-only months. This bounded
    // two-month query also calculates the previous summary; Home does not display comparisons.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    internal val dashboard = combine(homeMonth, refresh) { value, _ -> value }.flatMapLatest { value ->
        val selected = YearMonth.parse(value)
        summaries.observeComparison(selected, zone).catchAsError().map { result ->
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
    val categories = refresh.flatMapLatest { categories.observeCategories(includeArchived = true).catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val expenseBreakdown = combine(dashboard, this.categories, ::dashboardExpenses)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardExpenseState(YearMonth.parse(homeMonth.value)))
    private val budgets = refresh.flatMapLatest { budgets.observeBudgets().catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val capPreviews = combine(dashboard, this.budgets, this.categories) { selected, limits, labels ->
        dashboardCaps(selected, limits, labels, YearMonth.now(zone))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardCapsState(YearMonth.parse(homeMonth.value)))

    val deleting = MutableStateFlow<String?>(null)
    val deleteError = MutableStateFlow<AppError?>(null)
    private val deletionResults = Channel<AppResult<Unit>>(Channel.BUFFERED)
    val deletionEvents = deletionResults.receiveAsFlow()

    fun deleteTransaction(id: String) {
        if (deleting.value != null) return
        deleting.value = id
        deleteError.value = null
        viewModelScope.launch {
            try {
                val result = guarded { transactions.delete(id) }
                deleteError.value = (result as? AppResult.Error)?.error
                deletionResults.send(result)
            } finally { deleting.value = null }
        }
    }

    fun clearDeleteError() { deleteError.value = null }

    // Retain incoming thresholds and current-month observation, but use SF-21 eligibility
    // for both displayed progress and warnings (including caps beyond the preview limit).
    private val capOverview = refresh.flatMapLatest { capUsage().catchAsError() }
    private val warningUsages = combine(dashboard, this.budgets, this.categories, capOverview) { selected, limits, labels, overview ->
        val data = (overview as? AppResult.Success)?.data
        val eligible = if (data == null) emptyList() else
            dashboardWarningUsages(selected, limits, labels, data.month, data.thresholds)
        if (data != null && selected.month == data.month) capWarnings.check(data.month, eligible)
        eligible
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val warnings = combine(warningUsages, capWarnings.pending) { usages, pending ->
        pending.filter { warning -> usages.any { it.cap.id == warning.budgetId && it == warning.usage } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dismissWarning(warning: com.example.spendflow.domain.model.CapWarning) = capWarnings.dismiss(warning)
}
