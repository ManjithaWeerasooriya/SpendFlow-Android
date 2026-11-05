package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Settings stored on the user document, users/{uid}. Each setter writes only its own field, so two
 * devices changing different settings don't overwrite each other.
 */
interface PreferencesRepository {
    /** The defaults until the profile exists. */
    fun observePreferences(): Flow<AppResult<UserPreferences>>

    suspend fun setStreakEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setLockScreenQuickEntry(enabled: Boolean): AppResult<Unit>

    suspend fun setCapThresholds(thresholds: CapThresholds): AppResult<Unit>

    /** Existing transactions keep their stored baseAmount and baseCurrency; nothing is converted. */
    suspend fun setBaseCurrency(currencyCode: String): AppResult<Unit>
}
