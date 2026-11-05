package com.example.spendflow.ui.onboarding

import androidx.lifecycle.ViewModel
import com.example.spendflow.data.repository.AuthRepository
import com.example.spendflow.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 01c. The profile was already created in the default base currency at registration, and the rules
 * keep it fixed, so this screen only confirms it. Continue and Skip both lead Home via MainActivity.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val baseCurrency: String = User.DEFAULT_BASE_CURRENCY

    fun finish() = auth.finishOnboarding()
}
