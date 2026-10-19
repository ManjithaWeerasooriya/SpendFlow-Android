package com.example.spendflow.data.remote.mapper

import com.example.spendflow.domain.model.User

private const val DISPLAY_NAME = "displayName"
private const val EMAIL = "email"
private const val DEFAULT_CURRENCY = "defaultCurrency"

fun User.toFirestore(): Map<String, Any?> = mapOf(
    DISPLAY_NAME to displayName,
    EMAIL to email,
    DEFAULT_CURRENCY to defaultCurrency,
)

fun userFromFirestore(id: String, data: Map<String, Any?>): User = FirestoreData(id, data).build {
    User(
        id = id,
        displayName = optString(DISPLAY_NAME),
        email = optString(EMAIL),
        defaultCurrency = string(DEFAULT_CURRENCY),
    )
}
