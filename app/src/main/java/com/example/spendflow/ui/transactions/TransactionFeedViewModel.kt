package com.example.spendflow.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.catchAsError
import com.example.spendflow.core.common.guarded
import com.example.spendflow.data.repository.*
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
) : ViewModel() {
    val filter = state.getStateFlow("feed_filter", "ALL")
    fun setFilter(value: String) { state["feed_filter"] = value }
    private val refresh = MutableStateFlow(0)
    fun retry() { refresh.value++ }
    val goals = refresh.flatMapLatest { goals.observeGoals().catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pendingIds = transactions.observePendingIds().catch { emit(emptySet()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val history = refresh.flatMapLatest { transactions.observeTransactions(Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59.999999999Z")).catchAsError() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recent = refresh.flatMapLatest { transactions.observeRecent().catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val zone = ZoneId.systemDefault()
    val currentMonth: YearMonth = YearMonth.now(zone)
    val monthly = refresh.flatMapLatest { summaries(currentMonth, zone).catchAsError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val categories = categories.observeCategories(includeArchived = true).catchAsError().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
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

    /** Home's Spending caps; collecting it also raises the cap warnings (AC2). Archived categories don't warn. */
    val caps = refresh.flatMapLatest { capUsage().catchAsError() }
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
