package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.map
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.recurringTransactionFromFirestore
import com.example.spendflow.data.remote.mapper.toFirestore as mapToFirestore
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.RecurringTransaction
import com.google.firebase.firestore.CollectionReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRecurringTransactionRepository @Inject constructor(
    session: UserSession,
    private val refs: FirestoreRefs,
    dispatchers: DispatcherProvider,
    writeFailures: WriteFailureReporter,
) : FirestoreUserOwnedRepository<RecurringTransaction>(session, refs, dispatchers, writeFailures), RecurringTransactionRepository {

    override fun collection(uid: String): CollectionReference = refs.recurring(uid)
    override fun RecurringTransaction.id(): String = id
    override fun RecurringTransaction.toFirestore(): Map<String, Any?> = mapToFirestore()
    override fun fromFirestore(id: String, data: Map<String, Any?>): RecurringTransaction = recurringTransactionFromFirestore(id, data)

    override fun observeRecurring(activeOnly: Boolean): Flow<AppResult<List<RecurringTransaction>>> =
        observe().map { result ->
            result.map { items -> items.filter { !activeOnly || it.isActive }.sortedBy { it.nextOccurrence } }
        }
}
