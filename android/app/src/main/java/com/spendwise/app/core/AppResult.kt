package com.spendwise.app.core

/** What repositories return instead of throwing: callers must handle failure explicitly. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed class AppError(val message: String) {
    data object Offline : AppError("No internet connection")
    data object Unauthorized : AppError("Session expired. Please sign in again")
    data class RateLimited(val retryAfterSec: Long?) : AppError("Too many requests. Try again shortly")
    data class Validation(val detail: String) : AppError(detail)
    data class Server(val code: Int) : AppError("Server error ($code). Try again later")
    data class Unknown(val detail: String = "Something went wrong") : AppError(detail)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}
