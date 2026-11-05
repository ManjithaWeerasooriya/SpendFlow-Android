package com.example.spendflow.data.repository

import android.util.Log
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.DispatcherProvider
import com.example.spendflow.core.common.appResultOf
import com.example.spendflow.core.common.map
import com.example.spendflow.core.common.onError
import com.example.spendflow.data.remote.FirestoreProvider
import com.example.spendflow.data.remote.toAppError
import com.example.spendflow.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SpendFlowAuth"

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val users: UserRepository,
    private val firestore: FirestoreProvider,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    private val _onboardingPending = MutableStateFlow(false)
    override val onboardingPending: StateFlow<Boolean> = _onboardingPending.asStateFlow()

    // Signing in makes MainActivity leave the auth screen, which cancels the caller's scope while the
    // sign-in call is still returning. Both flows run NonCancellable so the profile step after it
    // always happens.
    override suspend fun register(email: String, password: String): AppResult<Unit> =
        withContext(NonCancellable + dispatchers.io) {
            // Set before the account exists, so the sign-in it triggers routes to onboarding, never Home
            _onboardingPending.value = true
            val created = appResultOf(errorMapper = { it.toAppError() }) {
                checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user?.uid) {
                    "Registration returned no user"
                }
            }
            when (created) {
                is AppResult.Error -> _onboardingPending.value = false
                // The account exists either way; logIn creates the profile if this write never lands
                is AppResult.Success -> users.createProfile(defaultProfile(created.data, email))
                    .onError { Log.w(TAG, "Profile not created on registration: $it") }
            }
            created.map { }
        }

    override suspend fun logIn(email: String, password: String): AppResult<Unit> =
        withContext(NonCancellable + dispatchers.io) {
            val signedIn = appResultOf(errorMapper = { it.toAppError() }) {
                checkNotNull(auth.signInWithEmailAndPassword(email, password).await().user?.uid) {
                    "Sign-in returned no user"
                }
            }
            if (signedIn is AppResult.Success) {
                users.createProfileIfMissing(defaultProfile(signedIn.data, email))
                    .onError { Log.w(TAG, "Couldn't check for a missing profile: $it") }
            }
            signedIn.map { }
        }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = withContext(dispatchers.io) {
        appResultOf(errorMapper = { it.toAppError() }) {
            try {
                auth.sendPasswordResetEmail(email).await()
            } catch (_: FirebaseAuthInvalidUserException) {
                // Only raised with email enumeration protection off; same answer as for a real account
            }
            Unit
        }
    }

    override fun finishOnboarding() {
        _onboardingPending.value = false
    }

    override suspend fun logOut(): AppResult<Unit> {
        _onboardingPending.value = false
        // Signing out first stops every listener (they follow the UID) and moves the UI to Login,
        // which cancels the caller's scope; the cache must still be cleared after that.
        auth.signOut()
        return withContext(NonCancellable + dispatchers.io) {
            appResultOf(errorMapper = { it.toAppError() }) { firestore.clearLocalData() }
                .onError { Log.w(TAG, "Couldn't clear the local cache on log-out: $it") }
        }
    }

    private fun defaultProfile(uid: String, email: String) =
        User(id = uid, displayName = null, email = email, baseCurrency = User.DEFAULT_BASE_CURRENCY)
}
