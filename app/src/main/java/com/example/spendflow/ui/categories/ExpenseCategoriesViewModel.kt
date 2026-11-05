package com.example.spendflow.ui.categories

import com.example.spendflow.core.common.catchAsError
import com.example.spendflow.core.common.guarded
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.notifications.CapWarnings
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.domain.usecase.CapOverview
import com.example.spendflow.domain.usecase.ObserveCapUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExpenseCategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val budgets: BudgetRepository,
    users: UserRepository,
    capUsage: ObserveCapUsage,
    private val capWarnings: CapWarnings,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories = refresh.flatMapLatest { repository.observeCategories(CategoryType.EXPENSE, includeArchived = true).catchAsError() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val caps = budgets.observeBudgets().catchAsError().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** This month's spending and cap usage per category; it also raises the cap warnings shown here and on Home. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val capOverview = refresh.flatMapLatest { capUsage().catchAsError() }
        .onEach { result -> (result as? AppResult.Success)?.data?.let(::checkWarnings) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val warnings = capWarnings.pending

    fun dismissWarning(warning: CapWarning) = capWarnings.dismiss(warning)

    // Archived categories keep their caps but don't warn
    private fun checkWarnings(overview: CapOverview) {
        val archived = (categories.value as? AppResult.Success)?.data.orEmpty().filter { it.isArchived }.map { it.id }.toSet()
        capWarnings.check(overview.month, overview.usages.filterKeys { it !in archived }.values)
    }
    private val profile = users.observeProfile().catchAsError().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val saving = MutableStateFlow(false)
    private val results = Channel<AppResult<Unit>>(Channel.BUFFERED)
    val events = results.receiveAsFlow()

    fun retry() { refresh.value++ }

    fun nameError(name: String, id: String? = null): Int? = when {
        name.trim().isEmpty() -> R.string.categories_error_name_required
        name.trim().length > 100 -> R.string.income_sources_name_too_long
        (categories.value as? AppResult.Success)?.data?.any {
            it.id != id && it.name.trim().equals(name.trim(), ignoreCase = true)
        } == true -> R.string.category_duplicate
        else -> null
    }

    fun save(id: String?, name: String, icon: String, classification: String): Int? {
        nameError(name, id)?.let { return it }
        if (categories.value !is AppResult.Success) return R.string.entry_not_ready
        if (icon !in CategoryAppearance.icons || classification !in CategoryAppearance.classifications) return R.string.entry_not_ready
        val items = (categories.value as AppResult.Success).data
        val old = items.firstOrNull { it.id == id }
        if (id != null && old == null) return R.string.entry_not_ready
        val category = old?.copy(name = name.trim(), iconName = icon, classification = classification)
            ?: Category(repository.newId(), name.trim(), CategoryType.EXPENSE, iconName = icon,
                classification = classification, sortOrder = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        write { if (old == null) repository.create(category) else repository.update(category) }
        return null
    }

    fun archive(category: Category, archived: Boolean) = write {
        when (val result = repository.get(category.id)) {
            is AppResult.Success -> repository.update(result.data.copy(isArchived = archived))
            is AppResult.Error -> result
        }
    }

    fun reorder(ids: List<String>) {
        val items = (categories.value as? AppResult.Success)?.data.orEmpty().filterNot { it.isArchived }
        if (ids.toSet() != items.map { it.id }.toSet() || ids.size != items.size) return
        write {
            // Retain an independent order for income sources. Only expense records are changed.
            for ((index, id) in ids.withIndex()) {
                val item = items.first { it.id == id }
                val result = repository.update(item.copy(sortOrder = index.toLong()))
                if (result is AppResult.Error) return@write result
            }
            AppResult.Success(Unit)
        }
    }

    fun cap(category: Category): Budget? =
        (caps.value as? AppResult.Success)?.data?.let { CategoryCaps.active(it)[category.id] }

    /**
     * Saves a monthly cap in the base currency, alerting at [alerts]. Returns the message to show under the
     * amount, or null when saved. Each category keeps one cap, so an existing one is updated.
     */
    fun saveCap(category: Category, text: String, alerts: Set<CapLevel> = CapLevel.ALERTS): Int? {
        val currency = (profile.value as? AppResult.Success)?.data?.baseCurrency ?: return R.string.entry_not_ready
        if (caps.value !is AppResult.Success) return R.string.entry_not_ready
        capAmountError(text, currency)?.let { return it }
        val money = com.example.spendflow.ui.quickadd.QuickAddViewModel.parseAmount(text, currency)
            ?: return R.string.category_cap_invalid
        val old = cap(category)
        val budget = old?.copy(limit = money, alerts = alerts) ?: Budget(budgets.newId(), category.name, money,
            BudgetPeriod.MONTHLY, LocalDate.now().withDayOfMonth(1), category.id, alerts)
        write { if (old == null) budgets.create(budget) else budgets.update(budget) }
        return null
    }

    /** Deletes every monthly cap on [category], including duplicates left by older versions. */
    fun removeCap(category: Category) {
        val existing = (caps.value as? AppResult.Success)?.data.orEmpty()
            .filter { it.categoryId == category.id && it.period == BudgetPeriod.MONTHLY }
        if (existing.isEmpty()) return
        write {
            existing.forEach { cap ->
                val result = budgets.delete(cap.id)
                if (result is AppResult.Error) return@write result
            }
            AppResult.Success(Unit)
        }
    }

    private fun write(operation: suspend () -> AppResult<Unit>) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { results.send(guarded { operation() }) } finally { saving.value = false }
        }
    }
}

/** AC1: empty, zero and negative caps get their own inline message; anything else unparseable is invalid. */
internal fun capAmountError(text: String, currency: String): Int? {
    val trimmed = text.trim().replace(',', '.')
    if (trimmed.isEmpty()) return R.string.category_cap_required
    val value = trimmed.toBigDecimalOrNull() ?: return R.string.category_cap_invalid
    if (value.signum() <= 0) return R.string.category_cap_positive
    val digits = java.util.Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    if (value.stripTrailingZeros().scale() > digits) return R.string.category_cap_invalid
    return null
}
