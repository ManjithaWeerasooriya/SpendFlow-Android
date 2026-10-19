package com.example.spendflow.core.ui

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val error: AppError) : UiState<Nothing>
}

fun <T> AppResult<T>.toUiState(): UiState<T> = when (this) {
    is AppResult.Success -> UiState.Success(data)
    is AppResult.Error -> UiState.Error(error)
}
