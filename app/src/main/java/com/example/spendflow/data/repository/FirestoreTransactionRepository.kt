package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.map
import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.toFirestore as mapToFirestore
import com.example.spendflow.data.remote.mapper.toTimestamp
import com.example.spendflow.data.remote.mapper.transactionFromFirestore
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.Transaction
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTransactionRepository @Inject constructor(
    session: UserSession,
    private val refs: FirestoreRefs,
    dispatchers: DispatcherProvider,
) : FirestoreUserOwnedRepository<Transaction>(session, refs, dispatchers), TransactionRepository {

    override fun collection(uid: String): CollectionReference = refs.transactions(uid)
    override fun Transaction.id(): String = id
    override fun Transaction.toFirestore(): Map<String, Any?> = mapToFirestore()
    override fun fromFirestore(id: String, data: Map<String, Any?>): Transaction = transactionFromFirestore(id, data)

    override fun observeTransactions(from: Instant, until: Instant): Flow<AppResult<List<Transaction>>> =
        observe { collection ->
            collection
                .whereGreaterThanOrEqualTo(FirestoreFields.OCCURRED_AT, from.toTimestamp())
                .whereLessThan(FirestoreFields.OCCURRED_AT, until.toTimestamp())
                .orderBy(FirestoreFields.OCCURRED_AT, Query.Direction.DESCENDING)
        }
}
