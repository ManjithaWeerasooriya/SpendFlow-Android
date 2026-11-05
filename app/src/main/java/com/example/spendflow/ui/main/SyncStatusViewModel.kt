package com.example.spendflow.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.network.ConnectivityMonitor
import com.example.spendflow.data.repository.BudgetRepository
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SyncStatus(val offline: Boolean = false, val pendingCount: Int = 0)

/** Offline state and the number of local changes Firestore has not yet synced. */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    connectivity: ConnectivityMonitor,
    transactions: TransactionRepository,
    goals: GoalRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
) : ViewModel() {
    val pendingTransactionIds: StateFlow<Set<String>> = transactions.observePendingIds().catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val status: StateFlow<SyncStatus> = combine(
        connectivity.online,
        pendingTransactionIds,
        goals.observePendingIds().catch { emit(emptySet()) },
        categories.observePendingIds().catch { emit(emptySet()) },
        budgets.observePendingIds().catch { emit(emptySet()) },
    ) { online, tx, g, c, b -> SyncStatus(offline = !online, pendingCount = tx.size + g.size + c.size + b.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus())
}
