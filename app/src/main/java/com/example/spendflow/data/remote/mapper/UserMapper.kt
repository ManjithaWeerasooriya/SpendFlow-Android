package com.example.spendflow.data.remote.mapper

import com.example.spendflow.domain.model.User

private const val DISPLAY_NAME = "displayName"
private const val EMAIL = "email"
private const val BASE_CURRENCY_FIELD = "baseCurrency"

fun User.toFirestore(): Map<String, Any?> = mapOf(
    DISPLAY_NAME to displayName,
    EMAIL to email,
    BASE_CURRENCY_FIELD to baseCurrency,
)

fun userFromFirestore(id: String, data: Map<String, Any?>): User = FirestoreData(id, data).build {
    User(
        id = id,
        displayName = optString(DISPLAY_NAME),
        email = optString(EMAIL),
        baseCurrency = string(BASE_CURRENCY_FIELD),
    )
}
