package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.toFirestore
import com.example.spendflow.data.remote.mapper.userFromFirestore
import com.example.spendflow.data.remote.mapper.withCreateTimestamps
import com.example.spendflow.data.remote.mapper.withUpdateTimestamp
import com.example.spendflow.data.remote.toAppError
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.User
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserRepository @Inject constructor(
    private val session: UserSession,
    private val refs: FirestoreRefs,
    private val dispatchers: DispatcherProvider,
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
    }.flowOn(dispatchers.default)

    override suspend fun createProfile(user: User): AppResult<Unit> = profile(user) {
        it.writeLocally { doc -> doc.set(user.toFirestore().withCreateTimestamps()) }
    }

    override suspend fun updateProfile(user: User): AppResult<Unit> = profile(user) {
        it.writeLocally { doc -> doc.set(user.toFirestore().withUpdateTimestamp(), SetOptions.merge()) }
    }

    private inline fun profile(user: User, block: (DocumentReference) -> AppResult<Unit>): AppResult<Unit> {
        val uid = session.currentUid() ?: return AppResult.Error(AppError.Unauthenticated)
        if (user.id != uid) return AppResult.Error(AppError.PermissionDenied)
        return block(refs.user(uid))
    }
}
