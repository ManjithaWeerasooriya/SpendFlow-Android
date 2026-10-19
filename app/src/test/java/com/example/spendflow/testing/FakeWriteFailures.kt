package com.example.spendflow.testing

import com.example.spendflow.core.common.AppError
import com.example.spendflow.data.repository.WriteFailure
import com.example.spendflow.data.repository.WriteFailures
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeWriteFailures : WriteFailures {
    override val failures = MutableSharedFlow<WriteFailure>(extraBufferCapacity = 16)

    /** Simulates the server rejecting a write that was already applied locally. */
    fun reject(path: String, error: AppError) {
        failures.tryEmit(WriteFailure(path, error))
    }
}
