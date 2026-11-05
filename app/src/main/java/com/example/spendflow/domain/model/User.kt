package com.example.spendflow.domain.model

/**
 * Profile for the signed-in user. [id] is the Firebase Auth UID.
 * [baseCurrency] is what reports and budgets are in; it can't change once set, because every
 * stored baseAmount was converted into it.
 */
data class User(
    val id: String,
    val displayName: String?,
    val email: String?,
    val baseCurrency: String,
) {
    init {
        requireCurrencyCode(baseCurrency)
    }

    companion object {
        /** Set on registration; there is no way to pick another one yet. */
        const val DEFAULT_BASE_CURRENCY = "LKR"
    }
}
