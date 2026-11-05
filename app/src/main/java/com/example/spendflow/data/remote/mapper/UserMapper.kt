package com.example.spendflow.data.remote.mapper

import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.User
import com.example.spendflow.domain.model.UserPreferences

/** Field names on users/{uid}; the preference ones are also written one at a time by the preferences repository. */
internal object UserFields {
    const val DISPLAY_NAME = "displayName"
    const val EMAIL = "email"
    const val BASE_CURRENCY = "baseCurrency"
    const val STREAK_ENABLED = "streakEnabled"
    const val LOCK_SCREEN_QUICK_ENTRY = "lockScreenQuickEntry"
    const val CAP_THRESHOLDS = "capThresholds"
}

fun User.toFirestore(): Map<String, Any?> = mapOf(
    UserFields.DISPLAY_NAME to displayName,
    UserFields.EMAIL to email,
    UserFields.BASE_CURRENCY to baseCurrency,
    UserFields.STREAK_ENABLED to preferences.streakEnabled,
    UserFields.LOCK_SCREEN_QUICK_ENTRY to preferences.lockScreenQuickEntry,
    UserFields.CAP_THRESHOLDS to preferences.capThresholds.asList(),
)

// Profiles created before Settings existed have no preference fields and read as the defaults
fun userFromFirestore(id: String, data: Map<String, Any?>): User = FirestoreData(id, data).build {
    val defaults = UserPreferences()
    User(
        id = id,
        displayName = optString(UserFields.DISPLAY_NAME),
        email = optString(UserFields.EMAIL),
        baseCurrency = string(UserFields.BASE_CURRENCY),
        preferences = UserPreferences(
            streakEnabled = boolean(UserFields.STREAK_ENABLED, defaults.streakEnabled),
            lockScreenQuickEntry = boolean(UserFields.LOCK_SCREEN_QUICK_ENTRY, defaults.lockScreenQuickEntry),
            capThresholds = optIntList(UserFields.CAP_THRESHOLDS)?.let(CapThresholds::fromList) ?: defaults.capThresholds,
        ),
    )
}
