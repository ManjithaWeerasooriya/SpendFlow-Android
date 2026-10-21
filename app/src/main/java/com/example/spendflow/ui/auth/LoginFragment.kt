package com.example.spendflow.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.spendflow.R
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.FragmentLoginBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLoginBinding.bind(view)
        binding.continueButton.setOnClickListener { viewModel.continueAsGuest() }

        viewModel.signingIn.collectWithLifecycle(viewLifecycleOwner) { binding.continueButton.isEnabled = !it }
        viewModel.events.collectWithLifecycle(viewLifecycleOwner) { event ->
            when (event) {
                is LoginEvent.ShowError ->
                    SpendFlowSnackbar.make(view, getString(event.error.messageRes), SpendFlowSnackbar.Tone.ERROR, duration = Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
