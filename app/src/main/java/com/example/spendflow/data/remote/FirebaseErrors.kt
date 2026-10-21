package com.example.spendflow.data.remote

import com.example.spendflow.core.common.AppError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

fun Throwable.toAppError(): AppError = when (this) {
    is FirebaseNetworkException -> AppError.Network
    is FirebaseTooManyRequestsException -> AppError.TooManyRequests
    // Before InvalidCredentials, which it extends
    is FirebaseAuthWeakPasswordException -> AppError.WeakPassword
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") AppError.InvalidEmail else AppError.InvalidCredentials
    // Unknown or disabled account: reported like a wrong password
    is FirebaseAuthInvalidUserException -> AppError.InvalidCredentials
    is FirebaseAuthUserCollisionException -> AppError.EmailInUse
    is FirebaseFirestoreException -> when (code) {
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> AppError.Network
        Code.UNAUTHENTICATED -> AppError.Unauthenticated
        Code.PERMISSION_DENIED -> AppError.PermissionDenied
        Code.NOT_FOUND -> AppError.NotFound
        else -> AppError.Unknown(this)
    }
    else -> AppError.Unknown(this)
}
