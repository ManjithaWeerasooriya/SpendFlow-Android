package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.map
import com.example.spendflow.data.remote.FirestoreRefs
import com.example.spendflow.data.remote.mapper.categoryFromFirestore
import com.example.spendflow.data.remote.mapper.toFirestore as mapToFirestore
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.google.firebase.firestore.CollectionReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreCategoryRepository @Inject constructor(
    session: UserSession,
    private val refs: FirestoreRefs,
    dispatchers: DispatcherProvider,
) : FirestoreUserOwnedRepository<Category>(session, refs, dispatchers), CategoryRepository {

    override fun collection(uid: String): CollectionReference = refs.categories(uid)
    override fun Category.id(): String = id
    override fun Category.toFirestore(): Map<String, Any?> = mapToFirestore()
    override fun fromFirestore(id: String, data: Map<String, Any?>): Category = categoryFromFirestore(id, data)

    // Filtered and sorted here for the same reason as accounts: small set, no composite index needed
    override fun observeCategories(type: CategoryType?, includeArchived: Boolean): Flow<AppResult<List<Category>>> =
        observe().map { result ->
            result.map { categories ->
                categories
                    .filter { (type == null || it.type == type) && (includeArchived || !it.isArchived) }
                    .sortedBy { it.name.lowercase() }
            }
        }
}
