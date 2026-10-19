package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.map
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.accountFromFirestore
import com.example.spendflow.data.remote.mapper.toFirestore as mapToFirestore
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.Account
import com.google.firebase.firestore.CollectionReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreAccountRepository @Inject constructor(
    session: UserSession,
    private val refs: FirestoreRefs,
    dispatchers: DispatcherProvider,
) : FirestoreUserOwnedRepository<Account>(session, refs, dispatchers), AccountRepository {

    override fun collection(uid: String): CollectionReference = refs.accounts(uid)
    override fun Account.id(): String = id
    override fun Account.toFirestore(): Map<String, Any?> = mapToFirestore()
    override fun fromFirestore(id: String, data: Map<String, Any?>): Account = accountFromFirestore(id, data)

    // Filtered and sorted here: accounts are few, and an isArchived + name query needs a composite index
    override fun observeAccounts(includeArchived: Boolean): Flow<AppResult<List<Account>>> =
        observe().map { result ->
            result.map { accounts ->
                accounts.filter { includeArchived || !it.isArchived }.sortedBy { it.name.lowercase() }
            }
        }
}
