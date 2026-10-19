package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.map
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.budgetFromFirestore
import com.example.spendflow.data.remote.mapper.toFirestore as mapToFirestore
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.Budget
import com.google.firebase.firestore.CollectionReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreBudgetRepository @Inject constructor(
    session: UserSession,
    private val refs: FirestoreRefs,
    dispatchers: DispatcherProvider,
    writeFailures: WriteFailureReporter,
) : FirestoreUserOwnedRepository<Budget>(session, refs, dispatchers, writeFailures), BudgetRepository {

    override fun collection(uid: String): CollectionReference = refs.budgets(uid)
    override fun Budget.id(): String = id
    override fun Budget.toFirestore(): Map<String, Any?> = mapToFirestore()
    override fun fromFirestore(id: String, data: Map<String, Any?>): Budget = budgetFromFirestore(id, data)

    override fun observeBudgets(): Flow<AppResult<List<Budget>>> =
        observe().map { result -> result.map { budgets -> budgets.sortedBy { it.name.lowercase() } } }
}
