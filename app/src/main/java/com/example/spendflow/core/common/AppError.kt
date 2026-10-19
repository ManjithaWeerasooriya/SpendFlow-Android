package com.example.spendflow.core.common

sealed interface AppError {
    data object Network : AppError
    data object Unauthenticated : AppError
    data object PermissionDenied : AppError
    data object NotFound : AppError
    data class Unknown(val cause: Throwable? = null) : AppError
}
