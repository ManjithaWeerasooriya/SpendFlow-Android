package com.example.spendflow.data.session

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.appResultOf
import com.example.spendflow.data.remote.toAppError
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserSession @Inject constructor(private val auth: FirebaseAuth) : UserSession {

    override val uid: Flow<String?> = callbackFlow {
        // Firebase calls the listener once on registration with the current state
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun ensureSignedIn(): AppResult<String> {
        currentUid()?.let { return AppResult.Success(it) }
        return appResultOf(errorMapper = { it.toAppError() }) {
            checkNotNull(auth.signInAnonymously().await().user?.uid) { "Anonymous sign-in returned no user" }
        }
    }
}
