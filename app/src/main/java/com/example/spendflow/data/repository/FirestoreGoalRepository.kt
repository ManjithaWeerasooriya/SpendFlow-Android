package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.savingsGoalFromFirestore
import com.example.spendflow.data.remote.mapper.toFirestore as mapToFirestore
import com.example.spendflow.data.remote.toAppError
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.SavingsGoal
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreGoalRepository @Inject constructor(
    private val session: UserSession,
    private val refs: FirestoreRefs,
    dispatchers: DispatcherProvider,
    failures: WriteFailureReporter,
) : FirestoreUserOwnedRepository<SavingsGoal>(session, refs, dispatchers, failures), GoalRepository {
    override fun collection(uid: String) = refs.goals(uid)
    override fun SavingsGoal.id() = id
    override fun SavingsGoal.toFirestore() = mapToFirestore()
    override fun fromFirestore(id: String, data: Map<String, Any?>) = savingsGoalFromFirestore(id, data)
    override fun observeGoals() = observe()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeConfirmedGoals(): Flow<AppResult<List<SavingsGoal>>> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(AppResult.Error(AppError.Unauthenticated))
        else collection(uid).snapshots(MetadataChanges.INCLUDE)
            .map<_, AppResult<List<SavingsGoal>>> { snapshot ->
                AppResult.Success(snapshot.documents.filter { !it.metadata.hasPendingWrites() }
                    .mapNotNull { it.toModelOrNull(::fromFirestore) })
            }.catch { emit(AppResult.Error(it.toAppError())) }
    }
}
