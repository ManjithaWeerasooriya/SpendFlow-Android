package com.example.spendflow.core.common

sealed interface AppError {
    data object Network : AppError
    data object Unauthenticated : AppError
    data object PermissionDenied : AppError
    data object NotFound : AppError

    // Sign-in and registration. Wrong password and unknown email are both InvalidCredentials,
    // so the login screen can't reveal whether an email has an account.
    data object InvalidCredentials : AppError
    data object InvalidEmail : AppError
    data object EmailInUse : AppError
    data object WeakPassword : AppError
    data object TooManyRequests : AppError

    data class Unknown(val cause: Throwable? = null) : AppError
}
