package com.example.spendflow.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendflow.R
import com.example.spendflow.core.ui.EventEffect
import com.example.spendflow.core.ui.components.LabeledTextField
import com.example.spendflow.core.ui.components.PrimaryButton
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.components.SpendFlowSnackbarVisuals
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.ui.theme.SpendFlowTheme
import kotlinx.coroutines.launch

@Composable
internal fun LoginRoute(viewModel: LoginViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    EventEffect(viewModel.events) { event ->
        val visuals = when (event) {
            is LoginEvent.ResetEmailSent -> SpendFlowSnackbarVisuals(
                context.getString(R.string.auth_reset_sent, event.email), SpendFlowSnackbar.Tone.SUCCESS,
                duration = SnackbarDuration.Long,
            )
            is LoginEvent.ShowError -> SpendFlowSnackbarVisuals(
                context.getString(event.error.messageRes), SpendFlowSnackbar.Tone.ERROR,
                duration = SnackbarDuration.Long,
            )
        }
        // A newer message replaces the one on screen, as View snackbars did
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(visuals) }
    }
    LoginScreen(
        state = state,
        snackbar = snackbar,
        onModeChange = viewModel::setMode,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onConfirmationChange = viewModel::onConfirmationChanged,
        onSubmit = viewModel::submit,
        onForgotPassword = viewModel::sendPasswordReset,
    )
}

/**
 * Log in (01a) and create account (01b) as one form. The typed text lives here; the ViewModel validates
 * on submit and reports field errors through [state].
 */
@Composable
internal fun LoginScreen(
    state: LoginUiState,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: () -> Unit,
    onPasswordChange: () -> Unit,
    onConfirmationChange: () -> Unit,
    onSubmit: (email: String, password: String, confirmation: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val creating = state.mode == AuthMode.CREATE_ACCOUNT
    val enabled = !state.submitting
    val colors = SpendFlowTheme.colors
    val submit = { onSubmit(email, password, confirmation) }

    // MainActivity already pads the Fragment host for system bars and the keyboard.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) { SpendFlowSnackbar(it) } },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(
                        start = dimensionResource(R.dimen.sf_screen_padding),
                        end = dimensionResource(R.dimen.sf_screen_padding),
                        top = dimensionResource(R.dimen.sf_space_xxl),
                        bottom = dimensionResource(R.dimen.sf_space_l),
                    ),
            ) {
                if (state.submitting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                if (creating) {
                    ModeToggle(state.mode, enabled, onModeChange)
                } else {
                    Image(
                        painterResource(R.drawable.logo_spendflow_mark),
                        contentDescription = null,
                        Modifier
                            .padding(top = dimensionResource(R.dimen.sf_space_xxxl))
                            .size(56.dp),
                    )
                }
                Text(
                    stringResource(if (creating) R.string.register_title else R.string.login_title),
                    Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_xxl))
                        .semantics { heading() },
                    color = colors.textHeading,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(if (creating) R.string.register_message else R.string.login_message),
                    Modifier.padding(top = dimensionResource(R.dimen.sf_space_xs)),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
                LabeledTextField(
                    label = stringResource(R.string.auth_email_label),
                    value = email,
                    onValueChange = { email = it; onEmailChange() },
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
                    fieldModifier = Modifier.semantics { contentType = ContentType.EmailAddress },
                    placeholder = stringResource(R.string.auth_email_placeholder),
                    error = state.emailError?.let { stringResource(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                PasswordField(
                    label = R.string.auth_password_label,
                    value = password,
                    onValueChange = { password = it; onPasswordChange() },
                    contentType = if (creating) ContentType.NewPassword else ContentType.Password,
                    error = state.passwordError,
                    imeAction = if (creating) ImeAction.Next else ImeAction.Done,
                    onDone = submit,
                )
                if (creating) {
                    PasswordField(
                        label = R.string.auth_confirm_label,
                        value = confirmation,
                        onValueChange = { confirmation = it; onConfirmationChange() },
                        contentType = ContentType.NewPassword,
                        error = state.confirmError,
                        imeAction = ImeAction.Done,
                        onDone = submit,
                        placeholder = R.string.auth_confirm_placeholder,
                    )
                } else {
                    TextLink(
                        R.string.auth_forgot_password,
                        onClick = { onForgotPassword(email) },
                        enabled = enabled,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = dimensionResource(R.dimen.sf_space_xs)),
                    )
                }
                PrimaryButton(
                    stringResource(if (creating) R.string.auth_mode_create_account else R.string.auth_mode_log_in),
                    onClick = submit,
                    enabled = enabled,
                    modifier = Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_xxl))
                        .fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f).heightIn(min = dimensionResource(R.dimen.sf_space_xxl)))
                if (!creating) {
                    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.auth_new_here), color = colors.textMuted, style = MaterialTheme.typography.bodyLarge)
                        TextLink(R.string.auth_mode_create_account, onClick = { onModeChange(AuthMode.CREATE_ACCOUNT) }, enabled = enabled)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggle(mode: AuthMode, enabled: Boolean, onModeChange: (AuthMode) -> Unit) {
    val colors = SpendFlowTheme.colors
    SingleChoiceSegmentedButtonRow(
        Modifier
            .padding(top = dimensionResource(R.dimen.sf_space_s))
            .fillMaxWidth(),
    ) {
        AuthMode.entries.forEachIndexed { i, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(i, AuthMode.entries.size),
                modifier = Modifier.heightIn(min = dimensionResource(R.dimen.sf_button_height)),
                enabled = enabled,
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.tile,
                    activeContentColor = colors.textHeading,
                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.textHeading,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    stringResource(if (option == AuthMode.LOG_IN) R.string.auth_mode_log_in else R.string.auth_mode_create_account),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    @StringRes label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    contentType: ContentType,
    @StringRes error: Int?,
    imeAction: ImeAction,
    onDone: () -> Unit,
    @StringRes placeholder: Int? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LabeledTextField(
        label = stringResource(label),
        value = value,
        onValueChange = onValueChange,
        fieldModifier = Modifier.semantics { this.contentType = contentType },
        placeholder = placeholder?.let { stringResource(it) },
        error = error?.let { stringResource(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password),
                    tint = SpendFlowTheme.colors.textBody,
                )
            }
        },
    )
}

/** Inline text link with a 48dp touch target (Widget.SpendFlow.TextLink). */
@Composable
private fun TextLink(@StringRes text: Int, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
    }
}

@ThemePreviews
@Preview(name = "Large text", showBackground = true, fontScale = 2f)
@Composable
private fun LoginScreenPreview() = SpendFlowTheme {
    LoginScreen(LoginUiState(), {}, {}, {}, {}, { _, _, _ -> }, {})
}

@ThemePreviews
@Composable
private fun CreateAccountScreenPreview() = SpendFlowTheme {
    LoginScreen(
        LoginUiState(mode = AuthMode.CREATE_ACCOUNT, passwordError = R.string.auth_error_password_short, submitting = true),
        {}, {}, {}, {}, { _, _, _ -> }, {},
    )
}
