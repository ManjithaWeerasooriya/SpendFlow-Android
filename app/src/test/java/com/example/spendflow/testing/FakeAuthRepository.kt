package com.example.spendflow.testing

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [AuthRepository] that signs [session] in and out like Firebase would.
 * Set [error] to make the next calls fail; set [gate] to hold calls until it completes.
 */
class FakeAuthRepository(
    private val session: FakeUserSession = FakeUserSession(initialUid = null),
) : AuthRepository {
    private val _onboardingPending = MutableStateFlow(false)
    override val onboardingPending: StateFlow<Boolean> = _onboardingPending

    var error: AppError? = null
    var gate: CompletableDeferred<Unit>? = null

    val registered = mutableListOf<Pair<String, String>>()
    val loggedIn = mutableListOf<Pair<String, String>>()
    val resetRequests = mutableListOf<String>()
    var logOutCount = 0
        private set

    override suspend fun register(email: String, password: String): AppResult<Unit> = call {
        registered += email to password
        _onboardingPending.value = true
        session.signIn()
    }

    override suspend fun logIn(email: String, password: String): AppResult<Unit> = call {
        loggedIn += email to password
        session.signIn()
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = call { resetRequests += email }

    override fun finishOnboarding() {
        _onboardingPending.value = false
    }

    override suspend fun logOut(): AppResult<Unit> {
        logOutCount++
        _onboardingPending.value = false
        session.signOut()
        return AppResult.Success(Unit)
    }

    private suspend fun call(block: () -> Unit): AppResult<Unit> {
        gate?.await()
        error?.let { return AppResult.Error(it) }
        block()
        return AppResult.Success(Unit)
    }
}
