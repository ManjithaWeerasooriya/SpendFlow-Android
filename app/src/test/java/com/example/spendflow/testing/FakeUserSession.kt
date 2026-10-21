package com.example.spendflow.testing

import com.example.spendflow.data.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeUserSession(initialUid: String? = "test-user") : UserSession {
    private val _uid = MutableStateFlow(initialUid)
    override val uid: StateFlow<String?> = _uid

    override fun currentUid(): String? = _uid.value

    fun signIn(uid: String = "test-user") {
        _uid.value = uid
    }

    fun signOut() {
        _uid.value = null
    }
}
