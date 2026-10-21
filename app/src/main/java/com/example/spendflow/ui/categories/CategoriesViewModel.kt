package com.example.spendflow.ui.categories

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.onError
import com.example.spendflow.core.common.onSuccess
import com.example.spendflow.core.ui.UiState
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CategoriesEvent {
    data object CategoryAdded : CategoriesEvent

    /** Input problems the ViewModel catches before anything is saved. */
    data class ShowMessage(@StringRes val messageRes: Int) : CategoriesEvent

    /** A failed operation, including a save the server rejected after it was applied locally. */
    data class ShowError(val error: AppError) : CategoriesEvent
}

/**
 * Reference screen for the UI -> ViewModel -> Repository structure. The Fragment only renders
 * [uiState], forwards user input to the public functions and reacts to [events].
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
    writeFailures: WriteFailures,
) : ViewModel() {

    // One-shot effects: delivered once, not replayed to a recreated view like state would be
    private val _events = Channel<CategoriesEvent>(Channel.BUFFERED)
    val events: Flow<CategoriesEvent> = _events.receiveAsFlow()

    private val retries = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<List<Category>>> = retries
        .flatMapLatest {
            categories.observeCategories()
                .map { it.toUiState() }
                .onStart { emit(UiState.Loading) }
        }
        // Keeps the subscription through configuration changes, stops it 5 s after the UI goes away
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        viewModelScope.launch {
            writeFailures.failures.collect { _events.send(CategoriesEvent.ShowError(it.error)) }
        }
    }

    fun retry() {
        retries.tryEmit(Unit)
    }

    fun addCategory(name: String, type: CategoryType) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _events.trySend(CategoriesEvent.ShowMessage(R.string.categories_error_name_required))
            return
        }
        viewModelScope.launch {
            categories.create(Category(id = categories.newId(), name = trimmed, type = type))
                .onSuccess { _events.send(CategoriesEvent.CategoryAdded) }
                .onError { _events.send(CategoriesEvent.ShowError(it)) }
        }
    }
}
