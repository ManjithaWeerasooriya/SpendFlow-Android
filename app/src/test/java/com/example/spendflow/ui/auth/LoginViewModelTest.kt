package com.example.spendflow.ui.auth

import app.cash.turbine.test
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.testing.FakeAuthRepository
import com.example.spendflow.testing.FakeUserSession
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = FakeUserSession(initialUid = null)
    private val auth = FakeAuthRepository(session)
    private val viewModel = LoginViewModel(auth)

    private val state get() = viewModel.uiState.value

    private fun createAccountMode() = viewModel.setMode(AuthMode.CREATE_ACCOUNT)

    @Test
    fun `starts in log in mode with no errors`() {
        assertEquals(LoginUiState(), state)
    }

    @Test
    fun `valid log in signs in with the trimmed email`() {
        viewModel.submit("  name@mail.com ", "secret", "")
        assertEquals(listOf("name@mail.com" to "secret"), auth.loggedIn)
        assertEquals("test-user", session.currentUid())
        assertFalse(state.submitting)
    }

    @Test
    fun `invalid log in input shows field errors and calls nothing`() {
        viewModel.submit("david@mail", "", "")
        assertEquals(R.string.auth_error_email_invalid, state.emailError)
        assertEquals(R.string.auth_error_password_required, state.passwordError)
        assertTrue(auth.loggedIn.isEmpty())
    }

    @Test
    fun `log in does not apply the new-password length rule`() {
        viewModel.submit("name@mail.com", "123", "")
        assertNull(state.passwordError)
        assertEquals(1, auth.loggedIn.size)
    }

    @Test
    fun `wrong credentials show one generic message under the password`() {
        auth.error = AppError.InvalidCredentials
        viewModel.submit("name@mail.com", "wrong-password", "")
        assertNull(state.emailError)
        assertEquals(R.string.auth_error_invalid_credentials, state.passwordError)
        assertNull(session.currentUid())
    }

    @Test
    fun `valid registration creates the account`() {
        createAccountMode()
        viewModel.submit("name@mail.com", "secret1", "secret1")
        assertEquals(listOf("name@mail.com" to "secret1"), auth.registered)
        assertTrue(auth.onboardingPending.value)
    }

    @Test
    fun `registration checks length and confirmation without creating an account`() {
        createAccountMode()
        viewModel.submit("name@mail.com", "12345", "1234")
        assertNull(state.emailError)
        assertEquals(R.string.auth_error_password_short, state.passwordError)
        assertEquals(R.string.auth_error_confirm_mismatch, state.confirmError)
        assertTrue(auth.registered.isEmpty())
    }

    @Test
    fun `email already in use shows under the email field`() {
        auth.error = AppError.EmailInUse
        createAccountMode()
        viewModel.submit("name@mail.com", "secret1", "secret1")
        assertEquals(R.string.auth_error_email_in_use, state.emailError)
        assertNull(session.currentUid())
    }

    @Test
    fun `weak password from the server shows under the password field`() {
        auth.error = AppError.WeakPassword
        createAccountMode()
        viewModel.submit("name@mail.com", "secret1", "secret1")
        assertEquals(R.string.auth_error_password_short, state.passwordError)
    }

    @Test
    fun `errors that belong to no field are sent as an event`() = runTest {
        auth.error = AppError.Network
        viewModel.events.test {
            viewModel.submit("name@mail.com", "secret", "")
            assertEquals(LoginEvent.ShowError(AppError.Network), awaitItem())
        }
        assertEquals(LoginUiState(), state)
    }

    @Test
    fun `editing a field clears only its error`() {
        createAccountMode()
        viewModel.submit("", "", "")
        viewModel.onEmailChanged()
        assertNull(state.emailError)
        assertEquals(R.string.auth_error_password_required, state.passwordError)
        assertEquals(R.string.auth_error_confirm_required, state.confirmError)
    }

    @Test
    fun `switching mode clears errors`() {
        viewModel.submit("", "", "")
        createAccountMode()
        assertEquals(LoginUiState(mode = AuthMode.CREATE_ACCOUNT), state)
    }

    @Test
    fun `a second tap while submitting is ignored`() {
        auth.gate = CompletableDeferred()
        viewModel.submit("name@mail.com", "secret", "")
        assertTrue(state.submitting)
        viewModel.submit("name@mail.com", "secret", "")
        viewModel.setMode(AuthMode.CREATE_ACCOUNT)
        assertEquals(AuthMode.LOG_IN, state.mode)

        auth.gate!!.complete(Unit)
        assertEquals(1, auth.loggedIn.size)
        assertFalse(state.submitting)
    }

    @Test
    fun `password reset needs a valid email first`() {
        viewModel.sendPasswordReset("david@mail")
        assertEquals(R.string.auth_error_email_invalid, state.emailError)
        assertTrue(auth.resetRequests.isEmpty())
    }

    @Test
    fun `password reset confirms with the same message for any valid email`() = runTest {
        viewModel.events.test {
            viewModel.sendPasswordReset(" name@mail.com ")
            assertEquals(LoginEvent.ResetEmailSent("name@mail.com"), awaitItem())
        }
        assertEquals(listOf("name@mail.com"), auth.resetRequests)
    }
}
