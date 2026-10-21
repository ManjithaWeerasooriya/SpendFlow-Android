package com.example.spendflow.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.spendflow.R
import com.example.spendflow.databinding.FragmentOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.Currency
import java.util.Locale

@AndroidEntryPoint
class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOnboardingBinding.bind(view)
        val code = viewModel.baseCurrency
        binding.currencyName.text = getString(
            R.string.onboarding_currency_value,
            code,
            Currency.getInstance(code).getDisplayName(Locale.getDefault()),
        )
        binding.skipButton.setOnClickListener { viewModel.finish() }
        binding.continueButton.setOnClickListener { viewModel.finish() }
    }
}
