package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class WriteFailure(val path: String, val error: AppError)

/**
 * Writes return as soon as they're applied to the local cache, so a server-side rejection
 * (e.g. security rules) arrives later. Screens collect [failures] to show it as a typed error.
 */
interface WriteFailures {
    /** Failures are not replayed: only collectors active when the server rejects a write see it. */
    val failures: Flow<WriteFailure>
}

@Singleton
class WriteFailureReporter @Inject constructor() : WriteFailures {
    private val _failures = MutableSharedFlow<WriteFailure>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val failures: Flow<WriteFailure> = _failures.asSharedFlow()

    fun report(failure: WriteFailure) {
        _failures.tryEmit(failure)
    }
}
