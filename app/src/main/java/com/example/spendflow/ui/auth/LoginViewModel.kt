package com.example.spendflow.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.onError
import com.example.spendflow.data.session.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginEvent {
    data class ShowError(val error: AppError) : LoginEvent
}

/**
 * Placeholder login: signs in anonymously until the email form (01a) exists.
 * Leaving the screen is MainActivity's job; it follows the session's UID.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: UserSession,
) : ViewModel() {

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun continueAsGuest() {
        if (_signingIn.value) return
        _signingIn.value = true
        viewModelScope.launch {
            session.ensureSignedIn().onError { _events.send(LoginEvent.ShowError(it)) }
            _signingIn.value = false
        }
    }
}
