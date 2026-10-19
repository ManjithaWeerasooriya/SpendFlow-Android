package com.example.spendflow.domain.model

/** Profile for the signed-in user. [id] is the Firebase Auth UID. */
data class User(
    val id: String,
    val displayName: String?,
    val email: String?,
    val defaultCurrency: String,
) {
    init {
        requireCurrencyCode(defaultCurrency)
    }
}
