package com.example.spendflow.ui.auth

import androidx.annotation.StringRes
import com.example.spendflow.R

/** Field checks for the login form. Each returns the message to show under the field, or null if valid. */
object CredentialsValidator {
    /** Firebase Auth's own minimum. */
    const val MIN_PASSWORD_LENGTH = 6

    // Same pattern and length as isValidEmail in firestore.rules; anything looser would let the
    // profile write be rejected after the account was already created.
    private val emailPattern = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+[.][a-zA-Z]{2,}$")
    private const val MAX_EMAIL_LENGTH = 254

    @StringRes
    fun email(email: String): Int? = when {
        email.isEmpty() -> R.string.auth_error_email_required
        email.length > MAX_EMAIL_LENGTH || !emailPattern.matches(email) -> R.string.auth_error_email_invalid
        else -> null
    }

    /** Log in only checks presence: the length rule could tell an attacker about the password policy. */
    @StringRes
    fun loginPassword(password: String): Int? =
        if (password.isEmpty()) R.string.auth_error_password_required else null

    @StringRes
    fun newPassword(password: String): Int? = when {
        password.isEmpty() -> R.string.auth_error_password_required
        password.length < MIN_PASSWORD_LENGTH -> R.string.auth_error_password_short
        else -> null
    }

    @StringRes
    fun confirmation(password: String, confirmation: String): Int? = when {
        confirmation.isEmpty() -> R.string.auth_error_confirm_required
        confirmation != password -> R.string.auth_error_confirm_mismatch
        else -> null
    }
}
