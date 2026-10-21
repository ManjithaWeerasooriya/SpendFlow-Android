package com.example.spendflow.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    // MainActivity shows Login as soon as the session ends; the repository finishes clearing the cache
    fun logOut() {
        viewModelScope.launch { auth.logOut() }
    }
}
