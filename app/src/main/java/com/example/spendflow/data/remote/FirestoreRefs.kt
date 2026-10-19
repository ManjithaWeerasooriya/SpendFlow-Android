package com.example.spendflow.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/** The only place repositories get Firestore references from. */
@Singleton
class FirestoreRefs @Inject constructor(private val firestore: FirebaseFirestore) {
    fun user(uid: String): DocumentReference = firestore.document(FirestorePaths.user(uid))
    fun accounts(uid: String): CollectionReference = firestore.collection(FirestorePaths.accounts(uid))
    fun categories(uid: String): CollectionReference = firestore.collection(FirestorePaths.categories(uid))
    fun transactions(uid: String): CollectionReference = firestore.collection(FirestorePaths.transactions(uid))
    fun budgets(uid: String): CollectionReference = firestore.collection(FirestorePaths.budgets(uid))
    fun recurring(uid: String): CollectionReference = firestore.collection(FirestorePaths.recurring(uid))
}
