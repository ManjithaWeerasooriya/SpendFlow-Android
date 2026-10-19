package com.example.spendflow.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Debug builds talk to the local Firebase emulators. */
object FirebaseEnvironment {
    // 10.0.2.2 is the host machine as seen from the Android emulator
    private const val EMULATOR_HOST = "10.0.2.2"

    fun configure(firestore: FirebaseFirestore) = firestore.useEmulator(EMULATOR_HOST, 8080)

    fun configure(auth: FirebaseAuth) = auth.useEmulator(EMULATOR_HOST, 9099)
}
