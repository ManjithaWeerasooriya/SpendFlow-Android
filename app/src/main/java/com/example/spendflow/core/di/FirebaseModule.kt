package com.example.spendflow.core.di

import com.example.spendflow.core.firebase.FirebaseEnvironment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Firestore comes from FirestoreProvider, since log-out replaces the instance. */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = Firebase.auth.apply {
        FirebaseEnvironment.configure(this)
    }
}
