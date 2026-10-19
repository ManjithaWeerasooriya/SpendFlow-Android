package com.example.spendflow.core.common

import kotlin.coroutines.cancellation.CancellationException

/** Return type for repository operations. Repositories return this instead of throwing. */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Error(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(error)
    return this
}

/** Runs [block] and wraps its outcome. Cancellation is rethrown so structured concurrency still works. */
suspend inline fun <T> appResultOf(
    errorMapper: (Throwable) -> AppError = { AppError.Unknown(it) },
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Error(errorMapper(e))
}
