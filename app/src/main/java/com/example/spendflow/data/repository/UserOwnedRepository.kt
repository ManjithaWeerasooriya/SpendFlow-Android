package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult

/** Operations shared by every entity stored under users/{uid}. All require a signed-in user. */
interface UserOwnedRepository<T> {
    /** ID to give a new model before passing it to [create]. */
    fun newId(): String

    suspend fun get(id: String): AppResult<T>

    suspend fun create(item: T): AppResult<Unit>

    suspend fun update(item: T): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>
}
