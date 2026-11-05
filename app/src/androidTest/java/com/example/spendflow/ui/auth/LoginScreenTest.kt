package com.example.spendflow.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Login form: mode switching, typed values reaching submit, errors and the busy state. */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private var state by mutableStateOf(LoginUiState())
    private val submitted = mutableListOf<Triple<String, String, String>>()
    private val resetRequests = mutableListOf<String>()

    private fun show(initial: LoginUiState = LoginUiState()) {
        state = initial
        compose.setContent {
            SpendFlowTheme {
                LoginScreen(
                    state = state,
                    onModeChange = { state = LoginUiState(mode = it) },
                    onEmailChange = { state = state.copy(emailError = null) },
                    onPasswordChange = {},
                    onConfirmationChange = {},
                    onSubmit = { e, p, c -> submitted += Triple(e, p, c) },
                    onForgotPassword = { resetRequests += it },
                )
            }
        }
    }

    private fun fields() = compose.onAllNodes(hasSetTextAction())

    @Test
    fun logInSubmitsTypedValuesFromButtonAndKeyboard() {
        show()
        fields().assertCountEquals(2)
        fields()[0].performTextInput("name@mail.com")
        fields()[1].performTextInput("secret1")
        compose.onNode(hasText(text(R.string.auth_mode_log_in)) and hasClickAction()).performClick()
        fields()[1].performImeAction()
        assertEquals(List(2) { Triple("name@mail.com", "secret1", "") }, submitted)
    }

    @Test
    fun forgotPasswordSendsTheTypedEmail() {
        show()
        fields()[0].performTextInput("name@mail.com")
        compose.onNodeWithText(text(R.string.auth_forgot_password)).performClick()
        assertEquals(listOf("name@mail.com"), resetRequests)
    }

    @Test
    fun createAccountLinkShowsConfirmationAndKeepsTypedEmail() {
        show()
        fields()[0].performTextInput("name@mail.com")
        compose.onNodeWithText(text(R.string.auth_mode_create_account)).performClick()
        compose.onNodeWithText(text(R.string.register_title)).assertExists()
        compose.onNodeWithText(text(R.string.auth_forgot_password)).assertDoesNotExist()
        fields().assertCountEquals(3)
        fields()[1].performTextInput("secret1")
        fields()[2].performTextInput("secret1")
        fields()[2].performImeAction()
        assertEquals(listOf(Triple("name@mail.com", "secret1", "secret1")), submitted)
    }

    @Test
    fun fieldErrorsShowUnderTheirFieldAndClearWhenEditing() {
        show(LoginUiState(emailError = R.string.auth_error_email_invalid, passwordError = R.string.auth_error_invalid_credentials))
        compose.onNodeWithText(text(R.string.auth_error_email_invalid)).assertExists()
        compose.onNodeWithText(text(R.string.auth_error_invalid_credentials)).assertExists()
        fields()[0].performTextInput("x")
        compose.onNodeWithText(text(R.string.auth_error_email_invalid)).assertDoesNotExist()
    }

    @Test
    fun submittingDisablesActions() {
        show(LoginUiState(submitting = true))
        compose.onNode(hasText(text(R.string.auth_mode_log_in)) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.auth_forgot_password)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.auth_mode_create_account)).assertIsNotEnabled()
    }
}
