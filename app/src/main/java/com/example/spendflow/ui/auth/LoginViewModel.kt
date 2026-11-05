package com.example.spendflow.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { LOG_IN, CREATE_ACCOUNT }

/** Errors are string resources shown under their field (01b); null means the field is fine. */
data class LoginUiState(
    val mode: AuthMode = AuthMode.LOG_IN,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val confirmError: Int? = null,
    val submitting: Boolean = false,
)

sealed interface LoginEvent {
    data class ResetEmailSent(val email: String) : LoginEvent

    /** Failures that belong to no single field, such as no connection. */
    data class ShowError(val error: AppError) : LoginEvent
}

/**
 * One form that switches between log in (01a) and create account (01b). Leaving the screen is
 * MainActivity's job: it follows the session's UID, so success needs no event here.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun setMode(mode: AuthMode) {
        if (_uiState.value.submitting) return
        _uiState.update { if (it.mode == mode) it else LoginUiState(mode = mode) }
    }

    fun onEmailChanged() = _uiState.update { it.copy(emailError = null) }

    fun onPasswordChanged() = _uiState.update { it.copy(passwordError = null) }

    fun onConfirmationChanged() = _uiState.update { it.copy(confirmError = null) }

    fun submit(email: String, password: String, confirmation: String) {
        val state = _uiState.value
        if (state.submitting) return
        val trimmedEmail = email.trim()
        val creating = state.mode == AuthMode.CREATE_ACCOUNT
        val checked = state.copy(
            emailError = CredentialsValidator.email(trimmedEmail),
            passwordError = if (creating) CredentialsValidator.newPassword(password) else CredentialsValidator.loginPassword(password),
            confirmError = if (creating) CredentialsValidator.confirmation(password, confirmation) else null,
        )
        if (checked.emailError != null || checked.passwordError != null || checked.confirmError != null) {
            _uiState.value = checked
            return
        }
        _uiState.value = checked.copy(submitting = true)
        viewModelScope.launch {
            val result = if (creating) auth.register(trimmedEmail, password) else auth.logIn(trimmedEmail, password)
            _uiState.update { it.copy(submitting = false) }
            if (result is AppResult.Error) showError(result.error)
        }
    }

    fun sendPasswordReset(email: String) {
        if (_uiState.value.submitting) return
        val trimmedEmail = email.trim()
        CredentialsValidator.email(trimmedEmail)?.let { error ->
            _uiState.update { it.copy(emailError = error) }
            return
        }
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = auth.sendPasswordReset(trimmedEmail)
            _uiState.update { it.copy(submitting = false) }
            when (result) {
                is AppResult.Success -> _events.send(LoginEvent.ResetEmailSent(trimmedEmail))
                is AppResult.Error -> showError(result.error)
            }
        }
    }

    private suspend fun showError(error: AppError) {
        when (error) {
            AppError.InvalidEmail -> _uiState.update { it.copy(emailError = R.string.auth_error_email_invalid) }
            AppError.EmailInUse -> _uiState.update { it.copy(emailError = R.string.auth_error_email_in_use) }
            AppError.WeakPassword -> _uiState.update { it.copy(passwordError = R.string.auth_error_password_short) }
            AppError.InvalidCredentials ->
                _uiState.update { it.copy(passwordError = R.string.auth_error_invalid_credentials) }
            else -> _events.send(LoginEvent.ShowError(error))
        }
    }
}
