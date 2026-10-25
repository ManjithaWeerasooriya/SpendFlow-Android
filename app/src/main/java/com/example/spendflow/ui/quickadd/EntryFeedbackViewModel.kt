package com.example.spendflow.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.AppError
import com.example.spendflow.data.repository.WriteFailures
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedEntry(val transaction: Transaction, val categoryName: String)
data class EntryConfirmation(val entry: SavedEntry, val uid: String, val expiresAt: Long)

/** Activity scoped: the Undo write survives dismissal of Quick Add. */
@HiltViewModel
class EntryFeedbackViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val session: UserSession,
    writeFailures: WriteFailures,
) : ViewModel() {
    private val pending = MutableStateFlow<EntryConfirmation?>(null)
    val confirmation = pending.asStateFlow()
    private val results = Channel<AppResult<Unit>>(Channel.BUFFERED)
    val events = results.receiveAsFlow()

    private val rejections = Channel<AppError>(Channel.BUFFERED)
    val rejectedWrites = rejections.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.uid.collect { uid ->
                if (pending.value?.uid != uid) clear()
            }
        }
        viewModelScope.launch {
            writeFailures.failures.collect { failure ->
                val uid = session.currentUid()
                if (uid != null && failure.path.startsWith("users/$uid/transactions/")) {
                    if (pending.value?.entry?.transaction?.id == failure.path.substringAfterLast('/')) clear()
                    rejections.send(failure.error)
                }
            }
        }
    }

    fun saved(entry: SavedEntry) {
        val uid = session.currentUid() ?: return
        val confirmation = EntryConfirmation(entry, uid, System.currentTimeMillis() + 5000)
        pending.value = confirmation
        viewModelScope.launch {
            delay(5000)
            if (pending.value === confirmation) pending.value = null
        }
    }

    fun clear() { pending.value = null }

    fun undo(confirmation: EntryConfirmation) {
        if (pending.value !== confirmation) return
        pending.value = null
        if (session.currentUid() != confirmation.uid || System.currentTimeMillis() > confirmation.expiresAt) return
        viewModelScope.launch {
            results.send(transactions.delete(confirmation.entry.transaction.id))
        }
    }
}
