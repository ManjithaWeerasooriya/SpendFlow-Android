package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.appResultOf
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.withCreateTimestamps
import com.example.spendflow.data.remote.mapper.withUpdateTimestamp
import com.example.spendflow.data.remote.toAppError
import com.example.spendflow.data.session.UserSession
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Firestore plumbing for one users/{uid}/<collection>. Subclasses add their entity-specific queries. */
abstract class FirestoreUserOwnedRepository<T>(
    private val session: UserSession,
    private val refs: FirestoreRefs,
    private val dispatchers: DispatcherProvider,
) : UserOwnedRepository<T> {

    protected abstract fun collection(uid: String): CollectionReference
    protected abstract fun T.id(): String
    protected abstract fun T.toFirestore(): Map<String, Any?>
    protected abstract fun fromFirestore(id: String, data: Map<String, Any?>): T

    /** Live results of [query], re-subscribing when the signed-in user changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun observe(query: (CollectionReference) -> Query = { it }): Flow<AppResult<List<T>>> =
        session.uid.flatMapLatest { uid ->
            if (uid == null) {
                flowOf(AppResult.Error(AppError.Unauthenticated))
            } else {
                query(collection(uid)).snapshots()
                    .map<_, AppResult<List<T>>> { snapshot ->
                        AppResult.Success(snapshot.documents.mapNotNull { it.toModelOrNull(::fromFirestore) })
                    }
                    .catch { emit(AppResult.Error(it.toAppError())) }
            }
        }.flowOn(dispatchers.default)

    override fun newId(): String = refs.newId()

    override suspend fun get(id: String): AppResult<T> {
        val uid = session.currentUid() ?: return AppResult.Error(AppError.Unauthenticated)
        val result = appResultOf(errorMapper = { it.toAppError() }) {
            val snapshot = collection(uid).document(id).get().await()
            snapshot.data?.let { fromFirestore(snapshot.id, it) }
        }
        return when (result) {
            is AppResult.Success -> result.data?.let { AppResult.Success(it) } ?: AppResult.Error(AppError.NotFound)
            is AppResult.Error -> result
        }
    }

    override suspend fun create(item: T): AppResult<Unit> =
        document(item.id()) { it.writeLocally { doc -> doc.set(item.toFirestore().withCreateTimestamps()) } }

    // merge keeps createdAt, which toFirestore() doesn't include
    override suspend fun update(item: T): AppResult<Unit> = document(item.id()) {
        it.writeLocally { doc -> doc.set(item.toFirestore().withUpdateTimestamp(), SetOptions.merge()) }
    }

    override suspend fun delete(id: String): AppResult<Unit> = document(id) { it.writeLocally { doc -> doc.delete() } }

    private inline fun document(id: String, block: (DocumentReference) -> AppResult<Unit>): AppResult<Unit> {
        val uid = session.currentUid() ?: return AppResult.Error(AppError.Unauthenticated)
        return block(collection(uid).document(id))
    }
}
