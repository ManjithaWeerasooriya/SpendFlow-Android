package com.example.spendflow.data.session

import kotlinx.coroutines.flow.Flow

/** Who is signed in. Repositories scope every read and write to this UID. Signing in is AuthRepository's job. */
interface UserSession {
    /** Emits the current UID immediately, then on every sign-in or sign-out. */
    val uid: Flow<String?>

    fun currentUid(): String?
}
