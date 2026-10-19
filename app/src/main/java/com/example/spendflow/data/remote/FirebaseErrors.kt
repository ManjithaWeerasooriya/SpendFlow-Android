package com.example.spendflow.data.remote

import com.example.spendflow.core.common.AppError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

fun Throwable.toAppError(): AppError = when (this) {
    is FirebaseNetworkException -> AppError.Network
    is FirebaseFirestoreException -> when (code) {
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> AppError.Network
        Code.UNAUTHENTICATED -> AppError.Unauthenticated
        Code.PERMISSION_DENIED -> AppError.PermissionDenied
        Code.NOT_FOUND -> AppError.NotFound
        else -> AppError.Unknown(this)
    }
    else -> AppError.Unknown(this)
}
