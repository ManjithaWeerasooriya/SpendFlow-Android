package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Operations shared by every entity stored under users/{uid}. All require a signed-in user. */
interface UserOwnedRepository<T> {
    /** ID to give a new model before passing it to [create]. */
    fun newId(): String

    suspend fun get(id: String): AppResult<T>

    suspend fun create(item: T): AppResult<Unit>

    suspend fun update(item: T): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>

    /** IDs of documents with local writes the server has not acknowledged yet. */
    fun observePendingIds(): Flow<Set<String>> = flowOf(emptySet())
}
