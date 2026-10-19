package com.example.spendflow.core.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects while [owner] is at least [minState] and stops when it drops below.
 * In a Fragment pass `viewLifecycleOwner`, not the Fragment, so collection ends with the view.
 */
fun <T> Flow<T>.collectWithLifecycle(
    owner: LifecycleOwner,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit,
): Job = owner.lifecycleScope.launch {
    owner.repeatOnLifecycle(minState) {
        collect { action(it) }
    }
}
