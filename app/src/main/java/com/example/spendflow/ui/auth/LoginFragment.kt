package com.example.spendflow.ui.auth

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.spendflow.R
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.FragmentLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLoginBinding.bind(view)

        fun submit() = viewModel.submit(
            binding.emailInput.text.toString(),
            binding.passwordInput.text.toString(),
            binding.confirmInput.text.toString(),
        )

        binding.modeToggle.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                viewModel.setMode(if (id == R.id.createAccountModeButton) AuthMode.CREATE_ACCOUNT else AuthMode.LOG_IN)
            }
        }
        binding.createAccountLink.setOnClickListener { viewModel.setMode(AuthMode.CREATE_ACCOUNT) }
        binding.forgotPassword.setOnClickListener { viewModel.sendPasswordReset(binding.emailInput.text.toString()) }
        binding.submitButton.setOnClickListener { submit() }
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged() }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged() }
        binding.confirmInput.doAfterTextChanged { viewModel.onConfirmationChanged() }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        binding.confirmInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }

        viewModel.uiState.collectWithLifecycle(viewLifecycleOwner) { state -> binding.render(state) }
        viewModel.events.collectWithLifecycle(viewLifecycleOwner) { event ->
            when (event) {
                is LoginEvent.ResetEmailSent -> SpendFlowSnackbar.make(
                    view,
                    getString(R.string.auth_reset_sent, event.email),
                    SpendFlowSnackbar.Tone.SUCCESS,
                    duration = Snackbar.LENGTH_LONG,
                ).show()
                is LoginEvent.ShowError ->
                    SpendFlowSnackbar.make(view, getString(event.error.messageRes), SpendFlowSnackbar.Tone.ERROR, duration = Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun FragmentLoginBinding.render(state: LoginUiState) {
        val creating = state.mode == AuthMode.CREATE_ACCOUNT
        // 01a has the logo and a "Create account" link; 01b has the segmented control instead
        modeToggle.isVisible = creating
        modeToggle.check(if (creating) R.id.createAccountModeButton else R.id.logInModeButton)
        logo.isVisible = !creating
        createAccountPrompt.isVisible = !creating
        forgotPassword.isVisible = !creating
        confirmGroup.isVisible = creating
        title.setText(if (creating) R.string.register_title else R.string.login_title)
        message.setText(if (creating) R.string.register_message else R.string.login_message)
        submitButton.setText(if (creating) R.string.auth_mode_create_account else R.string.auth_mode_log_in)
        passwordInput.imeOptions = if (creating) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            passwordInput.setAutofillHints(if (creating) "newPassword" else "password")
        }

        emailField.showError(state.emailError)
        passwordField.showError(state.passwordError)
        confirmField.showError(state.confirmError)

        progress.visibility = if (state.submitting) View.VISIBLE else View.INVISIBLE
        submitButton.isEnabled = !state.submitting
        forgotPassword.isEnabled = !state.submitting
        modeToggle.isEnabled = !state.submitting
        createAccountLink.isEnabled = !state.submitting
    }

    private fun TextInputLayout.showError(@StringRes message: Int?) {
        error = message?.let(::getString)
    }
}
