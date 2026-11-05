package com.example.spendflow.data.remote

import com.example.spendflow.core.firebase.FirebaseEnvironment
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val FIRESTORE_CACHE_SIZE_BYTES = 100L * 1024 * 1024

/**
 * Holds the current Firestore instance. A terminated instance can't be used again, so clearing the
 * cache on log-out swaps in a new one; callers must read [firestore] each time instead of keeping it.
 */
@Singleton
class FirestoreProvider @Inject constructor() {

    @Volatile
    var firestore: FirebaseFirestore = create()
        private set

    /** Drops every cached document and unsynced write, then starts a fresh instance. */
    suspend fun clearLocalData() {
        val old = firestore
        old.terminate().await()
        old.clearPersistence().await()
        firestore = create()
    }

    // Settings and emulator wiring only take effect before the instance's first call,
    // so nothing else should use FirebaseFirestore.getInstance() directly.
    private fun create(): FirebaseFirestore = Firebase.firestore.apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(FIRESTORE_CACHE_SIZE_BYTES) })
        }
        FirebaseEnvironment.configure(this)
    }
}
