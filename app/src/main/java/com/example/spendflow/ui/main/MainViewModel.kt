package com.example.spendflow.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.data.repository.AuthRepository
import com.example.spendflow.data.session.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Decides which graph the user belongs in (auth, onboarding or main) and follows sign-in and sign-out. */
@HiltViewModel
class MainViewModel @Inject constructor(session: UserSession, auth: AuthRepository) : ViewModel() {

    // Seeded synchronously: Firebase restores the signed-in user from disk, so the first screen is already right
    val graph: StateFlow<AppGraph> = combine(session.uid, auth.onboardingPending, ::toGraph)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toGraph(session.currentUid(), auth.onboardingPending.value))

    private fun toGraph(uid: String?, onboardingPending: Boolean) = when {
        uid == null -> AppGraph.AUTH
        onboardingPending -> AppGraph.ONBOARDING
        else -> AppGraph.MAIN
    }
}
