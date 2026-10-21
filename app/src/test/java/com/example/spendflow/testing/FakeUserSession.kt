package com.example.spendflow.testing

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeUserSession(initialUid: String? = "test-user") : UserSession {
    private val _uid = MutableStateFlow(initialUid)
    override val uid: StateFlow<String?> = _uid

    /** When set, [ensureSignedIn] fails with this error instead of signing in. */
    var signInError: AppError? = null

    override fun currentUid(): String? = _uid.value

    override suspend fun ensureSignedIn(): AppResult<String> {
        signInError?.let { return AppResult.Error(it) }
        val uid = _uid.value ?: "test-user".also { _uid.value = it }
        return AppResult.Success(uid)
    }

    fun signOut() {
        _uid.value = null
    }
}
