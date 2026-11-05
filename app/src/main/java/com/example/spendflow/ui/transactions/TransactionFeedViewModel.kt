package com.example.spendflow.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.data.repository.*
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
    capUsage: com.example.spendflow.domain.usecase.ObserveCapUsage,
    private val capWarnings: com.example.spendflow.core.notifications.CapWarnings,
    private val state: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {
    val filter = state.getStateFlow("feed_filter", "ALL")
    fun setFilter(value: String) { state["feed_filter"] = value }
    val goals = goals.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pendingIds = transactions.observePendingIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val history = transactions.observeTransactions(Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59.999999999Z"))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recent = transactions.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val zone = ZoneId.systemDefault()
    val currentMonth: YearMonth = YearMonth.now(zone)
    val monthly = summaries(currentMonth, zone).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val categories = categories.observeCategories(includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Home's Spending caps; collecting it also raises the cap warnings (AC2). Archived categories don't warn. */
    val caps = capUsage()
        .onEach { result ->
            val overview = (result as? com.example.spendflow.core.common.AppResult.Success)?.data ?: return@onEach
            val archived = (this.categories.value as? com.example.spendflow.core.common.AppResult.Success)?.data.orEmpty()
                .filter { it.isArchived }.map { it.id }.toSet()
            capWarnings.check(overview.month, overview.usages.filterKeys { it !in archived }.values)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val warnings = capWarnings.pending

    fun dismissWarning(warning: com.example.spendflow.domain.model.CapWarning) = capWarnings.dismiss(warning)
}
