package com.example.spendflow.ui.settings

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
import com.example.spendflow.core.ui.CurrencyOptions
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.data.repository.AuthRepository
import com.example.spendflow.data.repository.PreferencesRepository
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsEvent {
    data class CurrencyChanged(val currencyCode: String) : SettingsEvent
    data class ShowError(val error: AppError) : SettingsEvent
}

internal data class SettingsContent(
    val baseCurrency: String,
    val preferences: UserPreferences,
    /** True when unsure, so a currency change is confirmed rather than silently applied. */
    val hasTransactions: Boolean,
)

/**
 * 09 Settings. Every switch writes straight to the user document; Firestore applies it to the cache first,
 * so [uiState] reflects it at once, offline too. A base-currency change with transactions on record waits in
 * [pendingCurrency] for the user to confirm it.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    users: UserRepository,
    transactions: TransactionRepository,
    private val preferences: PreferencesRepository,
    private val auth: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    private val retries = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val uiState: StateFlow<UiState<SettingsContent>> = retries
        .flatMapLatest {
            combine(users.observeProfile().catchAsError(), transactions.observeRecent(limit = 1).catchAsError()) { profile, recent ->
                when (profile) {
                    is AppResult.Error -> UiState.Error(profile.error)
                    is AppResult.Success -> profile.data?.let { user ->
                        UiState.Success(
                            SettingsContent(
                                baseCurrency = user.baseCurrency,
                                preferences = user.preferences,
                                hasTransactions = recent !is AppResult.Success || recent.data.isNotEmpty(),
                            ),
                        )
                    } ?: UiState.Error(AppError.NotFound)
                }
            }.onStart { emit(UiState.Loading) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    internal val pendingCurrency: StateFlow<String?> = savedState.getStateFlow(PENDING_CURRENCY, null)

    private val content get() = (uiState.value as? UiState.Success)?.data

    fun retry() {
        retries.tryEmit(Unit)
    }

    fun setStreakEnabled(enabled: Boolean) = write { preferences.setStreakEnabled(enabled) }

    fun setLockScreenQuickEntry(enabled: Boolean) = write { preferences.setLockScreenQuickEntry(enabled) }

    /** Applies [currencyCode] at once when there are no transactions, otherwise asks for confirmation first. */
    fun selectBaseCurrency(currencyCode: String) {
        val current = content ?: return
        if (currencyCode == current.baseCurrency || currencyCode !in CurrencyOptions.codes) return
        if (current.hasTransactions) {
            savedState[PENDING_CURRENCY] = currencyCode
        } else {
            changeBaseCurrency(currencyCode)
        }
    }

    fun confirmBaseCurrency() {
        val code = pendingCurrency.value ?: return
        savedState[PENDING_CURRENCY] = null
        changeBaseCurrency(code)
    }

    fun dismissBaseCurrency() {
        savedState[PENDING_CURRENCY] = null
    }

    /** Null when the thresholds are valid and being saved, otherwise the message to show in the form. */
    @StringRes
    fun saveCapThresholds(notice: String, warning: String, over: String): Int? {
        val values = listOf(notice, warning, over).map { it.trim().toIntOrNull() }
        if (values.any { it == null || it !in CapThresholds.RANGE }) return R.string.settings_cap_error_range
        val (first, second, third) = values.map { it!! }
        if (first >= second || second >= third) return R.string.settings_cap_error_order
        write { preferences.setCapThresholds(CapThresholds(first, second, third)) }
        return null
    }

    // MainActivity shows Login as soon as the session ends
    fun logOut() {
        viewModelScope.launch { guarded { auth.logOut() } }
    }

    private fun changeBaseCurrency(code: String) = write(successEvent = { SettingsEvent.CurrencyChanged(code) }) {
        preferences.setBaseCurrency(code)
    }

    private fun write(successEvent: (() -> SettingsEvent)? = null, block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            guarded { block() }
                .onSuccess { successEvent?.let { _events.send(it()) } }
                .onError { _events.send(SettingsEvent.ShowError(it)) }
        }
    }

    private companion object {
        const val PENDING_CURRENCY = "settings_pending_currency"
    }
}
