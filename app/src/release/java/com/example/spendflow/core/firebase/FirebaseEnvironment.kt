package com.example.spendflow.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Release builds use the production Firebase project as configured in google-services.json. */
object FirebaseEnvironment {
    fun configure(firestore: FirebaseFirestore) = Unit

    fun configure(auth: FirebaseAuth) = Unit
}
