package com.example.spendflow.testing

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.PreferencesRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.User
import com.example.spendflow.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory users/{uid} document behind both [UserRepository] and [PreferencesRepository], so a
 * preference write shows up in the observed profile the way Firestore's cache does.
 * Set [writeError] to make the next writes fail.
 */
class FakeProfileRepository(initial: User?) : UserRepository, PreferencesRepository {
    val profile = MutableStateFlow(initial)
    var writeError: AppError? = null
    var writeCount = 0
        private set

    override fun observeProfile(): Flow<AppResult<User?>> = profile.map { AppResult.Success(it) }

    override suspend fun createProfile(user: User) = write { user }

    override suspend fun createProfileIfMissing(user: User) = write { it ?: user }

    override suspend fun updateProfile(user: User) = write { user }

    override fun observePreferences(): Flow<AppResult<UserPreferences>> =
        profile.map { AppResult.Success(it?.preferences ?: UserPreferences()) }

    override suspend fun setStreakEnabled(enabled: Boolean) = updatePreferences { it.copy(streakEnabled = enabled) }

    override suspend fun setLockScreenQuickEntry(enabled: Boolean) =
        updatePreferences { it.copy(lockScreenQuickEntry = enabled) }

    override suspend fun setCapThresholds(thresholds: CapThresholds) =
        updatePreferences { it.copy(capThresholds = thresholds) }

    override suspend fun setBaseCurrency(currencyCode: String) = write { it?.copy(baseCurrency = currencyCode) }

    private fun updatePreferences(change: (UserPreferences) -> UserPreferences) =
        write { it?.copy(preferences = change(it.preferences)) }

    private fun write(change: (User?) -> User?): AppResult<Unit> {
        writeError?.let { return AppResult.Error(it) }
        writeCount++
        profile.value = change(profile.value)
        return AppResult.Success(Unit)
    }
}
