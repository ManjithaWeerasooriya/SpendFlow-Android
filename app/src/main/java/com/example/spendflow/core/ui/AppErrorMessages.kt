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
        is AppError.Unknown -> R.string.error_unknown
    }
