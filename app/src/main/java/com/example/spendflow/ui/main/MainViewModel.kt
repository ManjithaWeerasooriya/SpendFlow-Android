package com.example.spendflow.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.data.session.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Decides whether the user belongs in the auth or the main graph, and follows sign-in and sign-out. */
@HiltViewModel
class MainViewModel @Inject constructor(session: UserSession) : ViewModel() {

    // Seeded synchronously: Firebase restores the signed-in user from disk, so the first screen is already right
    val graph: StateFlow<AppGraph> = session.uid
        .map { it.toGraph() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.currentUid().toGraph())

    private fun String?.toGraph() = if (this == null) AppGraph.AUTH else AppGraph.MAIN
}
