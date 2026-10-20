package com.example.spendflow.data.session

import com.example.spendflow.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** Who is signed in. Repositories scope every read and write to this UID. */
interface UserSession {
    /** Emits the current UID immediately, then on every sign-in or sign-out. */
    val uid: Flow<String?>

    fun currentUid(): String?

    /**
     * Signs in anonymously if nobody is signed in. Stand-in until a real login flow exists;
     * an anonymous account can later be linked to email or Google without losing data.
     */
    suspend fun ensureSignedIn(): AppResult<String>
}
