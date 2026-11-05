package com.example.spendflow.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
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
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories = refresh.flatMapLatest { repository.observeCategories(CategoryType.EXPENSE, includeArchived = true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val caps = budgets.observeBudgets().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val profile = users.observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, null)
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

    fun cap(category: Category): Budget? = (caps.value as? AppResult.Success)?.data?.firstOrNull {
        it.categoryId == category.id && it.period == BudgetPeriod.MONTHLY
    }

    fun saveCap(category: Category, text: String): Int? {
        val currency = (profile.value as? AppResult.Success)?.data?.baseCurrency ?: return R.string.entry_not_ready
        if (caps.value !is AppResult.Success) return R.string.entry_not_ready
        val money = com.example.spendflow.ui.quickadd.QuickAddViewModel.parseAmount(text, currency)
            ?: return R.string.category_cap_invalid
        val old = cap(category)
        val budget = old?.copy(limit = money) ?: Budget(budgets.newId(), category.name, money,
            BudgetPeriod.MONTHLY, LocalDate.now().withDayOfMonth(1), category.id)
        write { if (old == null) budgets.create(budget) else budgets.update(budget) }
        return null
    }

    private fun write(operation: suspend () -> AppResult<Unit>) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { results.send(operation()) } finally { saving.value = false }
        }
    }
}
