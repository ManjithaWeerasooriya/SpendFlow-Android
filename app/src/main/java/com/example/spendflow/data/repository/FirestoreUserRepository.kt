package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.appResultOf
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.toFirestore
import com.example.spendflow.data.remote.mapper.userFromFirestore
import com.example.spendflow.data.remote.mapper.withCreateTimestamps
import com.example.spendflow.data.remote.mapper.withUpdateTimestamp
import com.example.spendflow.data.remote.toAppError
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.User
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserRepository @Inject constructor(
    private val session: UserSession,
    private val refs: FirestoreRefs,
    private val dispatchers: DispatcherProvider,
    private val writeFailures: WriteFailureReporter,
) : UserRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProfile(): Flow<AppResult<User?>> = session.uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(AppResult.Error(AppError.Unauthenticated))
        } else {
            refs.user(uid).snapshots()
                .map<_, AppResult<User?>> { AppResult.Success(it.toModelOrNull(::userFromFirestore)) }
                .catch { emit(AppResult.Error(it.toAppError())) }
        }
    }.flowOn(dispatchers.io)

    override suspend fun createProfile(user: User): AppResult<Unit> =
        writeProfile(user) { it.set(user.toFirestore().withCreateTimestamps()) }

    override suspend fun createProfileIfMissing(user: User): AppResult<Unit> {
        val exists = withContext(dispatchers.io) {
            val uid = session.currentUid() ?: return@withContext AppResult.Error(AppError.Unauthenticated)
            // From the server: a cache miss doesn't mean the profile is missing
            appResultOf(errorMapper = { it.toAppError() }) { refs.user(uid).get(Source.SERVER).await().exists() }
        }
        return when (exists) {
            is AppResult.Error -> exists
            is AppResult.Success -> if (exists.data) AppResult.Success(Unit) else createProfile(user)
        }
    }

    override suspend fun updateProfile(user: User): AppResult<Unit> =
        writeProfile(user) { it.set(user.toFirestore().withUpdateTimestamp(), SetOptions.merge()) }

    private suspend fun writeProfile(user: User, op: (DocumentReference) -> Task<Void>): AppResult<Unit> =
        withContext(dispatchers.io) {
            val uid = session.currentUid() ?: return@withContext AppResult.Error(AppError.Unauthenticated)
            if (user.id != uid) return@withContext AppResult.Error(AppError.PermissionDenied)
            refs.user(uid).writeLocally(writeFailures, op)
        }
}
