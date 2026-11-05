package com.example.spendflow.core.ui

import androidx.annotation.StringRes
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError

@get:StringRes
val AppError.messageRes: Int
    get() = when (this) {
        AppError.Network -> R.string.error_network
        AppError.Unauthenticated -> R.string.error_unauthenticated
        AppError.PermissionDenied -> R.string.error_permission_denied
        AppError.NotFound -> R.string.error_not_found
        AppError.InvalidCredentials -> R.string.auth_error_invalid_credentials
        AppError.InvalidEmail -> R.string.auth_error_email_invalid
        AppError.EmailInUse -> R.string.auth_error_email_in_use
        AppError.WeakPassword -> R.string.auth_error_password_short
        AppError.TooManyRequests -> R.string.auth_error_too_many_requests
        is AppError.Unknown -> R.string.error_unknown
    }
