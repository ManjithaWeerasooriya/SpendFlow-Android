package com.example.spendflow.ui.categories

import com.example.spendflow.core.common.catchAsError
import com.example.spendflow.core.common.guarded
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.onError
import com.example.spendflow.core.common.onSuccess
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.CurrencyOptions
import com.example.spendflow.core.ui.toUiState
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.repository.WriteFailures
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CategoriesEvent {
    data object CategoryAdded : CategoriesEvent
    data object IncomeSourceSaved : CategoriesEvent
    data class IncomeSourceWriteFailed(val error: AppError) : CategoriesEvent

    /** Input problems the ViewModel catches before anything is saved. */
    data class ShowMessage(@StringRes val messageRes: Int) : CategoriesEvent

    /** A failed operation, including a save the server rejected after it was applied locally. */
    data class ShowError(val error: AppError) : CategoriesEvent
}

internal enum class CategoriesTab { CATEGORIES, INCOME_SOURCES }

internal data class IncomeSourceFormErrors(
    @StringRes val name: Int? = null,
    @StringRes val currency: Int? = null,
)

internal data class CategoriesScreenState(
    val tab: CategoriesTab,
    val content: UiState<List<Category>>,
)

/**
 * Reference screen for the UI -> ViewModel -> Repository structure. The Fragment only renders
 * [uiState], forwards user input to the public functions and reacts to [events].
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
    writeFailures: WriteFailures,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // One-shot effects: delivered once, not replayed to a recreated view like state would be
    private val _events = Channel<CategoriesEvent>(Channel.BUFFERED)
    val events: Flow<CategoriesEvent> = _events.receiveAsFlow()

    private val retries = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<List<Category>>> = retries
        .flatMapLatest {
            categories.observeCategories().catchAsError()
                .map { it.toUiState() }
                .onStart { emit(UiState.Loading) }
        }
        // Keeps the subscription through configuration changes, stops it 5 s after the UI goes away
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    internal val selectedTab = savedState.getStateFlow("categories_tab", CategoriesTab.CATEGORIES)
    internal val screenState: Flow<CategoriesScreenState> = combine(selectedTab, uiState) { tab, state ->
        val content = if (tab == CategoriesTab.INCOME_SOURCES && state is UiState.Success) {
            UiState.Success(state.data.filter { it.type == CategoryType.INCOME })
        } else {
            state
        }
        CategoriesScreenState(tab, content)
    }

    internal fun selectTab(tab: CategoriesTab) {
        savedState["categories_tab"] = tab
    }

    private val _incomeSourceSaving = MutableStateFlow(false)
    internal val incomeSourceSaving = _incomeSourceSaving.asStateFlow()

    internal fun saveIncomeSource(id: String?, name: String, currency: String?): IncomeSourceFormErrors {
        val trimmed = name.trim()
        val errors = IncomeSourceFormErrors(
            name = when {
                trimmed.isBlank() -> R.string.income_sources_name_required
                trimmed.length > 100 -> R.string.income_sources_name_too_long
                else -> null
            },
            currency = if (id == null && (currency == null || currency !in CurrencyOptions.codes)) {
                R.string.income_sources_currency_required
            } else {
                null
            },
        )
        if (errors.name != null || errors.currency != null) return errors

        writeIncomeSource {
            if (id == null) {
                categories.create(Category(categories.newId(), trimmed, CategoryType.INCOME, defaultCurrency = currency))
            } else {
                val source = activeIncomeSource(id) ?: return@writeIncomeSource AppResult.Error(AppError.NotFound)
                categories.update(source.copy(name = trimmed))
            }
        }
        return errors
    }

    internal fun archiveIncomeSource(id: String) {
        writeIncomeSource {
            val source = activeIncomeSource(id) ?: return@writeIncomeSource AppResult.Error(AppError.NotFound)
            categories.update(source.copy(isArchived = true))
        }
    }

    private fun activeIncomeSource(id: String): Category? =
        (uiState.value as? UiState.Success)?.data?.firstOrNull {
            it.id == id && it.type == CategoryType.INCOME && !it.isArchived
        }

    private fun writeIncomeSource(write: suspend () -> AppResult<Unit>) {
        if (_incomeSourceSaving.value) return
        _incomeSourceSaving.value = true
        viewModelScope.launch {
            val result = try {
                guarded { write() }
            } finally {
                _incomeSourceSaving.value = false
            }
            _events.send(when (result) {
                is AppResult.Success -> CategoriesEvent.IncomeSourceSaved
                is AppResult.Error -> CategoriesEvent.IncomeSourceWriteFailed(result.error)
            })
        }
    }

    init {
        viewModelScope.launch {
            writeFailures.failures.collect { _events.send(CategoriesEvent.ShowError(it.error)) }
        }
    }

    fun retry() {
        retries.tryEmit(Unit)
    }

    fun addCategory(name: String, type: CategoryType, defaultCurrency: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _events.trySend(CategoriesEvent.ShowMessage(R.string.categories_error_name_required))
            return
        }
        viewModelScope.launch {
            guarded { categories.create(Category(id = categories.newId(), name = trimmed, type = type, defaultCurrency = if (type == CategoryType.INCOME) defaultCurrency else null)) }
                .onSuccess { _events.send(CategoriesEvent.CategoryAdded) }
                .onError { _events.send(CategoriesEvent.ShowError(it)) }
        }
    }
}
