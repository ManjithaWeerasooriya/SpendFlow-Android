package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.User
import kotlinx.coroutines.flow.Flow

/** The signed-in user's profile document, users/{uid}. */
interface UserRepository {
    /** Null until the profile has been created. */
    fun observeProfile(): Flow<AppResult<User?>>

    suspend fun createProfile(user: User): AppResult<Unit>

    suspend fun updateProfile(user: User): AppResult<Unit>
}
