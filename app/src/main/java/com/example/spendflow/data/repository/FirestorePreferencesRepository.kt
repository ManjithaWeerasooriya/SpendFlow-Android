package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.map
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.UserFields
import com.example.spendflow.data.remote.mapper.withUpdateTimestamp
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.UserPreferences
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestorePreferencesRepository @Inject constructor(
    private val session: UserSession,
    private val refs: FirestoreRefs,
    private val users: UserRepository,
    private val dispatchers: DispatcherProvider,
    private val writeFailures: WriteFailureReporter,
) : PreferencesRepository {

    override fun observePreferences(): Flow<AppResult<UserPreferences>> =
        users.observeProfile().map { result -> result.map { it?.preferences ?: UserPreferences() } }

    override suspend fun setStreakEnabled(enabled: Boolean) = write(UserFields.STREAK_ENABLED, enabled)

    override suspend fun setLockScreenQuickEntry(enabled: Boolean) = write(UserFields.LOCK_SCREEN_QUICK_ENTRY, enabled)

    override suspend fun setCapThresholds(thresholds: CapThresholds) = write(UserFields.CAP_THRESHOLDS, thresholds.asList())

    override suspend fun setBaseCurrency(currencyCode: String) = write(UserFields.BASE_CURRENCY, currencyCode)

    private suspend fun write(field: String, value: Any): AppResult<Unit> = withContext(dispatchers.io) {
        val uid = session.currentUid() ?: return@withContext AppResult.Error(AppError.Unauthenticated)
        refs.user(uid).writeLocally(writeFailures) {
            it.set(mapOf(field to value).withUpdateTimestamp(), SetOptions.merge())
        }
    }
}
