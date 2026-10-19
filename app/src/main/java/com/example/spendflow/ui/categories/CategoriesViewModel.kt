package com.example.spendflow.ui.categories

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.onError
import com.example.spendflow.core.common.onSuccess
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.core.ui.toUiState
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CategoriesEvent {
    data object CategoryAdded : CategoriesEvent
    data class ShowMessage(@StringRes val messageRes: Int) : CategoriesEvent
}

/**
 * Reference screen for the UI -> ViewModel -> Repository structure. The Fragment only renders
 * [uiState], forwards user input to the public functions and reacts to [events].
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val session: UserSession,
    private val categories: CategoryRepository,
) : ViewModel() {

    private val retries = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<List<Category>>> = retries
        .flatMapLatest {
            flow {
                emit(UiState.Loading)
                when (val signIn = session.ensureSignedIn()) {
                    is AppResult.Error -> emit(UiState.Error(signIn.error))
                    is AppResult.Success -> emitAll(categories.observeCategories().map { it.toUiState() })
                }
            }
        }
        // Keeps the subscription through configuration changes, stops it 5 s after the UI goes away
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    // One-shot effects: delivered once, not replayed to a recreated view like state would be
    private val _events = Channel<CategoriesEvent>(Channel.BUFFERED)
    val events: Flow<CategoriesEvent> = _events.receiveAsFlow()

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
                .onError { _events.send(CategoriesEvent.ShowMessage(it.messageRes)) }
        }
    }
}
