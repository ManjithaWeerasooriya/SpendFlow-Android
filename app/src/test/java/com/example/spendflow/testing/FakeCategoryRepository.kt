package com.example.spendflow.testing

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** In-memory [CategoryRepository]. Set [observeError] or [writeError] to simulate failures. */
class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val items = MutableStateFlow(initial.associateBy { it.id })
    private val observeErrorFlow = MutableStateFlow<AppError?>(null)
    private var nextId = 1

    var observeError: AppError?
        get() = observeErrorFlow.value
        set(value) { observeErrorFlow.value = value }

    var writeError: AppError? = null

    val categories: List<Category> get() = items.value.values.toList()

    // Mirrors the Firestore implementation's filtering and sorting
    override fun observeCategories(type: CategoryType?, includeArchived: Boolean): Flow<AppResult<List<Category>>> =
        combine(items, observeErrorFlow) { map, error ->
            error?.let { return@combine AppResult.Error(it) }
            AppResult.Success(
                map.values
                    .filter { (type == null || it.type == type) && (includeArchived || !it.isArchived) }
                    .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name.lowercase() }),
            )
        }

    override fun newId(): String = "category-${nextId++}"

    override suspend fun get(id: String): AppResult<Category> =
        items.value[id]?.let { AppResult.Success(it) } ?: AppResult.Error(AppError.NotFound)

    override suspend fun create(item: Category): AppResult<Unit> = write { items.value += item.id to item }

    override suspend fun update(item: Category): AppResult<Unit> = write { items.value += item.id to item }

    override suspend fun delete(id: String): AppResult<Unit> = write { items.value -= id }

    private inline fun write(block: () -> Unit): AppResult<Unit> {
        writeError?.let { return AppResult.Error(it) }
        block()
        return AppResult.Success(Unit)
    }
}
