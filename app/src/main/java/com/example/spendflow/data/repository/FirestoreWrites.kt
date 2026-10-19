package com.example.spendflow.data.repository

import android.util.Log
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.remote.mapper.FirestoreMappingException
import com.example.spendflow.data.remote.toAppError
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot

private const val TAG = "SpendFlowData"

/**
 * Starts a write without waiting for the server. Firestore applies it to the local cache
 * immediately, so snapshot listeners update at once, and it syncs when the device is online.
 * Awaiting the returned Task would hang for as long as the device is offline.
 * Server-side rejections (e.g. security rules) are logged and sent to [failures].
 */
internal fun DocumentReference.writeLocally(
    failures: WriteFailureReporter,
    op: (DocumentReference) -> Task<Void>,
): AppResult<Unit> = try {
    op(this).addOnFailureListener {
        Log.w(TAG, "Write to $path failed", it)
        failures.report(WriteFailure(path, it.toAppError()))
    }
    AppResult.Success(Unit)
} catch (e: Exception) {
    AppResult.Error(e.toAppError())
}

/**
 * Pending server timestamps (createdAt/updatedAt right after a local write) read as the local
 * estimate instead of null, so a just-saved item already has them.
 */
internal fun DocumentSnapshot.estimatedData(): Map<String, Any?>? =
    getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

/** Maps a document, or logs and returns null if it's malformed, so one bad document can't break a list. */
internal fun <T> DocumentSnapshot.toModelOrNull(fromFirestore: (String, Map<String, Any?>) -> T): T? {
    val data = estimatedData() ?: return null
    return try {
        fromFirestore(id, data)
    } catch (e: FirestoreMappingException) {
        Log.w(TAG, "Skipping malformed document ${reference.path}", e)
        null
    }
}
