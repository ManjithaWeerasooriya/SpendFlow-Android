package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Email/password accounts. Who is signed in is UserSession's to report; MainActivity follows it,
 * so a successful call here needs no navigation from the caller.
 */
interface AuthRepository {
    /**
     * True from a successful [register] until [finishOnboarding] or [logOut]. Kept in memory only,
     * so restarting the app goes straight to Home.
     */
    val onboardingPending: StateFlow<Boolean>

    /** Creates the account and its users/{uid} profile in the default base currency, and signs in. */
    suspend fun register(email: String, password: String): AppResult<Unit>

    suspend fun logIn(email: String, password: String): AppResult<Unit>

    /** Succeeds whether or not [email] has an account, so the result can't reveal which emails exist. */
    suspend fun sendPasswordReset(email: String): AppResult<Unit>

    fun finishOnboarding()

    /** Signs out and clears the local Firestore cache, including writes not yet synced. */
    suspend fun logOut(): AppResult<Unit>
}
