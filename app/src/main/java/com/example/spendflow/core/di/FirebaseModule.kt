package com.example.spendflow.core.di

import com.example.spendflow.core.firebase.FirebaseEnvironment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val FIRESTORE_CACHE_SIZE_BYTES = 100L * 1024 * 1024

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // Settings and emulator wiring only take effect before the first Firestore call,
    // so nothing else should use FirebaseFirestore.getInstance() directly.
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore.apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(FIRESTORE_CACHE_SIZE_BYTES) })
        }
        FirebaseEnvironment.configure(this)
    }

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = Firebase.auth.apply {
        FirebaseEnvironment.configure(this)
    }
}
