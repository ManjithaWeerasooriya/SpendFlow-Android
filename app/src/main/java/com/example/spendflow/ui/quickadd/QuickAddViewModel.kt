package com.example.spendflow.ui.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    categories: CategoryRepository,
    users: UserRepository,
    private val transactions: TransactionRepository,
    val draft: SavedStateHandle,
) : ViewModel() {
    val categories = categories.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile = users.observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val results = Channel<AppResult<Unit>>(Channel.BUFFERED)
    val events = results.receiveAsFlow()
    val saving = MutableStateFlow(false)
    var completed = false
        private set

    fun save(amount: String, categoryId: String?, income: Boolean, payment: PaymentMethod?, note: String): Boolean {
        if (saving.value || completed) return false
        val user = (profile.value as? AppResult.Success)?.data ?: return false
        val category = ((categories.value as? AppResult.Success)?.data ?: emptyList()).find {
            it.id == categoryId && it.type == if (income) CategoryType.INCOME else CategoryType.EXPENSE
        } ?: return false
        val money = parseAmount(amount, user.baseCurrency) ?: return false
        val transaction = Transaction(
            id = transactions.newId(), type = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
            amount = money, rate = BigDecimal.ONE, baseAmount = money,
            dateTime = draft.get<String>("date")?.let(Instant::parse) ?: Instant.now(),
            categoryId = category.id, paymentMethod = if (income) null else payment,
            note = note.trim().takeIf { it.isNotEmpty() },
        )
        saving.value = true
        viewModelScope.launch {
            val result = transactions.create(transaction)
            completed = result is AppResult.Success
            saving.value = false
            results.send(result)
        }
        return true
    }

    companion object {
        fun parseAmount(text: String, currency: String): Money? = runCatching {
            val digits = Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
            val minor = BigDecimal(text.trim().replace(',', '.')).movePointRight(digits).longValueExact()
            require(minor in 1..100_000_000_000_000L)
            Money(minor, currency)
        }.getOrNull()
    }
}
