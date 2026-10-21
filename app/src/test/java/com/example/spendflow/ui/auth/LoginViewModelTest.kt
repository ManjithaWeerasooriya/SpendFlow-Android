package com.example.spendflow.ui.auth

import app.cash.turbine.test
import com.example.spendflow.core.common.AppError
import com.example.spendflow.testing.FakeUserSession
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = FakeUserSession(initialUid = null)

    @Test
    fun `continue signs in`() {
        LoginViewModel(session).continueAsGuest()
        assertEquals("test-user", session.currentUid())
    }

    @Test
    fun `sign-in failure shows error and re-enables continue`() = runTest {
        session.signInError = AppError.Network
        val viewModel = LoginViewModel(session)
        viewModel.events.test {
            viewModel.continueAsGuest()
            assertEquals(LoginEvent.ShowError(AppError.Network), awaitItem())
        }
        assertFalse(viewModel.signingIn.value)
    }
}
