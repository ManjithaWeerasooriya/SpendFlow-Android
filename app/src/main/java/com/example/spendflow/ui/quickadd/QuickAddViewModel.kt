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
    private val exchangeRates: com.example.spendflow.data.repository.ExchangeRates,
) : ViewModel() {
    val categories = categories.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile = users.observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val results = Channel<AppResult<Unit>>(Channel.BUFFERED)
    val events = results.receiveAsFlow()
    val saving = MutableStateFlow(false)
    val errors = MutableStateFlow(EntryErrors())
    var savedEntry: SavedEntry? = null
        private set
    var completed = false
        private set

    fun takeSavedEntry(): SavedEntry? = savedEntry.also { savedEntry = null }

    val rateLoading = MutableStateFlow(false)
    val rateError = MutableStateFlow(false)
    private var rateRequest = 0

    fun selectSource(category: Category) {
        draft["category"] = category.id
        if (category.type == CategoryType.INCOME) {
            category.defaultCurrency?.takeIf { code ->
                runCatching { Currency.getInstance(code).defaultFractionDigits >= 0 }.getOrDefault(false)
            }?.let(::selectCurrency)
        }
    }

    fun selectCurrency(code: String) {
        rateRequest++
        draft["currency"] = code
        val base = (profile.value as? AppResult.Success)?.data?.baseCurrency ?: "LKR"
        draft["rate"] = if (code == base) "1" else exchangeRates.lastRate(code, base)?.toPlainString().orEmpty()
        rateLoading.value = false
        rateError.value = false
    }

    fun editRate(text: String) {
        rateRequest++
        rateLoading.value = false
        draft["rate"] = text
    }

    fun fetchRate() {
        val code = draft.get<String>("currency") ?: return
        val base = (profile.value as? AppResult.Success)?.data?.baseCurrency ?: return
        val request = ++rateRequest
        rateLoading.value = true
        rateError.value = false
        viewModelScope.launch {
            try {
                val rate = exchangeRates.latest(code, base)
                if (request == rateRequest) draft["rate"] = rate.toPlainString()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (request == rateRequest) rateError.value = true
            } finally {
                if (request == rateRequest) rateLoading.value = false
            }
        }
    }

    fun conversion(amount: String, income: Boolean): Pair<Money, BigDecimal>? {
        val base = (profile.value as? AppResult.Success)?.data?.baseCurrency ?: return null
        val currency = if (income) draft.get<String>("currency") ?: base else base
        val money = parseAmount(amount, currency) ?: return null
        val rate = if (currency == base) BigDecimal.ONE else
            draft.get<String>("rate")?.trim()?.replace(',', '.')?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        return runCatching { CurrencyConverter.convert(money, rate, base) to rate }.getOrNull()
    }

    fun save(amount: String, categoryId: String?, income: Boolean, payment: PaymentMethod?, note: String): Boolean {
        if (saving.value || completed) return false
        val user = (profile.value as? AppResult.Success)?.data
        val category = ((categories.value as? AppResult.Success)?.data ?: emptyList()).find {
            it.id == categoryId && !it.isArchived && it.type == if (income) CategoryType.INCOME else CategoryType.EXPENSE
        }
        val currency = if (income) draft.get<String>("currency") ?: user?.baseCurrency ?: "LKR" else user?.baseCurrency ?: "LKR"
        val rateMissing = currency != (user?.baseCurrency ?: "LKR") &&
            (draft.get<String>("rate")?.trim()?.replace(',', '.')?.toBigDecimalOrNull()?.signum() ?: 0) <= 0
        errors.value = EntryErrors(
            amount = EntryValidation.amountError(amount, currency),
            category = if (category == null) if (income) com.example.spendflow.R.string.entry_source_required else com.example.spendflow.R.string.entry_category_required else null,
            rate = if (rateMissing) com.example.spendflow.R.string.entry_rate_required else null,
            general = if (user == null) com.example.spendflow.R.string.entry_not_ready else null,
        )
        if (errors.value.hasErrors || user == null || category == null) return false
        val money = parseAmount(amount, currency) ?: return false
        val converted = conversion(amount, income)
        if (converted == null) {
            errors.value = errors.value.copy(rate = com.example.spendflow.R.string.entry_rate_invalid)
            return false
        }
        val (baseAmount, rate) = converted
        val baseDigits = Currency.getInstance(user.baseCurrency).defaultFractionDigits
        if (BigDecimal.valueOf(baseAmount.amountMinor).movePointLeft(baseDigits) > EntryValidation.maximumAmount) {
            errors.value = errors.value.copy(amount = com.example.spendflow.R.string.entry_amount_large)
            return false
        }
        val transaction = Transaction(
            id = transactions.newId(), type = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
            amount = money, rate = rate, baseAmount = baseAmount,
            dateTime = draft.get<String>("date")?.let(Instant::parse) ?: Instant.now(),
            categoryId = category.id, paymentMethod = if (income) null else payment,
            note = note.trim().takeIf { it.isNotEmpty() },
        )
        saving.value = true
        viewModelScope.launch {
            val result = transactions.create(transaction)
            completed = result is AppResult.Success
            if (completed) {
                savedEntry = SavedEntry(transaction, category.name)
                if (income) exchangeRates.remember(currency, user.baseCurrency, rate)
            }
            saving.value = false
            results.send(result)
        }
        return true
    }

    companion object {
        fun parseAmount(text: String, currency: String): Money? = runCatching {
            require(EntryValidation.amountError(text, currency) == null)
            val digits = Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
            val minor = BigDecimal(text.trim().replace(',', '.')).movePointRight(digits).longValueExact()
            require(minor in 1..100_000_000_000_000L)
            Money(minor, currency)
        }.getOrNull()
    }
}
