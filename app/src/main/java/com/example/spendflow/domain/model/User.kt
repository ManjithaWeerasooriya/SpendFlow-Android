package com.example.spendflow.domain.model

/**
 * Profile for the signed-in user. [id] is the Firebase Auth UID.
 * [baseCurrency] is what reports and budgets are in. Changing it doesn't convert anything: each
 * transaction keeps the baseAmount and baseCurrency it was stored with.
 */
data class User(
    val id: String,
    val displayName: String?,
    val email: String?,
    val baseCurrency: String,
    val preferences: UserPreferences = UserPreferences(),
) {
    init {
        requireCurrencyCode(baseCurrency)
    }

    companion object {
        /** Set on registration; it can be changed later in Settings. */
        const val DEFAULT_BASE_CURRENCY = "LKR"
    }
}
