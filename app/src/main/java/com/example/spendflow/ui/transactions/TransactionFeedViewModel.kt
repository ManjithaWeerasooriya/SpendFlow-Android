package com.example.spendflow.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class TransactionFeedViewModel @Inject constructor(transactions: TransactionRepository, categories: CategoryRepository, users: UserRepository, goals: GoalRepository, private val state: androidx.lifecycle.SavedStateHandle) : ViewModel() {
    val filter = state.getStateFlow("feed_filter", "ALL")
    fun setFilter(value: String) { state["feed_filter"] = value }
    val goals = goals.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val history = transactions.observeTransactions(Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59.999999999Z"))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recent = transactions.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val monthly = transactions.observeMonth(YearMonth.now()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val categories = categories.observeCategories(includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val profile = users.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
